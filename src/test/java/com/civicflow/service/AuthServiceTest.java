package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.AuthenticationException;
import com.civicflow.model.*;
import com.civicflow.repository.UserRepository;
import com.civicflow.repository.VoterRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.PasswordService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VoterRepository voterRepository;
    @Mock private SettingsService settingsService;
    @Mock private AuditService auditService;

    private PasswordService passwordService;
    private SessionManager sessionManager;
    private AuthService authService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        sessionManager = new SessionManager();
        AccessControlService accessControl = new AccessControlService(sessionManager);
        fixedClock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
        authService = new AuthService(userRepository, voterRepository, passwordService, sessionManager,
                accessControl, settingsService, auditService, fixedClock);
    }

    @Test
    void correctLoginStartsSessionAndLogsAudit() {
        User admin = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(admin));

        UserSession session = authService.login("superadmin", "Abc123");

        assertNotNull(session);
        assertEquals("superadmin", session.getUsername());
        assertEquals(RoleType.SUPER_ADMIN, session.getRole());
        assertTrue(sessionManager.isAuthenticated());
        verify(auditService).recordForActor(eq(1L), eq("superadmin"), eq("AUTH"), eq("LOGIN_SUCCESS"), anyString(), eq(AuditResultStatus.SUCCESS));
    }

    @Test
    void incorrectLoginReturnsGenericErrorAndTracksFailures() {
        User admin = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(admin));
        when(settingsService.getIntSetting(SettingsService.KEY_MAX_FAILED_ATTEMPTS, 5)).thenReturn(3);
        when(settingsService.getIntSetting(SettingsService.KEY_LOCKOUT_MINUTES, 15)).thenReturn(15);

        AuthenticationException ex1 = assertThrows(AuthenticationException.class,
                () -> authService.login("superadmin", "WrongPass@1"));
        assertEquals(AuthService.GENERIC_LOGIN_ERROR, ex1.getUserMessage());

        // Unknown user produces the exact same generic message (prevents account enumeration)
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        AuthenticationException ex2 = assertThrows(AuthenticationException.class,
                () -> authService.login("ghost", "WrongPass@1"));
        assertEquals(AuthService.GENERIC_LOGIN_ERROR, ex2.getUserMessage());

        verify(userRepository).updateLoginFailure(eq(1L), eq(1), isNull(), eq(AccountStatus.ACTIVE));
    }

    @Test
    void accountLocksAfterMaxFailedAttempts() {
        User admin = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        admin.setFailedLoginAttempts(2);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(admin));
        when(settingsService.getIntSetting(SettingsService.KEY_MAX_FAILED_ATTEMPTS, 5)).thenReturn(3);
        when(settingsService.getIntSetting(SettingsService.KEY_LOCKOUT_MINUTES, 15)).thenReturn(15);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.login("superadmin", "WrongPass@1"));
        assertTrue(ex.getUserMessage().toLowerCase().contains("temporarily locked"));

        LocalDateTime expectedUnlock = LocalDateTime.now(fixedClock).plusMinutes(15);
        verify(userRepository).updateLoginFailure(1L, 3, expectedUnlock, AccountStatus.LOCKED);
    }

    @Test
    void lockedAccountCannotLoginBeforeLockExpires() {
        User admin = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        admin.setAccountStatus(AccountStatus.LOCKED);
        admin.setLockedUntil(LocalDateTime.now(fixedClock).plusMinutes(10));
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(admin));

        assertThrows(AuthenticationException.class, () -> authService.login("superadmin", "Abc123"));
        assertFalse(sessionManager.isAuthenticated());
    }

    @Test
    void logoutClearsSession() {
        User admin = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(admin));
        authService.login("superadmin", "Abc123");

        authService.logout();
        assertFalse(sessionManager.isAuthenticated());
        verify(auditService).recordForActor(eq(1L), eq("superadmin"), eq("AUTH"), eq("LOGOUT"), anyString(), eq(AuditResultStatus.SUCCESS));
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        authService.login("superadmin", "Abc123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        com.civicflow.exception.ValidationException ex = assertThrows(
                com.civicflow.exception.ValidationException.class,
                () -> authService.changeOwnPassword("WrongPass", "New123", "New123")
        );
        assertEquals("Current password is incorrect.", ex.getMessage());
    }

    @Test
    void changePasswordRejectsMismatchedConfirmation() {
        User user = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        authService.login("superadmin", "Abc123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        com.civicflow.exception.ValidationException ex = assertThrows(
                com.civicflow.exception.ValidationException.class,
                () -> authService.changeOwnPassword("Abc123", "New123", "Diff12")
        );
        assertEquals("New password and confirmation must match.", ex.getMessage());
    }

    @Test
    void changePasswordRejectsInvalidLength() {
        User user = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        authService.login("superadmin", "Abc123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Shorter than 6
        assertThrows(
                com.civicflow.exception.ValidationException.class,
                () -> authService.changeOwnPassword("Abc123", "12345", "12345")
        );

        // Longer than 6
        assertThrows(
                com.civicflow.exception.ValidationException.class,
                () -> authService.changeOwnPassword("Abc123", "1234567", "1234567")
        );
    }

    @Test
    void changePasswordSucceedsWithValidCredentials() {
        User user = buildUser("superadmin", "Abc123", RoleType.SUPER_ADMIN);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        authService.login("superadmin", "Abc123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.changeOwnPassword("Abc123", "New123", "New123");

        verify(userRepository).updatePassword(eq(1L), anyString(), eq(false));
        verify(auditService).record(eq("AUTH"), eq("PASSWORD_CHANGED"), anyString(), eq(AuditResultStatus.SUCCESS));
    }

    private User buildUser(String username, String rawPassword, RoleType role) {
        User u = new User();
        u.setUserId(1L);
        u.setUsername(username);
        u.setFullName("Demo User");
        u.setEmail(username + "@civicflow.demo");
        u.setRoleType(role);
        u.setPasswordHash(passwordService.hashPassword(rawPassword));
        u.setAccountStatus(AccountStatus.ACTIVE);
        return u;
    }
}
