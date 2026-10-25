package com.medbook.bootstrap;

import com.medbook.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;













class AdminBootstrapRunnerTest {

    private static BootstrapProperties properties(String email, String password) {
        return new BootstrapProperties(email, password, "Clinic", "Admin", "+10000000000");
    }

    @Test
    void doesNothingWhenNotConfigured() {
        BootstrapAccountService service = mock(BootstrapAccountService.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties("", ""), service);

        runner.run(null);

        verifyNoInteractions(service);
    }

    @Test
    void delegatesToEmailScopedEnsureWithTheConfiguredCredentials() {
        BootstrapAccountService service = mock(BootstrapAccountService.class);
        when(service.ensureUser(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new User("bootstrap@medbook.test", "hash", "Clinic", "Admin", "+1")));
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(properties("bootstrap@medbook.test", "SecretPass1"), service);

        runner.run(null);

        verify(service).ensureUser(
                eq("bootstrap@medbook.test"),
                eq("SecretPass1"),
                eq("Clinic"),
                eq("Admin"),
                eq("+10000000000"),
                eq(BootstrapAccountService.ROLE_ADMIN));
    }

    @Test
    void stillDelegatesWhenAnotherAdminAlreadyExists() {


        BootstrapAccountService service = mock(BootstrapAccountService.class);
        User existing = new User("bootstrap@medbook.test", "existing-hash", "Clinic", "Admin", "+1");
        when(service.ensureUser(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(properties("bootstrap@medbook.test", "SecretPass1"), service);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

        verify(service).ensureUser(
                eq("bootstrap@medbook.test"),
                eq("SecretPass1"),
                eq("Clinic"),
                eq("Admin"),
                eq("+10000000000"),
                eq(BootstrapAccountService.ROLE_ADMIN));
    }

    @Test
    void aFailureIsSwallowedSoStartupContinues() {
        BootstrapAccountService service = mock(BootstrapAccountService.class);
        when(service.ensureUser(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database down"));
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(properties("bootstrap@medbook.test", "SecretPass1"), service);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }
}
