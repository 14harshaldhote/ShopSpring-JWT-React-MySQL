package com.shopeefy.catalog;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.catalog.ProductDtos.ProductDto;
import com.shopeefy.security.CurrentUser;

/** Admin catalogue management. The ADMIN role is enforced in SecurityConfig for all of /api/admin/**. [OWASP A01:2025] */
@RestController
@Validated
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService service;
    private final AuditService audit;

    public AdminProductController(ProductService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/all")
    List<ProductDto> all() {
        return service.all();
    }

    @GetMapping("/recent")
    List<ProductDto> recent() {
        return service.recent();
    }

    @PostMapping({"", "/"})
    ResponseEntity<List<ProductDto>> create(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody @Size(min = 1, max = 50) List<@Valid CreateProductRequest> body) {
        List<ProductDto> created = service.create(body);
        audit.record(SecurityEventType.ADMIN_ACTION, Outcome.SUCCESS, CurrentUser.id(jwt), null,
                "products.create count=" + created.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/update")
    ProductDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) Long id,
                      @Valid @RequestBody UpdateProductRequest body) {
        ProductDto updated = service.update(id, body);
        audit.record(SecurityEventType.ADMIN_ACTION, Outcome.SUCCESS, CurrentUser.id(jwt), null, "products.update id=" + id);
        return updated;
    }

    @DeleteMapping("/{id}/delete")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) Long id) {
        service.delete(id);
        audit.record(SecurityEventType.ADMIN_ACTION, Outcome.SUCCESS, CurrentUser.id(jwt), null, "products.delete id=" + id);
        return ResponseEntity.noContent().build();
    }
}
