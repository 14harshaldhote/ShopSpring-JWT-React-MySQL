package com.shopeefy.ops;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.shopeefy.auth.AuthService;
import com.shopeefy.catalog.CreateProductRequest;
import com.shopeefy.catalog.ProductRepository;
import com.shopeefy.catalog.ProductService;
import com.shopeefy.common.LogSanitizer;
import com.shopeefy.config.AppProperties;
import com.shopeefy.security.PasswordPolicy;
import com.shopeefy.user.Role;
import com.shopeefy.user.User;
import com.shopeefy.user.UserRepository;

/**
 * Loads the demo catalogue on an empty database, and creates the admin account from
 * ADMIN_EMAIL / ADMIN_PASSWORD if both are set. There is no default admin password anywhere in
 * the code.                                                                    [OWASP A07:2025]
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;
    private final ProductService productService;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final JsonMapper json;
    private final AppProperties.Seed config;

    public DataSeeder(ProductRepository products, ProductService productService, UserRepository users,
                      PasswordEncoder encoder, PasswordPolicy policy, JsonMapper json, AppProperties props) {
        this.products = products;
        this.productService = productService;
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.json = json;
        this.config = props.seed();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (config.products() && products.count() == 0) {
            try (InputStream in = new ClassPathResource("seed/products.json").getInputStream()) {
                List<CreateProductRequest> seed = json.readValue(in, new TypeReference<>() { });
                productService.create(seed);
                log.info("Seeded {} products", seed.size());
            }
        }
        String email = AuthService.normalizeEmail(config.adminEmail());
        if (email != null && !email.isBlank() && config.adminPassword() != null && users.findByEmail(email).isEmpty()) {
            policy.check(config.adminPassword(), email);
            User admin = new User(email, "Admin", "User");
            admin.setPasswordHash(encoder.encode(PasswordPolicy.normalize(config.adminPassword())));
            admin.setRole(Role.ADMIN);
            admin.setEmailVerified(true);
            users.save(admin);
            log.info("Created admin account {}", LogSanitizer.maskEmail(email));
        }
    }
}
