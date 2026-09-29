package com.shopeefy.cart;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.catalog.Product;
import com.shopeefy.catalog.ProductRepository;
import com.shopeefy.common.ApiException;
import com.shopeefy.user.UserRepository;

/**
 * Cart rules live on the server: 1 to 10 of an item, at most 20 different items, only active
 * products and real sizes, and never more than is in stock.           [OWASP A06:2025]
 * Every lookup is scoped to the caller's own cart.                    [OWASP A01:2025]
 */
@Service
public class CartService {

    static final int MAX_QTY = 10;
    static final int MAX_LINES = 20;

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final UserRepository users;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products, UserRepository users) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartDto view(long userId) {
        return CartDto.of(carts.findByUserId(userId).orElse(null));
    }

    @Transactional
    public CartDto add(long userId, AddToCartRequest req) {
        Cart cart = lockOrCreate(userId);
        Product product = products.findByIdAndActiveTrue(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        if (!product.hasSize(req.size())) {
            throw ApiException.badRequest("This product doesn't come in size " + req.size() + ".");
        }
        CartItem line = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()) && i.getSize().equals(req.size()))
                .findFirst().orElse(null);
        int quantity = req.quantity() + (line == null ? 0 : line.getQuantity());
        if (quantity > MAX_QTY) {
            throw ApiException.badRequest("You can buy at most " + MAX_QTY + " of an item.");
        }
        checkStock(product, req.size(), quantity);
        if (line != null) {
            line.setQuantity(quantity);
        } else {
            if (cart.getItems().size() >= MAX_LINES) {
                throw ApiException.badRequest("Your cart can hold at most " + MAX_LINES + " different items.");
            }
            cart.getItems().add(items.save(new CartItem(cart, product, req.size(), quantity)));
        }
        return CartDto.of(cart);
    }

    @Transactional
    public CartDto update(long userId, long itemId, int quantity) {
        Cart cart = lockOrCreate(userId);
        CartItem line = find(cart, itemId);
        checkStock(line.getProduct(), line.getSize(), quantity);
        line.setQuantity(quantity);
        return CartDto.of(cart);
    }

    @Transactional
    public CartDto remove(long userId, long itemId) {
        Cart cart = lockOrCreate(userId);
        cart.getItems().remove(find(cart, itemId));
        return CartDto.of(cart);
    }

    /** Locks the caller's cart row, creating the cart on first use. */
    @Transactional
    public Cart lockOrCreate(long userId) {
        return carts.lockByUserId(userId).orElseGet(() -> carts.save(new Cart(users.getReferenceById(userId))));
    }

    /** Someone else's cart item id gives the same 404 as a missing one. */
    private static CartItem find(Cart cart, long itemId) {
        return cart.getItems().stream().filter(i -> i.getId() == itemId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Cart item not found."));
    }

    private static void checkStock(Product product, String size, int quantity) {
        int stock = product.stockOf(size);
        if (stock < quantity) {
            throw ApiException.conflict(stock == 0 ? "Size " + size + " is out of stock."
                    : "Only " + stock + " left in size " + size + ".");
        }
    }
}
