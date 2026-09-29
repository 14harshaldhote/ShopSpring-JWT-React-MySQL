package com.shopeefy.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    @Query("select i from UserIdentity i join fetch i.user where i.provider = :provider and i.subject = :subject")
    Optional<UserIdentity> findWithUser(AuthProvider provider, String subject);
}
