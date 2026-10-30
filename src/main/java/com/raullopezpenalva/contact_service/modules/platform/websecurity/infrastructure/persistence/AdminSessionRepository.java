package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminSession;

public interface AdminSessionRepository extends JpaRepository<AdminSession,UUID> {
    long countByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(UUID accountId,Instant now);
    List<AdminSession> findByExpiresAtBeforeOrderByExpiresAtAsc(Instant now,Pageable pageable);
}
