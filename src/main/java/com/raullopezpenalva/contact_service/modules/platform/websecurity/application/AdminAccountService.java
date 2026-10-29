package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.AdminAccountRepository;

@Service
public class AdminAccountService {
    private static final Logger log=LoggerFactory.getLogger(AdminAccountService.class);
    private final AdminAccountRepository accounts;
    private final AdminPasswordHasher passwords;
    private final JdbcTemplate jdbc;
    public AdminAccountService(AdminAccountRepository accounts,AdminPasswordHasher passwords,JdbcTemplate jdbc) {
        this.accounts=accounts;this.passwords=passwords;this.jdbc=jdbc;
    }
    public record Summary(UUID id,String login,boolean enabled,Set<AdminRole> roles,long authenticationVersion) {}
    private Summary summary(AdminAccount a) {return new Summary(a.getId(),a.getLogin(),a.isEnabled(),a.getRoles(),a.getAuthenticationVersion());}
    private void lockManagement() {jdbc.queryForObject("SELECT id FROM admin_management_lock WHERE id=1 FOR UPDATE",Integer.class);}
    private AdminAccount required(String login) {return accounts.findByLogin(AdminAccount.canonicalLogin(login))
        .orElseThrow(()->new IllegalArgumentException("Account not found"));}
    private void revoke(AdminAccount account) {
        jdbc.update("UPDATE admin_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL",account.getId());
    }
    @Transactional
    public Summary provision(String login,char[] password) {
        String canonical=AdminAccount.canonicalLogin(login); AdminPasswordHasher.validate(password); lockManagement();
        var existing=accounts.findByLogin(canonical);
        if(existing.isPresent()) return summary(existing.get());
        var account=accounts.saveAndFlush(AdminAccount.create(canonical,passwords.encode(password)));
        log.info("Admin account provisioned accountId={}",account.getId());
        return summary(account);
    }
    @Transactional
    public Summary resetPassword(String login,char[] password) {
        AdminPasswordHasher.validate(password); lockManagement(); var account=required(login);
        account.resetPassword(passwords.encode(password)); revoke(account); accounts.flush();
        log.info("Admin password reset accountId={}",account.getId()); return summary(account);
    }
    @Transactional
    public Summary setEnabled(String login,boolean enabled) {
        lockManagement(); var account=required(login);
        if(!enabled && account.isEnabled() && account.getRoles().contains(AdminRole.ADMIN) && accounts.countEnabledAdmins()<=1)
            throw new IllegalStateException("Cannot disable the last enabled administrator");
        if(account.isEnabled()!=enabled) {account.setEnabled(enabled); revoke(account);accounts.flush();}
        log.info("Admin enabled state changed accountId={}, enabled={}",account.getId(),enabled); return summary(account);
    }
    @Transactional(readOnly=true)
    public boolean verifyEligibleCredentials(String login,char[] password) {
        String canonical;
        try {canonical=AdminAccount.canonicalLogin(login);} catch(IllegalArgumentException ex) {return passwords.eligiblePasswordMatches(password,null,false);}
        var account=accounts.findByLogin(canonical);
        return passwords.eligiblePasswordMatches(password,account.map(AdminAccount::getPasswordHash).orElse(null),
            account.map(a->a.isEnabled() && a.getRoles().contains(AdminRole.ADMIN)).orElse(false));
    }
}
