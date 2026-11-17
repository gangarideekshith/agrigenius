package com.agrigenius.identity.application;

import com.agrigenius.common.error.AccountAlreadyExistsException;
import com.agrigenius.identity.domain.Role;
import com.agrigenius.identity.domain.UserAccount;
import com.agrigenius.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle for other modules. Callers pass already-normalised values
 * (see {@code MobileNumbers}); passwords are hashed here and never returned.
 */
@Service
public class UserAccountService {

    static final String DUPLICATE_MOBILE = "An account with this mobile number already exists.";
    static final String DUPLICATE_EMAIL = "An account with this email address already exists.";

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAccountService(UserAccountRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Public view of an account; never includes the password hash. */
    public record AccountView(UUID id, String mobileNumber, String email, Role role, boolean active,
                              java.time.Instant createdAt) {
        static AccountView of(UserAccount account) {
            return new AccountView(account.getId(), account.getMobileNumber(), account.getEmail(),
                    account.getRole(), account.isActive(), account.getCreatedAt());
        }
    }

    /**
     * Creates an active account. Joins the caller's transaction so a later failure
     * (for example creating the farmer profile) rolls the account back too.
     *
     * @throws AccountAlreadyExistsException if the mobile number or email is taken, including
     *                                       when a concurrent registration wins the race
     */
    @Transactional
    public AccountView createAccount(String normalizedMobile, String email, String rawPassword, Role role) {
        if (repository.existsByMobileNumber(normalizedMobile)) {
            throw new AccountAlreadyExistsException(DUPLICATE_MOBILE);
        }
        if (email != null && repository.existsByEmail(email)) {
            throw new AccountAlreadyExistsException(DUPLICATE_EMAIL);
        }
        UserAccount account = new UserAccount(normalizedMobile, email, passwordEncoder.encode(rawPassword), role,
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        try {
            return AccountView.of(repository.saveAndFlush(account));
        } catch (DataIntegrityViolationException raceLost) {
            String message = raceLost.getMostSpecificCause().getMessage();
            throw new AccountAlreadyExistsException(message != null && message.contains("uk_users_email")
                    ? DUPLICATE_EMAIL : DUPLICATE_MOBILE);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AccountView> findActiveById(UUID id) {
        return repository.findById(id).filter(UserAccount::isActive).map(AccountView::of);
    }

    @Transactional(readOnly = true)
    public Optional<AccountView> findByMobile(String normalizedMobile) {
        return repository.findByMobileNumber(normalizedMobile).map(AccountView::of);
    }

    @Transactional
    public void recordSuccessfulLogin(UUID id) {
        repository.findById(id).ifPresent(account -> account.recordLogin(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }
}
