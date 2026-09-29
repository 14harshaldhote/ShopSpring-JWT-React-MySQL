package com.shopeefy.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Ownership is part of the query itself, not a check after loading.      [OWASP A01:2025] */
    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
