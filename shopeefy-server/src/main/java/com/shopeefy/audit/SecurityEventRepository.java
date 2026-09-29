package com.shopeefy.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Slice;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    /** Locks the chain head row, so concurrent writers (even on other instances) append one at a time. */
    @Query(value = "select last_hash from audit_chain_head where id = 1 for update", nativeQuery = true)
    String lockChainHead();

    @Modifying
    @Query(value = "update audit_chain_head set last_hash = :hash where id = 1", nativeQuery = true)
    void moveChainHead(String hash);

    List<SecurityEvent> findTop20ByUserIdOrderByIdDesc(Long userId);

    Page<SecurityEvent> findAllByOrderByIdDesc(Pageable pageable);

    Page<SecurityEvent> findByTypeOrderByIdDesc(SecurityEventType type, Pageable pageable);

    Slice<SecurityEvent> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);
}
