-- ShopSpring schema v2 (MySQL 8.4, InnoDB, utf8mb4).
-- Schema changes only go through Flyway migrations; Hibernate runs with ddl-auto=validate.   [OWASP A02:2025]
-- CHECK constraints enforce business invariants in the database as a last line of defence. [OWASP A06:2025]

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NULL,          -- Argon2id; NULL for OAuth2-only accounts
    first_name            VARCHAR(60)  NOT NULL,
    last_name             VARCHAR(60)  NOT NULL,
    mobile                VARCHAR(20)  NULL,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    auth_provider         VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    first_failed_login_at DATETIME(6)  NULL,          -- start of the lockout observation window
    lockout_count         INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- External identities linked to a user (Google, GitHub, dev IdP). One user can have several.
CREATE TABLE user_identities (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(254) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_identity UNIQUE (provider, subject),
    CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE addresses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    first_name     VARCHAR(60)  NOT NULL,
    last_name      VARCHAR(60)  NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    city           VARCHAR(80)  NOT NULL,
    state          VARCHAR(80)  NOT NULL,
    zip_code       VARCHAR(12)  NOT NULL,
    mobile         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE categories (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    name      VARCHAR(50) NOT NULL,
    parent_id BIGINT      NULL,
    level     INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_category UNIQUE (parent_id, name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT ck_category_level CHECK (level BETWEEN 1 AND 3)
);
CREATE INDEX idx_category_name ON categories (name);

CREATE TABLE products (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    brand            VARCHAR(80)   NOT NULL,
    color            VARCHAR(40)   NOT NULL,
    image_url        VARCHAR(500)  NOT NULL,
    price            INT           NOT NULL,
    discounted_price INT           NOT NULL,
    discount_percent INT           NOT NULL,
    quantity         INT           NOT NULL,
    category_id      BIGINT        NOT NULL,
    rating_avg       DECIMAL(3, 2) NOT NULL DEFAULT 0,  -- denormalised so listings need no aggregate query
    rating_count     INT           NOT NULL DEFAULT 0,
    review_count     INT           NOT NULL DEFAULT 0,
    active           BOOLEAN       NOT NULL DEFAULT TRUE, -- soft delete keeps order history intact
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_product_price CHECK (price > 0 AND discounted_price > 0 AND discounted_price <= price),
    CONSTRAINT ck_product_discount CHECK (discount_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_product_quantity CHECK (quantity >= 0)
);
-- Listing query: WHERE active AND category_id = ? ORDER BY discounted_price / created_at
CREATE INDEX idx_product_listing ON products (category_id, active, discounted_price);
CREATE INDEX idx_product_created ON products (active, created_at);
-- Search uses MATCH ... AGAINST instead of LIKE '%term%' full scans
CREATE FULLTEXT INDEX ft_product_search ON products (title, brand, color, description);

CREATE TABLE product_sizes (
    product_id BIGINT      NOT NULL,
    name       VARCHAR(10) NOT NULL,
    quantity   INT         NOT NULL,
    PRIMARY KEY (product_id, name),
    CONSTRAINT fk_size_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_size_quantity CHECK (quantity >= 0)   -- stock can never go negative   [OWASP A06:2025]
);

CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_user UNIQUE (user_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    size       VARCHAR(10) NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_item UNIQUE (cart_id, product_id, size),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 10)
);

CREATE TABLE orders (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    total_price            INT          NOT NULL,
    total_discounted_price INT          NOT NULL,
    discount               INT          NOT NULL,
    total_item             INT          NOT NULL,
    -- shipping address is copied into the order so later address edits can't rewrite history
    ship_first_name        VARCHAR(60)  NOT NULL,
    ship_last_name         VARCHAR(60)  NOT NULL,
    ship_street_address    VARCHAR(255) NOT NULL,
    ship_city              VARCHAR(80)  NOT NULL,
    ship_state             VARCHAR(80)  NOT NULL,
    ship_zip_code          VARCHAR(12)  NOT NULL,
    ship_mobile            VARCHAR(20)  NOT NULL,
    payment_provider       VARCHAR(20)  NULL,
    payment_status         VARCHAR(20)  NOT NULL,
    provider_order_id      VARCHAR(64)  NULL,
    provider_payment_id    VARCHAR(64)  NULL,
    paid_at                DATETIME(6)  NULL,
    order_date             DATETIME(6)  NOT NULL,
    delivery_date          DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_order_provider_order UNIQUE (provider_order_id),
    CONSTRAINT uk_order_provider_payment UNIQUE (provider_payment_id),  -- one payment can pay one order only
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT', 'PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_order_payment_status CHECK (payment_status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_order_totals CHECK (total_discounted_price > 0 AND total_discounted_price <= total_price)
);
CREATE INDEX idx_order_user ON orders (user_id, created_at);
CREATE INDEX idx_order_status ON orders (status, created_at);

CREATE TABLE order_items (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    order_id         BIGINT      NOT NULL,
    product_id       BIGINT      NOT NULL,
    size             VARCHAR(10) NOT NULL,
    quantity         INT         NOT NULL,
    price            INT         NOT NULL,   -- line totals captured at order time
    discounted_price INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 10)
);
CREATE INDEX idx_order_item_product ON order_items (product_id);

CREATE TABLE reviews (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    product_id BIGINT        NOT NULL,
    user_id    BIGINT        NOT NULL,
    review     VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_review_product ON reviews (product_id, created_at);

CREATE TABLE ratings (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    rating     INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rating_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_rating_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_rating_value CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_rating_product ON ratings (product_id);

-- One row per signed-in device. Refresh tokens rotate inside a session.   [OWASP A07:2025]
CREATE TABLE user_sessions (
    id            VARCHAR(36)     NOT NULL,
    user_id       BIGINT       NOT NULL,
    auth_method   VARCHAR(30)  NOT NULL,
    ip_address    VARCHAR(45)  NULL,
    user_agent    VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    last_used_at  DATETIME(6)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,   -- absolute lifetime, not extended by rotation
    revoked_at    DATETIME(6)  NULL,
    revoke_reason VARCHAR(40)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_session_user ON user_sessions (user_id, revoked_at);

-- Only a SHA-256 of each refresh token is stored; a database leak does not leak usable tokens. [OWASP A04:2025]
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36)    NOT NULL,
    token_hash VARCHAR(64)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,          -- set on rotation; a second use means the token was stolen
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_session FOREIGN KEY (session_id) REFERENCES user_sessions (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_expiry ON refresh_tokens (expires_at);

-- Email one-time passwords. The code is stored as HMAC-SHA256(pepper, challenge id + code). [OWASP A04:2025, A07:2025]
CREATE TABLE otp_challenges (
    id           VARCHAR(36)     NOT NULL,
    user_id      BIGINT       NULL,
    email        VARCHAR(254) NOT NULL,
    purpose      VARCHAR(20)  NOT NULL,
    code_hash    VARCHAR(64)     NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    send_count   INT          NOT NULL DEFAULT 1,
    decoy        BOOLEAN      NOT NULL DEFAULT FALSE, -- answer-shaped challenge for unknown emails (no enumeration)
    ip_address   VARCHAR(45)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_sent_at DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    consumed_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('REGISTER', 'LOGIN', 'PASSWORD_RESET'))
);
CREATE INDEX idx_otp_expiry ON otp_challenges (expires_at);

-- Append-only, hash-chained security audit trail: every row stores
-- SHA-256(previous row hash + this row's content), so editing or deleting a row breaks the chain. [OWASP A09:2025]
CREATE TABLE security_events (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    type       VARCHAR(40)  NOT NULL,
    severity   VARCHAR(10)  NOT NULL,
    outcome    VARCHAR(10)  NOT NULL,
    user_id    BIGINT       NULL,
    email      VARCHAR(254) NULL,
    ip_address VARCHAR(45)  NULL,
    user_agent VARCHAR(255) NULL,
    request    VARCHAR(255) NULL,          -- "POST /auth/login"
    detail     VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL,
    prev_hash  VARCHAR(64)     NOT NULL,
    hash       VARCHAR(64)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_event_severity CHECK (severity IN ('INFO', 'WARN', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);
CREATE INDEX idx_event_user ON security_events (user_id, created_at);
CREATE INDEX idx_event_type ON security_events (type, created_at);

-- Single-row chain head. Appenders lock this row (SELECT ... FOR UPDATE), so events are chained
-- strictly one after another even with several app instances.
CREATE TABLE audit_chain_head (
    id        TINYINT  NOT NULL,
    last_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id)
);
INSERT INTO audit_chain_head (id, last_hash) VALUES (1, REPEAT('0', 64));
