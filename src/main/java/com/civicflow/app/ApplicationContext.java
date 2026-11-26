package com.civicflow.app;

import com.civicflow.audit.AuditService;
import com.civicflow.config.AppConfig;
import com.civicflow.config.DatabaseManager;
import com.civicflow.reporting.CsvReportExporter;
import com.civicflow.reporting.PdfReportExporter;
import com.civicflow.reporting.ReportService;
import com.civicflow.repository.*;
import com.civicflow.repository.jdbc.*;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.FileUploadValidator;
import com.civicflow.security.PasswordService;
import com.civicflow.security.SessionManager;
import com.civicflow.service.*;

import java.time.Clock;

/**
 * Application composition root. Wires configuration, JDBC repositories, security components,
 * domain services, and reporting modules using constructor dependency injection.
 */
public class ApplicationContext implements AutoCloseable {

    private final AppConfig appConfig;
    private final DatabaseManager databaseManager;
    private final Clock clock;

    private final PasswordService passwordService;
    private final SessionManager sessionManager;
    private final AccessControlService accessControlService;
    private final FileUploadValidator fileUploadValidator;

    private final UserRepository userRepository;
    private final ConstituencyRepository constituencyRepository;
    private final PartyRepository partyRepository;
    private final VoterRepository voterRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VotingRepository votingRepository;
    private final ResultRepository resultRepository;
    private final AuditLogRepository auditLogRepository;
    private final SettingsRepository settingsRepository;

    private final AuditService auditService;
    private final SettingsService settingsService;
    private final AuthService authService;
    private final UserManagementService userManagementService;
    private final ConstituencyService constituencyService;
    private final PartyService partyService;
    private final VoterService voterService;
    private final CandidateService candidateService;
    private final ElectionService electionService;
    private final VotingService votingService;
    private final ResultService resultService;
    private final DashboardService dashboardService;
    private final ReportService reportService;

    public ApplicationContext(AppConfig appConfig, Clock clock) {
        this.appConfig = appConfig;
        this.clock = clock;
        this.databaseManager = new DatabaseManager(appConfig);

        this.passwordService = new PasswordService();
        this.sessionManager = new SessionManager();
        this.accessControlService = new AccessControlService(sessionManager);
        this.fileUploadValidator = new FileUploadValidator();

        this.userRepository = new JdbcUserRepository(databaseManager);
        this.constituencyRepository = new JdbcConstituencyRepository(databaseManager);
        this.partyRepository = new JdbcPartyRepository(databaseManager);
        this.voterRepository = new JdbcVoterRepository(databaseManager);
        this.candidateRepository = new JdbcCandidateRepository(databaseManager);
        this.electionRepository = new JdbcElectionRepository(databaseManager);
        this.votingRepository = new JdbcVotingRepository(databaseManager);
        this.resultRepository = new JdbcResultRepository(databaseManager);
        this.auditLogRepository = new JdbcAuditLogRepository(databaseManager);
        this.settingsRepository = new JdbcSettingsRepository(databaseManager);

        this.auditService = new AuditService(auditLogRepository, sessionManager, accessControlService, clock);
        this.settingsService = new SettingsService(settingsRepository, accessControlService, auditService);
        this.authService = new AuthService(userRepository, voterRepository, passwordService, sessionManager,
                accessControlService, settingsService, auditService, clock);
        this.userManagementService = new UserManagementService(userRepository, passwordService, accessControlService, auditService);
        this.constituencyService = new ConstituencyService(constituencyRepository, electionRepository, accessControlService, auditService);
        this.partyService = new PartyService(partyRepository, accessControlService, auditService, fileUploadValidator, appConfig.getUploadsDir());
        this.voterService = new VoterService(databaseManager, voterRepository, userRepository, constituencyRepository,
                votingRepository, passwordService, accessControlService, settingsService, auditService, clock);
        this.candidateService = new CandidateService(candidateRepository, constituencyRepository, partyRepository,
                accessControlService, auditService, fileUploadValidator, appConfig.getUploadsDir());
        this.electionService = new ElectionService(electionRepository, constituencyRepository, candidateRepository,
                accessControlService, auditService, clock);
        this.votingService = new VotingService(electionRepository, voterRepository, votingRepository,
                accessControlService, auditService, clock);
        this.resultService = new ResultService(electionRepository, resultRepository, partyRepository, accessControlService);
        this.dashboardService = new DashboardService(voterRepository, candidateRepository, constituencyRepository,
                electionRepository, votingRepository, auditService, accessControlService);
        this.reportService = new ReportService(voterRepository, candidateRepository, partyRepository,
                constituencyRepository, electionRepository, votingRepository, resultService,
                auditService, settingsService, accessControlService, new PdfReportExporter(), new CsvReportExporter(), clock);
    }

    public static ApplicationContext createDefault() {
        return new ApplicationContext(AppConfig.load(), Clock.systemDefaultZone());
    }

    public AppConfig getAppConfig() { return appConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Clock getClock() { return clock; }
    public PasswordService getPasswordService() { return passwordService; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AccessControlService getAccessControlService() { return accessControlService; }
    public AuditService getAuditService() { return auditService; }
    public SettingsService getSettingsService() { return settingsService; }
    public AuthService getAuthService() { return authService; }
    public UserManagementService getUserManagementService() { return userManagementService; }
    public ConstituencyService getConstituencyService() { return constituencyService; }
    public PartyService getPartyService() { return partyService; }
    public VoterService getVoterService() { return voterService; }
    public CandidateService getCandidateService() { return candidateService; }
    public ElectionService getElectionService() { return electionService; }
    public VotingService getVotingService() { return votingService; }
    public ResultService getResultService() { return resultService; }
    public DashboardService getDashboardService() { return dashboardService; }
    public ReportService getReportService() { return reportService; }

    @Override
    public void close() {
        sessionManager.clearSession();
        databaseManager.close();
    }
}
