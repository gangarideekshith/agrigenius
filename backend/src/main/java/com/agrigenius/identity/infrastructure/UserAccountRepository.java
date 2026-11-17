package com.agrigenius.identity.infrastructure;

import com.agrigenius.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByMobileNumber(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    /** Emails are stored lowercase (enforced by a check constraint), so equality is enough. */
    boolean existsByEmail(String email);
}
