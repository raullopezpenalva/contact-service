package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminAccount;

public interface AdminAccountRepository extends JpaRepository<AdminAccount,UUID> {
    Optional<AdminAccount> findByLogin(String login);
    @Query("select count(a) from AdminAccount a join a.roles r where a.enabled=true and r=com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminRole.ADMIN")
    long countEnabledAdmins();
}
