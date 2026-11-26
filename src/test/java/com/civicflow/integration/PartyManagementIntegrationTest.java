package com.civicflow.integration;

import com.civicflow.app.ApplicationContext;
import com.civicflow.config.AppConfig;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.Candidate;
import com.civicflow.model.Party;
import com.civicflow.model.PartyStatus;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the 10 standard simulation parties in MySQL:
 * 1. BJP 2. INC 3. AAP 4. CJP 5. JDS 6. RJP 7. TMC 8. BSP 9. AIADMK 10. SP
 * Ensures:
 * - All 10 parties exist
 * - No duplicate party codes or party names
 * - Candidate-party assignments exist and maintain foreign keys
 * - Service layer duplicate rejection
 */
class PartyManagementIntegrationTest {

    private static ApplicationContext ctx;
    private static boolean dbAvailable = false;

    private static final List<String> REQUIRED_PARTIES = List.of(
            "BJP", "INC", "AAP", "CJP", "JDS", "RJP", "TMC", "BSP", "AIADMK", "SP"
    );

    @BeforeAll
    static void setUp() {
        AppConfig cfg = AppConfig.load();
        if (cfg.getDbUser() == null || cfg.getDbUser().isBlank()) {
            return;
        }
        try {
            ctx = new ApplicationContext(cfg, Clock.systemDefaultZone());
            dbAvailable = ctx.getDatabaseManager().testConnection();
        } catch (Exception e) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private static void loginAdmin() {
        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("superadmin");
        admin.setRoleType(RoleType.SUPER_ADMIN);
        ctx.getSessionManager().startSession(new UserSession(admin, null, LocalDateTime.now()));
    }

    @Test
    void allTenPartiesExistWithExactNamesAndCodes() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not available in test environment");
        loginAdmin();

        List<Party> parties = ctx.getPartyService().getAll(null, null);
        assertNotNull(parties);

        Set<String> foundCodes = new HashSet<>();
        Set<String> foundNames = new HashSet<>();
        for (Party p : parties) {
            foundCodes.add(p.getPartyCode());
            foundNames.add(p.getPartyName());
        }

        for (String req : REQUIRED_PARTIES) {
            assertTrue(foundCodes.contains(req), "Required party code missing: " + req);
            assertTrue(foundNames.contains(req), "Required party name missing: " + req);
        }
    }

    @Test
    void noDuplicatePartyRecordsInDatabase() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not available in test environment");

        try (Connection conn = ctx.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {

            // Check duplicate codes
            try (ResultSet rs = stmt.executeQuery("SELECT party_code, COUNT(*) AS cnt FROM parties GROUP BY party_code HAVING cnt > 1")) {
                assertFalse(rs.next(), "Found duplicate party_code in database!");
            }

            // Check duplicate names
            try (ResultSet rs = stmt.executeQuery("SELECT party_name, COUNT(*) AS cnt FROM parties GROUP BY party_name HAVING cnt > 1")) {
                assertFalse(rs.next(), "Found duplicate party_name in database!");
            }
        }
    }

    @Test
    void candidatePartyAssignmentsMaintainValidForeignKeys() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not available in test environment");
        loginAdmin();

        List<Candidate> candidates = ctx.getCandidateService().search(null, null, null, null);
        assertFalse(candidates.isEmpty(), "Candidates table should not be empty");

        Set<String> assignedParties = new HashSet<>();
        for (Candidate c : candidates) {
            assertNotNull(c.getPartyCode(), "Candidate " + c.getFullName() + " must have a partyCode");
            assertNotNull(c.getPartyName(), "Candidate " + c.getFullName() + " must have a partyName");
            assignedParties.add(c.getPartyCode());
        }

        // Verify that candidates belong to the required parties
        for (String assigned : assignedParties) {
            assertTrue(REQUIRED_PARTIES.contains(assigned) || assigned.startsWith("PTY"),
                    "Candidate party " + assigned + " should be among standard parties or test fixtures");
        }
    }

    @Test
    void preventsDuplicatePartyCodeOrNameViaService() {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not available in test environment");
        loginAdmin();

        Party dupCode = new Party();
        dupCode.setPartyCode("BJP");
        dupCode.setPartyName("BJP New Branch");
        dupCode.setStatus(PartyStatus.ACTIVE);

        assertThrows(DuplicateRecordException.class, () -> ctx.getPartyService().create(dupCode, null));

        Party dupName = new Party();
        dupName.setPartyCode("NEWBJP");
        dupName.setPartyName("BJP");
        dupName.setStatus(PartyStatus.ACTIVE);

        assertThrows(DuplicateRecordException.class, () -> ctx.getPartyService().create(dupName, null));
    }
}
