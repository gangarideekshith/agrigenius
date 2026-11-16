package com.agrigenius.identity.infrastructure;

import com.agrigenius.common.validation.MobileNumbers;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads credentials for login by mobile number. The username is the account UUID so the
 * authenticated principal never carries the phone number.
 *
 * <p>Accounts are always reported as enabled here; the ACTIVE check happens after the password
 * check (in {@code AuthController}) so disabled accounts take the same time and get the same
 * generic error as a wrong password.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public DatabaseUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String mobileNumber) {
        String normalized = MobileNumbers.normalize(mobileNumber)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return repository.findByMobileNumber(normalized)
                .map(account -> User.withUsername(account.getId().toString())
                        .password(account.getPasswordHash())
                        .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
