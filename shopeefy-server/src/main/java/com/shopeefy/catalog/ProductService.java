package com.shopeefy.catalog;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.catalog.ProductDtos.PageResponse;
import com.shopeefy.catalog.ProductDtos.ProductDto;
import com.shopeefy.common.ApiException;

@Service
public class ProductService {

    /** InnoDB's default stopwords and tokens under 3 characters are never indexed; requiring them would match nothing. */
    private static final Set<String> STOPWORDS = Set.of("about", "are", "com", "for", "from", "how", "that", "the",
            "this", "was", "what", "when", "where", "who", "will", "with", "und", "www");

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> list(String category, String color, String size, Integer minPrice, Integer maxPrice,
                                         Integer minDiscount, String sort, String stock, int page, int pageSize) {
        List<Long> categoryIds = null;
        if (category != null && !category.isBlank()) {
            categoryIds = categories.findLeafIdsByName(category);
            if (categoryIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0, 0, page, pageSize);
            }
        }
        var filter = new ProductFilter(categoryIds, ProductFilter.colors(color), ProductFilter.sizes(size),
                minPrice, maxPrice, minDiscount, stock);
        Page<Product> result = products.findAll(filter.toSpecification(),
                PageRequest.of(page, pageSize, ProductFilter.sort(sort)));
        return PageResponse.of(result.map(ProductDto::of));
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return products.findByIdAndActiveTrue(id).map(ProductDto::of)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> search(String q) {
        String expr = toFullTextQuery(q);
        return expr == null ? List.of() : products.fullTextSearch(expr).stream().map(ProductDto::of).toList();
    }

    /**
     * Builds a boolean-mode expression such as {@code +black* +jeans*} from letters and digits only.
     * Operators a user types ({@code + - > < ( ) ~ * " @}) are dropped, so input can't change the
     * meaning of the search or make MySQL reject the expression.            [OWASP A05:2025]
     */
    static String toFullTextQuery(String q) {
        if (q == null) {
            return null;
        }
        String terms = Arrays.stream(q.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 3 && t.length() <= 40 && !STOPWORDS.contains(t))
                .distinct()
                .limit(6)
                .map(t -> "+" + t + "*")
                .collect(Collectors.joining(" "));
        return terms.isEmpty() ? null : terms;
    }

    // ---- Admin --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProductDto> all() {
        return products.findAll((root, query, cb) -> cb.isTrue(root.get("active")),
                Sort.by("createdAt").descending().and(Sort.by("id").descending())).stream().map(ProductDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> recent() {
        return products.findByActiveTrueOrderByCreatedAtDesc(PageRequest.of(0, 10)).stream().map(ProductDto::of).toList();
    }

    @Transactional
    public List<ProductDto> create(List<CreateProductRequest> requests) {
        Map<String, Category> cache = new HashMap<>();
        return requests.stream().map(req -> {
            requireHttpsImage(req.imageUrl());
            if (req.discountedPrice() > req.price()) {
                throw ApiException.badRequest("discountedPrice can't be higher than price.");
            }
            Set<String> names = new HashSet<>();
            req.size().forEach(s -> {
                if (!names.add(s.name())) {
                    throw ApiException.badRequest("Duplicate size " + s.name() + ".");
                }
            });
            Category top = category(cache, req.topLevelCategory(), 1, null);
            Category second = category(cache, req.secondLevelCategory(), 2, top);
            Category third = category(cache, req.thirdLevelCategory(), 3, second);
            Product product = new Product(req.title().strip(), req.description().strip(), req.brand().strip(),
                    req.color().strip().toLowerCase(java.util.Locale.ROOT), req.imageUrl(), req.price(),
                    req.discountedPrice(), third,
                    req.size().stream().map(s -> new ProductSize(s.name(), s.quantity())).toList());
            return ProductDto.of(products.save(product));
        }).toList();
    }

    @Transactional
    public ProductDto update(Long id, UpdateProductRequest req) {
        Product product = products.findByIdAndActiveTrue(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        if (req.description() != null) {
            product.setDescription(req.description().strip());
        }
        if (req.price() != null || req.discountedPrice() != null) {
            int price = req.price() != null ? req.price() : product.getPrice();
            int discounted = req.discountedPrice() != null ? req.discountedPrice() : product.getDiscountedPrice();
            if (discounted > price) {
                throw ApiException.badRequest("discountedPrice can't be higher than price.");
            }
            product.setPrices(price, discounted);
        }
        products.saveAndFlush(product);
        if (req.sizes() != null) {
            for (var size : req.sizes()) {
                if (products.setSizeStock(id, size.name(), size.quantity()) == 0) {
                    throw ApiException.badRequest("Unknown size " + size.name() + ".");
                }
            }
            products.syncTotalStock(id);
        }
        return get(id);
    }

    /** Soft delete: past orders keep pointing at the product. */
    @Transactional
    public void delete(Long id) {
        products.findByIdAndActiveTrue(id).orElseThrow(() -> ApiException.notFound("Product not found.")).deactivate();
    }

    private Category category(Map<String, Category> cache, String name, int level, Category parent) {
        String key = level + "/" + (parent == null ? "" : parent.getId()) + "/" + name;
        return cache.computeIfAbsent(key, k -> categories.find(name, level, parent)
                .orElseGet(() -> categories.save(new Category(name, parent, level))));
    }

    /**
     * Product images are shown to every visitor, so only https URLs are accepted: no
     * {@code javascript:} or {@code data:} URLs, no mixed content.       [OWASP A05:2025]
     */
    static void requireHttpsImage(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw ApiException.badRequest("imageUrl must be an https URL.");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("imageUrl must be an https URL.");
        }
    }
}
