package com.civicflow.web;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.*;
import com.civicflow.model.*;
import com.civicflow.reporting.ReportData;
import com.civicflow.reporting.ReportType;
import com.civicflow.security.CsvSanitizer;
import com.civicflow.security.UserSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pure Core Java Embedded Live HTTP Server for CIVICFLOW — Election Management Suite (CEMS).
 * Uses JDK built-in com.sun.net.httpserver.HttpServer (zero Spring Boot or external web frameworks)
 * and delegates 100% of business logic, RBAC, transactions, and reporting to the existing ApplicationContext.
 */
public class CivicFlowWebServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CivicFlowWebServer.class);
    private static final String SESSION_COOKIE = "CIVICFLOW_SID";
    public static final int MAX_REQUEST_SIZE_BYTES = 4 * 1024 * 1024; // 4 MiB limit

    private final ApplicationContext ctx;
    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public CivicFlowWebServer(ApplicationContext ctx, int port) throws IOException {
        this.ctx = ctx;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.setExecutor(this.executor);
        registerRoutes();
    }

    public void start() {
        server.start();
        LOGGER.info("CIVICFLOW Live Server started at http://localhost:{}", port);
        System.out.println("====================================================================");
        System.out.println(" CIVICFLOW LIVE SERVER RUNNING -> http://localhost:" + port);
        System.out.println(" Connected to MySQL civicflow_db | Educational Simulation Platform");
        System.out.println("====================================================================");
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public int getPort() {
        return port;
    }

    @Override
    public void close() {
        server.stop(1);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        sessions.clear();
        ctx.close();
        LOGGER.info("CIVICFLOW Live Server stopped.");
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String envPort = System.getenv("CIVICFLOW_PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort.trim());
        } else if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        ApplicationContext ctx = ApplicationContext.createDefault();
        if (!ctx.getDatabaseManager().testConnection()) {
            System.err.println("ERROR: Could not connect to MySQL civicflow_db. Configure CIVICFLOW_DB_URL/USER/PASSWORD.");
            System.exit(1);
        }

        CivicFlowWebServer webServer = new CivicFlowWebServer(ctx, port);
        Runtime.getRuntime().addShutdownHook(new Thread(webServer::close));
        webServer.start();
    }

    private void registerRoutes() {
        server.createContext("/", this::handleRoot);
        server.createContext("/api/", this::handleApi);
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendStatus(ex, 405, "Method Not Allowed");
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/web/index.html")) {
            if (in == null) {
                sendStatus(ex, 404, "Web UI resource not found.");
                return;
            }
            byte[] html = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);

        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Cookie, Authorization, X-Requested-With, X-Session-ID");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        }
        if ("OPTIONS".equals(method)) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String contentLengthHeader = ex.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthHeader != null) {
            try {
                long len = Long.parseLong(contentLengthHeader.trim());
                if (len > MAX_REQUEST_SIZE_BYTES) {
                    LOGGER.warn("Request Content-Length {} exceeds maximum limit of 4 MiB", len);
                    sendJson(ex, 413, Map.of("error", "Payload Too Large: Maximum allowed request size is 4 MiB."));
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String sid = readSessionCookie(ex);
        UserSession activeSession = sid != null ? sessions.get(sid) : null;

        ctx.getSessionManager().beginThreadScope(activeSession);
        try {
            if ("/api/health".equals(path) && "GET".equals(method)) {
                sendJson(ex, 200, Map.of("status", "UP", "database", ctx.getDatabaseManager().testConnection(), "app", ctx.getAppConfig().getAppName()));
                return;
            }
            if ("/api/auth/login".equals(path) && "POST".equals(method)) {
                Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
                UserSession loggedIn = ctx.getAuthService().login(body.get("username"), body.get("password"));
                String newSid = UUID.randomUUID().toString();
                sessions.put(newSid, loggedIn);
                ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + newSid + "; Path=/; HttpOnly; SameSite=Lax");
                Map<String, Object> sessionMap = sessionToMap(loggedIn);
                sessionMap.put("sessionId", newSid);
                sendJson(ex, 200, sessionMap);
                return;
            }
            if ("/api/auth/logout".equals(path) && "POST".equals(method)) {
                ctx.getAuthService().logout();
                if (sid != null) sessions.remove(sid);
                ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                sendJson(ex, 200, Map.of("ok", true));
                return;
            }
            if ("/api/auth/me".equals(path) && "GET".equals(method)) {
                if (activeSession == null) {
                    sendJson(ex, 200, Map.of("authenticated", false));
                } else {
                    sendJson(ex, 200, sessionToMap(activeSession));
                }
                return;
            }
            if ("/api/auth/change-password".equals(path) && "POST".equals(method)) {
                if (activeSession == null) {
                    sendStatus(ex, 401, "Authentication required.");
                    return;
                }
                Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
                String currentPassword = body.get("currentPassword");
                String newPassword = body.get("newPassword");
                String confirmPassword = body.get("confirmPassword");
                ctx.getAuthService().changeOwnPassword(currentPassword, newPassword, confirmPassword);
                ctx.getSessionManager().getCurrentSession().ifPresent(updated -> {
                    if (sid != null) sessions.put(sid, updated);
                });
                sendJson(ex, 200, Map.of("ok", true, "message", "Password updated successfully."));
                return;
            }

            // Dashboard
            if ("/api/dashboard".equals(path) && "GET".equals(method)) {
                DashboardStats s = ctx.getDashboardService().loadAdminDashboardStats();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("totalVoters", s.getTotalVoters());
                out.put("verifiedVoters", s.getVerifiedVoters());
                out.put("pendingVoters", s.getPendingVoters());
                out.put("totalCandidates", s.getTotalCandidates());
                out.put("totalConstituencies", s.getTotalConstituencies());
                out.put("totalElections", s.getTotalElections());
                out.put("upcomingElections", s.getUpcomingElections());
                out.put("openElections", s.getOpenElections());
                out.put("closedElections", s.getClosedElections());
                out.put("publishedResults", s.getPublishedResults());
                out.put("electionsByStatus", s.getElectionsByStatus());
                out.put("votersByVerificationStatus", s.getVotersByVerificationStatus());
                out.put("votersByConstituency", s.getVotersByConstituency());
                out.put("candidatesByParty", s.getCandidatesByParty());
                out.put("participationByElection", s.getParticipationByElection());
                out.put("recentActivity", s.getRecentActivity().stream().map(this::auditToMap).toList());
                sendJson(ex, 200, out);
                return;
            }

            // Constituencies
            if ("/api/constituencies".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                List<Map<String, Object>> list = ctx.getConstituencyService().getAll(q.get("search"), null)
                        .stream().map(this::constituencyToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if ("/api/constituencies".equals(path) && "POST".equals(method)) {
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                Constituency c = new Constituency();
                c.setConstituencyCode(b.get("code"));
                c.setConstituencyName(b.get("name"));
                c.setCity(b.get("city"));
                c.setState(b.get("state"));
                c.setDescription(b.get("description"));
                c.setStatus(ConstituencyStatus.ACTIVE);
                sendJson(ex, 201, constituencyToMap(ctx.getConstituencyService().create(c)));
                return;
            }

            // Parties
            if ("/api/parties".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                List<Map<String, Object>> list = ctx.getPartyService().getAll(q.get("search"), null)
                        .stream().map(this::partyToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if ("/api/parties".equals(path) && "POST".equals(method)) {
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                Party p = new Party();
                p.setPartyCode(b.get("code"));
                p.setPartyName(b.get("name"));
                p.setDescription(b.get("description"));
                p.setStatus(PartyStatus.ACTIVE);
                sendJson(ex, 201, partyToMap(ctx.getPartyService().create(p, null)));
                return;
            }

            // Voters
            if ("/api/voters".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                VerificationStatus vs = q.get("verification") != null && !q.get("verification").isBlank()
                        ? VerificationStatus.valueOf(q.get("verification")) : null;
                List<Map<String, Object>> list = ctx.getVoterService().findAllVoters(q.get("search"), vs, null, null)
                        .stream().map(this::voterToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if ("/api/voters".equals(path) && "POST".equals(method)) {
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                Voter v = new Voter();
                v.setFirstName(b.get("firstName"));
                v.setLastName(b.get("lastName"));
                v.setEmail(b.get("email"));
                v.setPhoneNumber(b.get("phoneNumber"));
                v.setDateOfBirth(LocalDate.parse(b.get("dateOfBirth")));
                v.setAddress(b.get("address"));
                v.setCity(b.get("city"));
                v.setState(b.get("state"));
                v.setConstituencyId(Long.parseLong(b.get("constituencyId")));
                sendJson(ex, 201, voterToMap(ctx.getVoterService().registerVoter(v, b.get("username"), b.get("password"))));
                return;
            }
            if (path.startsWith("/api/voters/") && "POST".equals(method)) {
                String[] parts = path.split("/");
                if (parts.length == 5) {
                    long id = Long.parseLong(parts[3]);
                    switch (parts[4]) {
                        case "approve" -> ctx.getVoterService().approveVoter(id);
                        case "reject" -> ctx.getVoterService().rejectVoter(id);
                        case "suspend" -> ctx.getVoterService().suspendVoter(id);
                        case "reactivate" -> ctx.getVoterService().reactivateVoter(id);
                        default -> { sendStatus(ex, 404, "Unknown voter action"); return; }
                    }
                    sendJson(ex, 200, Map.of("ok", true));
                    return;
                }
            }

            // Candidates
            if ("/api/candidates".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                List<Map<String, Object>> list = ctx.getCandidateService().search(q.get("search"), null, null, null)
                        .stream().map(this::candidateToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if ("/api/candidates".equals(path) && "POST".equals(method)) {
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                Candidate c = new Candidate();
                c.setFullName(b.get("fullName"));
                c.setAge(Integer.parseInt(b.get("age")));
                c.setEmail(b.get("email"));
                c.setContactNumber(b.get("contactNumber"));
                c.setPartyId(Long.parseLong(b.get("partyId")));
                c.setConstituencyId(Long.parseLong(b.get("constituencyId")));
                c.setProfileDescription(b.get("profileDescription"));
                sendJson(ex, 201, candidateToMap(ctx.getCandidateService().registerCandidate(c, null)));
                return;
            }
            if (path.startsWith("/api/candidates/") && "POST".equals(method)) {
                String[] parts = path.split("/");
                if (parts.length == 5) {
                    long id = Long.parseLong(parts[3]);
                    switch (parts[4]) {
                        case "approve" -> ctx.getCandidateService().approveCandidate(id);
                        case "reject" -> ctx.getCandidateService().rejectCandidate(id);
                        case "delete" -> ctx.getCandidateService().deleteCandidate(id);
                        default -> { sendStatus(ex, 404, "Unknown candidate action"); return; }
                    }
                    sendJson(ex, 200, Map.of("ok", true));
                    return;
                }
            }

            // Elections
            if ("/api/elections".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                List<Map<String, Object>> list = ctx.getElectionService().getAllElections(q.get("search"), null)
                        .stream().map(this::electionToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if ("/api/elections".equals(path) && "POST".equals(method)) {
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                Election e = new Election();
                e.setElectionName(b.get("electionName"));
                e.setElectionType(ElectionType.valueOf(b.getOrDefault("electionType", "GENERAL")));
                e.setStartDateTime(LocalDateTime.parse(b.get("startDateTime")));
                e.setEndDateTime(LocalDateTime.parse(b.get("endDateTime")));
                e.setDescription(b.get("description"));
                sendJson(ex, 201, electionToMap(ctx.getElectionService().createElection(e)));
                return;
            }
            if (path.startsWith("/api/elections/") && "POST".equals(method)) {
                String[] parts = path.split("/");
                if (parts.length == 5) {
                    long id = Long.parseLong(parts[3]);
                    switch (parts[4]) {
                        case "schedule" -> ctx.getElectionService().scheduleElection(id);
                        case "open" -> ctx.getElectionService().openElection(id);
                        case "close" -> ctx.getElectionService().closeElection(id);
                        case "publish" -> ctx.getElectionService().publishResults(id);
                        case "cancel" -> ctx.getElectionService().cancelElection(id);
                        case "assign-constituency" -> {
                            Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                            ctx.getElectionService().assignConstituency(id, Long.parseLong(b.get("constituencyId")));
                        }
                        case "assign-candidate" -> {
                            Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                            ctx.getElectionService().assignCandidate(id, Long.parseLong(b.get("candidateId")));
                        }
                        default -> { sendStatus(ex, 404, "Unknown election action"); return; }
                    }
                    sendJson(ex, 200, Map.of("ok", true));
                    return;
                }
            }

            // Voter Portal & Simulated Voting
            if ("/api/voter/portal".equals(path) && "GET".equals(method)) {
                Voter v = ctx.getVoterService().getOwnVoterProfile();
                List<Election> eligible = ctx.getElectionService().getEligibleElectionsForCurrentVoter();
                List<VoterParticipation> history = ctx.getVoterService().getParticipationHistory(v.getVoterId());
                Set<Long> votedIds = new HashSet<>();
                history.forEach(p -> votedIds.add(p.getElectionId()));

                List<Map<String, Object>> elMaps = new ArrayList<>();
                for (Election e : eligible) {
                    Map<String, Object> m = electionToMap(e);
                    m.put("hasVoted", votedIds.contains(e.getElectionId()));
                    elMaps.add(m);
                }
                sendJson(ex, 200, Map.of(
                        "voter", voterToMap(v),
                        "eligibleElections", elMaps,
                        "participationHistory", history.stream().map(this::participationToMap).toList()
                ));
                return;
            }
            if (path.matches("/api/voting/\\d+/candidates") && "GET".equals(method)) {
                long elId = Long.parseLong(path.split("/")[3]);
                List<Map<String, Object>> cands = ctx.getVotingService().getEligibleCandidatesForBallot(elId)
                        .stream().map(this::candidateToMap).toList();
                sendJson(ex, 200, cands);
                return;
            }
            if (path.matches("/api/voting/\\d+/cast") && "POST".equals(method)) {
                long elId = Long.parseLong(path.split("/")[3]);
                Map<String, String> b = JsonUtil.parseFlatObject(readBody(ex));
                long candId = Long.parseLong(b.get("candidateId"));
                VotingReceipt r = ctx.getVotingService().castSimulatedBallot(elId, candId);
                sendJson(ex, 200, Map.of(
                        "confirmationCode", r.getConfirmationCode(),
                        "electionName", r.getElectionName(),
                        "submittedAt", r.getSubmittedAt(),
                        "message", r.getMessage()
                ));
                return;
            }

            // Results
            if ("/api/results/elections".equals(path) && "GET".equals(method)) {
                List<Map<String, Object>> list = ctx.getResultService().getViewableResultElections()
                        .stream().map(this::electionToMap).toList();
                sendJson(ex, 200, list);
                return;
            }
            if (path.matches("/api/results/\\d+") && "GET".equals(method)) {
                long elId = Long.parseLong(path.split("/")[3]);
                ElectionResultSummary sum = ctx.getResultService().getElectionResults(elId);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("election", electionToMap(sum.getElection()));
                out.put("totalEligibleVoters", sum.getTotalEligibleVoters());
                out.put("totalParticipants", sum.getTotalParticipants());
                out.put("totalBallotsCast", sum.getTotalBallotsCast());
                out.put("turnoutPercentage", sum.getTurnoutPercentage());
                out.put("published", sum.isPublished());
                out.put("leadingPartySummary", sum.getLeadingPartySummary() != null ? sum.getLeadingPartySummary() : "");
                out.put("partyResults", sum.getPartyResults().stream().map(pr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("partyCode", pr.getPartyCode());
                    m.put("partyName", pr.getPartyName());
                    m.put("candidateCount", pr.getCandidateCount());
                    m.put("totalVotes", pr.getTotalVotes());
                    m.put("votePercentage", pr.getVotePercentage());
                    m.put("isLeading", pr.isLeading());
                    m.put("isTiedForLead", pr.isTiedForLead());
                    return m;
                }).toList());
                out.put("candidateResults", sum.getCandidateResults().stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("candidateName", r.getCandidateName());
                    m.put("partyCode", r.getPartyCode());
                    m.put("partyName", r.getPartyName());
                    m.put("constituencyName", r.getConstituencyName());
                    m.put("voteCount", r.getVoteCount());
                    m.put("percentage", r.getPercentage());
                    return m;
                }).toList());
                out.put("constituencyResults", sum.getConstituencyResults().stream().map(cr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("constituencyCode", cr.getConstituencyCode());
                    m.put("constituencyName", cr.getConstituencyName());
                    m.put("eligibleVoters", cr.getEligibleVoters());
                    m.put("totalBallots", cr.getTotalBallots());
                    m.put("turnoutPercentage", cr.getTurnoutPercentage());
                    m.put("leadingCandidateName", cr.getLeadingCandidateName());
                    return m;
                }).toList());
                sendJson(ex, 200, out);
                return;
            }

            // Reports (Preview + PDF/CSV Download)
            if ("/api/reports".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                ReportType rt = ReportType.valueOf(q.getOrDefault("type", "VOTER_REGISTRATION"));
                ReportData data = ctx.getReportService().generateReport(rt, q.get("search"), null, null, null, null);
                String format = q.get("format");
                if ("pdf".equalsIgnoreCase(format) || "csv".equalsIgnoreCase(format)) {
                    boolean pdf = "pdf".equalsIgnoreCase(format);
                    Path tmp = Files.createTempFile("civicflow_dl_", pdf ? ".pdf" : ".csv");
                    try {
                        if (pdf) ctx.getReportService().exportPdf(data, tmp);
                        else ctx.getReportService().exportCsv(data, tmp);
                        byte[] bytes = Files.readAllBytes(tmp);
                        String fname = CsvSanitizer.sanitizeFileName("civicflow_" + rt.name().toLowerCase(), pdf ? ".pdf" : ".csv");
                        ex.getResponseHeaders().set("Content-Type", pdf ? "application/pdf" : "text/csv; charset=UTF-8");
                        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fname + "\"");
                        ex.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
                    } finally {
                        Files.deleteIfExists(tmp);
                    }
                    return;
                }
                sendJson(ex, 200, Map.of(
                        "title", data.getTitle(),
                        "subtitle", data.getSubtitle(),
                        "watermark", data.getWatermark(),
                        "headers", data.getHeaders(),
                        "rows", data.getRows()
                ));
                return;
            }

            // Audit Logs
            if ("/api/audit".equals(path) && "GET".equals(method)) {
                Map<String, String> q = parseQuery(ex);
                PageResult<AuditLog> page = ctx.getAuditService().searchLogs(q.get("module"), q.get("search"), null, null, 1, 50);
                sendJson(ex, 200, page.getItems().stream().map(this::auditToMap).toList());
                return;
            }

            // Super Admin Settings & Admin Accounts
            if ("/api/settings".equals(path) && "GET".equals(method)) {
                sendJson(ex, 200, Map.of(
                        "settings", ctx.getSettingsService().getAllSettings().stream().map(s -> Map.of(
                                "key", s.getSettingKey(), "value", s.getSettingValue(),
                                "description", s.getDescription() != null ? s.getDescription() : "")).toList(),
                        "admins", ctx.getUserManagementService().getAllAdminAccounts().stream().map(u -> Map.of(
                                "userId", u.getUserId(), "username", u.getUsername(), "fullName", u.getFullName(),
                                "email", u.getEmail(), "role", u.getRoleType().name(), "status", u.getAccountStatus().name())).toList()
                ));
                return;
            }

            sendJson(ex, 404, Map.of("error", "API endpoint not found."));
        } catch (PayloadTooLargeException ple) {
            LOGGER.warn("Payload size rejected (HTTP 413): {}", ple.getUserMessage());
            sendJson(ex, 413, Map.of("error", ple.getUserMessage()));
        } catch (AuthenticationException ae) {
            sendJson(ex, 401, Map.of("error", ae.getUserMessage()));
        } catch (AuthorizationException aze) {
            sendJson(ex, 403, Map.of("error", aze.getUserMessage()));
        } catch (ValidationException | DuplicateRecordException | VotingException | ResourceNotFoundException de) {
            sendJson(ex, 400, Map.of("error", de.getUserMessage()));
        } catch (Exception e) {
            String safeMsg = GlobalExceptionHandler.handleAndGetUserMessage(e, method + " " + path);
            sendJson(ex, 500, Map.of("error", safeMsg));
        } finally {
            ctx.getSessionManager().endThreadScope();
        }
    }

    private Map<String, Object> sessionToMap(UserSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authenticated", true);
        m.put("userId", s.getUserId());
        m.put("username", s.getUsername());
        m.put("fullName", s.getFullName());
        m.put("email", s.getUser().getEmail());
        m.put("role", s.getRole().name());
        m.put("mustChangePassword", s.isMustChangePassword());
        if (s.getVoterProfile() != null) {
            m.put("voterCode", s.getVoterProfile().getVoterCode());
            m.put("constituencyName", s.getVoterProfile().getConstituencyName());
        }
        return m;
    }

    private Map<String, Object> constituencyToMap(Constituency c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getConstituencyId());
        m.put("code", c.getConstituencyCode());
        m.put("name", c.getConstituencyName());
        m.put("city", c.getCity());
        m.put("state", c.getState());
        m.put("status", c.getStatus().name());
        m.put("voterCount", c.getVoterCount());
        m.put("candidateCount", c.getCandidateCount());
        return m;
    }

    private Map<String, Object> partyToMap(Party p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getPartyId());
        m.put("code", p.getPartyCode());
        m.put("name", p.getPartyName());
        m.put("description", p.getDescription() != null ? p.getDescription() : "");
        m.put("status", p.getStatus().name());
        m.put("candidateCount", p.getCandidateCount());
        return m;
    }

    private Map<String, Object> voterToMap(Voter v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getVoterId());
        m.put("voterCode", v.getVoterCode());
        m.put("fullName", v.getFullName());
        m.put("username", v.getUsername());
        m.put("email", v.getEmail());
        m.put("phoneNumber", v.getPhoneNumber());
        m.put("constituencyName", v.getConstituencyName());
        m.put("city", v.getCity());
        m.put("verificationStatus", v.getVerificationStatus().name());
        m.put("accountStatus", v.getAccountStatus().name());
        return m;
    }

    private Map<String, Object> candidateToMap(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getCandidateId());
        m.put("code", c.getCandidateCode());
        m.put("fullName", c.getFullName());
        m.put("age", c.getAge());
        m.put("partyCode", c.getPartyCode());
        m.put("partyName", c.getPartyName());
        m.put("constituencyName", c.getConstituencyName());
        m.put("email", c.getEmail());
        m.put("profileDescription", c.getProfileDescription() != null ? c.getProfileDescription() : "");
        m.put("status", c.getStatus().name());
        return m;
    }

    private Map<String, Object> electionToMap(Election e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getElectionId());
        m.put("code", e.getElectionCode());
        m.put("name", e.getElectionName());
        m.put("description", e.getDescription() != null ? e.getDescription() : "");
        m.put("type", e.getElectionType().getLabel());
        m.put("startDateTime", e.getStartDateTime());
        m.put("endDateTime", e.getEndDateTime());
        m.put("status", e.getStatus().name());
        m.put("constituencyCount", e.getConstituencyCount());
        m.put("candidateCount", e.getCandidateCount());
        m.put("totalParticipants", e.getTotalParticipants());
        return m;
    }

    private Map<String, Object> participationToMap(VoterParticipation p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("electionCode", p.getElectionCode());
        m.put("electionName", p.getElectionName());
        m.put("submittedAt", p.getSubmittedAt());
        m.put("status", p.getParticipationStatus());
        return m;
    }

    private Map<String, Object> auditToMap(AuditLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getAuditId());
        m.put("timestamp", l.getTimestamp());
        m.put("username", l.getUsername() != null ? l.getUsername() : "SYSTEM");
        m.put("module", l.getModule());
        m.put("action", l.getAction());
        m.put("details", l.getDetails() != null ? l.getDetails() : "");
        m.put("status", l.getResultStatus().name());
        return m;
    }

    private String readBody(HttpExchange ex) throws IOException {
        String contentLengthHeader = ex.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthHeader != null) {
            try {
                long len = Long.parseLong(contentLengthHeader.trim());
                if (len > MAX_REQUEST_SIZE_BYTES) {
                    throw new PayloadTooLargeException("Request payload size of " + len + " bytes exceeds maximum allowed limit of 4 MiB.");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int r;
            long total = 0;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > MAX_REQUEST_SIZE_BYTES) {
                    throw new PayloadTooLargeException("Request payload exceeded maximum allowed limit of 4 MiB.");
                }
                baos.write(buf, 0, r);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private String readSessionCookie(HttpExchange ex) {
        String customHeader = ex.getRequestHeaders().getFirst("X-Session-ID");
        if (customHeader != null && !customHeader.isBlank()) {
            return customHeader.trim();
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && SESSION_COOKIE.equals(kv[0].trim())) {
                    return kv[1].trim();
                }
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
        return map;
    }

    private void sendJson(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendStatus(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
