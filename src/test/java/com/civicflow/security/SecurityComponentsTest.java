package com.civicflow.security;

import com.civicflow.exception.AuthorizationException;
import com.civicflow.exception.PayloadTooLargeException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AccountStatus;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;
import com.civicflow.model.Voter;
import com.civicflow.web.CivicFlowWebServer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SecurityComponentsTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void passwordHashingAndVerificationWorks() {
        String raw = "CivicFlow@2026";
        String hash = passwordService.hashPassword(raw);
        assertNotNull(hash);
        assertNotEquals(raw, hash);
        assertTrue(hash.startsWith("$2a$"));
        assertTrue(passwordService.verifyPassword(raw, hash));
        assertFalse(passwordService.verifyPassword("WrongPass@2026", hash));
    }

    @Test
    void sixCharacterPasswordPolicyIsEnforced() {
        // Less than 6 characters must be rejected
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("12345"));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("Abc12"));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("a"));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength(""));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength(null));

        // More than 6 characters must be rejected
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("1234567"));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("Abc1234"));
        assertThrows(ValidationException.class, () -> passwordService.validatePasswordStrength("CivicFlow@2026"));

        // Exactly 6 characters must be accepted
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength("Abc123"));
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength("Adm123"));
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength("Off123"));
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength("Vot123"));
    }

    @Test
    void accessControlEnforcesRoleSeparation() {
        SessionManager sm = new SessionManager();
        AccessControlService ac = new AccessControlService(sm);

        // Unauthenticated
        assertThrows(AuthorizationException.class, ac::requireAuthenticated);

        // Voter session
        User voterUser = new User();
        voterUser.setUserId(10L);
        voterUser.setUsername("voter.test");
        voterUser.setRoleType(RoleType.VOTER);
        voterUser.setAccountStatus(AccountStatus.ACTIVE);
        Voter voter = new Voter();
        voter.setVoterId(100L);
        sm.startSession(new UserSession(voterUser, voter, LocalDateTime.now()));

        assertDoesNotThrow(ac::requireVoter);
        assertThrows(AuthorizationException.class, ac::requireAdmin);
        assertThrows(AuthorizationException.class, ac::requireSuperAdmin);

        // Election Admin session
        User elAdmin = new User();
        elAdmin.setUserId(2L);
        elAdmin.setUsername("eladmin");
        elAdmin.setRoleType(RoleType.ELECTION_ADMIN);
        sm.startSession(new UserSession(elAdmin, null, LocalDateTime.now()));

        assertDoesNotThrow(ac::requireAdmin);
        assertThrows(AuthorizationException.class, ac::requireSuperAdmin);
        assertThrows(AuthorizationException.class, ac::requireVoter);

        // Super Admin session
        User superAdmin = new User();
        superAdmin.setUserId(1L);
        superAdmin.setUsername("superadmin");
        superAdmin.setRoleType(RoleType.SUPER_ADMIN);
        sm.startSession(new UserSession(superAdmin, null, LocalDateTime.now()));

        assertDoesNotThrow(ac::requireSuperAdmin);
        assertDoesNotThrow(ac::requireAdmin);
        assertThrows(AuthorizationException.class, ac::requireVoter);

        // Logout clears session
        sm.clearSession();
        assertFalse(sm.isAuthenticated());
        assertThrows(AuthorizationException.class, ac::requireAuthenticated);
    }

    @Test
    void csvSanitizerNeutralizesFormulaInjection() {
        assertEquals("\"'=CMD|' /C calc'!A0\"", CsvSanitizer.sanitizeCell("=CMD|' /C calc'!A0"));
        assertEquals("\"'+SUM(1,2)\"", CsvSanitizer.sanitizeCell("+SUM(1,2)"));
        assertEquals("\"'-1+1\"", CsvSanitizer.sanitizeCell("-1+1"));
        assertEquals("\"'@SUM(A1:A5)\"", CsvSanitizer.sanitizeCell("@SUM(A1:A5)"));
        assertEquals("\"Normal Text\"", CsvSanitizer.sanitizeCell("Normal Text"));
        assertEquals("\"Quoted \"\"Value\"\"\"", CsvSanitizer.sanitizeCell("Quoted \"Value\""));
    }

    @Test
    void safeFileNameStripsTraversalCharacters() {
        String safe = CsvSanitizer.sanitizeFileName("../../etc/passwd?bad*name", ".csv");
        assertFalse(safe.contains(".."));
        assertFalse(safe.contains("/"));
        assertTrue(safe.endsWith(".csv"));
    }

    @Test
    void requestPayloadSizeLimitRejectsOversizedPayloadWithHttp413() throws Exception {
        assertEquals(4 * 1024 * 1024, CivicFlowWebServer.MAX_REQUEST_SIZE_BYTES);

        PayloadTooLargeException ple = new PayloadTooLargeException("Request payload size exceeds maximum limit of 4 MiB.");
        assertEquals("PAYLOAD_TOO_LARGE", ple.getErrorCode());
        assertTrue(ple.getUserMessage().contains("4 MiB"));

        // Spin up ephemeral web server on an available port
        int testPort;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            testPort = ss.getLocalPort();
        }

        com.civicflow.app.ApplicationContext mockCtx = org.mockito.Mockito.mock(com.civicflow.app.ApplicationContext.class);
        SessionManager sm = new SessionManager();
        org.mockito.Mockito.when(mockCtx.getSessionManager()).thenReturn(sm);
        com.civicflow.service.AuthService mockAuth = org.mockito.Mockito.mock(com.civicflow.service.AuthService.class);
        org.mockito.Mockito.when(mockCtx.getAuthService()).thenReturn(mockAuth);

        CivicFlowWebServer webServer = new CivicFlowWebServer(mockCtx, testPort);
        try {
            webServer.start();

            // Send request with Content-Length exceeding 4 MiB to trigger HTTP 413
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", testPort);
                 java.io.OutputStream os = socket.getOutputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))) {
                String req = "POST /api/auth/login HTTP/1.1\r\n" +
                             "Host: 127.0.0.1:" + testPort + "\r\n" +
                             "Content-Type: application/json\r\n" +
                             "Content-Length: 5242880\r\n\r\n";
                os.write(req.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();

                String statusLine = reader.readLine();
                assertNotNull(statusLine, "Server must respond with status line");
                assertTrue(statusLine.contains("413"), "Web server MUST return HTTP 413 for requests exceeding 4 MiB; got: " + statusLine);
            }

            // Verify clean executor state while running
            assertFalse(webServer.getExecutor().isShutdown());
        } finally {
            webServer.close();
            assertTrue(webServer.getExecutor().isShutdown(), "Web server executor MUST be terminated on close");
        }
    }
}
