package com.agrigenius.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.agrigenius.common.error.AccountAlreadyExistsException;
import com.agrigenius.identity.domain.Role;
import com.agrigenius.identity.domain.UserAccount;
import com.agrigenius.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final String MOBILE = "+919876543210";

    @Mock
    UserAccountRepository repository;

    @Mock
    PasswordEncoder passwordEncoder;

    private UserAccountService service() {
        return new UserAccountService(repository, passwordEncoder,
                Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsAccountWithHashedPassword() {
        given(passwordEncoder.encode("s3cure-pass")).willReturn("{bcrypt}$2a$12$hash");
        given(repository.saveAndFlush(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

        UserAccountService.AccountView view = service().createAccount(MOBILE, null, "s3cure-pass", Role.FARMER);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}$2a$12$hash").isNotEqualTo("s3cure-pass");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.FARMER);
        assertThat(view.mobileNumber()).isEqualTo(MOBILE);
        assertThat(view.active()).isTrue();
    }

    @Test
    void duplicateMobileIsRejected() {
        given(repository.existsByMobileNumber(MOBILE)).willReturn(true);

        assertThatThrownBy(() -> service().createAccount(MOBILE, null, "s3cure-pass", Role.FARMER))
                .isInstanceOfSatisfying(AccountAlreadyExistsException.class,
                        ex -> assertThat(ex.code()).isEqualTo("ACCOUNT_ALREADY_EXISTS"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmailIsRejected() {
        given(repository.existsByEmail("ravi@example.com")).willReturn(true);

        assertThatThrownBy(() -> service().createAccount(MOBILE, "ravi@example.com", "s3cure-pass", Role.FARMER))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessageContaining("email");
    }

    @Test
    void lostRaceOnUniqueConstraintBecomesConflict() {
        given(passwordEncoder.encode(any())).willReturn("{bcrypt}x");
        given(repository.saveAndFlush(any(UserAccount.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key value violates uk_users_mobile_number"));

        assertThatThrownBy(() -> service().createAccount(MOBILE, null, "s3cure-pass", Role.FARMER))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessageContaining("mobile");
    }

    @Test
    void findActiveByIdFiltersDisabledAccounts() {
        UserAccount account = new UserAccount(MOBILE, null, "{bcrypt}x", Role.FARMER, Instant.now());
        account.disable();
        given(repository.findById(account.getId())).willReturn(Optional.of(account));

        assertThat(service().findActiveById(account.getId())).isEmpty();
    }

    @Test
    void findActiveByIdReturnsActiveAccount() {
        UserAccount account = new UserAccount(MOBILE, null, "{bcrypt}x", Role.FARMER, Instant.now());
        given(repository.findById(account.getId())).willReturn(Optional.of(account));

        assertThat(service().findActiveById(account.getId())).hasValueSatisfying(v -> assertThat(v.id()).isEqualTo(account.getId()));
    }
}
