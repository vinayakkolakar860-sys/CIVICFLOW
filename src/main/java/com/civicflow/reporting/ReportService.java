package com.civicflow.reporting;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.*;
import com.civicflow.repository.*;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.UserSession;
import com.civicflow.service.ResultService;
import com.civicflow.service.SettingsService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates all 11 database-backed CIVICFLOW reports and delegates export to PDF/CSV exporters.
 */
public class ReportService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VoterRepository voterRepository;
    private final CandidateRepository candidateRepository;
    private final PartyRepository partyRepository;
    private final ConstituencyRepository constituencyRepository;
    private final ElectionRepository electionRepository;
    private final VotingRepository votingRepository;
    private final ResultService resultService;
    private final AuditService auditService;
    private final SettingsService settingsService;
    private final AccessControlService accessControl;
    private final PdfReportExporter pdfExporter;
    private final CsvReportExporter csvExporter;
    private final Clock clock;

    public ReportService(VoterRepository voterRepository,
                         CandidateRepository candidateRepository,
                         PartyRepository partyRepository,
                         ConstituencyRepository constituencyRepository,
                         ElectionRepository electionRepository,
                         VotingRepository votingRepository,
                         ResultService resultService,
                         AuditService auditService,
                         SettingsService settingsService,
                         AccessControlService accessControl,
                         PdfReportExporter pdfExporter,
                         CsvReportExporter csvExporter,
                         Clock clock) {
        this.voterRepository = voterRepository;
        this.candidateRepository = candidateRepository;
        this.partyRepository = partyRepository;
        this.constituencyRepository = constituencyRepository;
        this.electionRepository = electionRepository;
        this.votingRepository = votingRepository;
        this.resultService = resultService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.accessControl = accessControl;
        this.pdfExporter = pdfExporter;
        this.csvExporter = csvExporter;
        this.clock = clock;
    }

    public ReportData generateReport(ReportType type, String searchText, Long electionId,
                                     Long constituencyId, LocalDate fromDate, LocalDate toDate) {
        UserSession session = accessControl.requireAdmin();
        if (type == null) {
            throw new ValidationException("Report type is required.");
        }

        String watermark = settingsService.getStringSetting(
                SettingsService.KEY_DEMO_BANNER,
                "DEMONSTRATION / SIMULATED DATA — Educational Election Simulation Only");
        LocalDateTime now = LocalDateTime.now(clock);
        String actor = session.getFullName() + " (" + session.getRole().getDisplayName() + ")";

        return switch (type) {
            case VOTER_REGISTRATION -> buildVoterReport(type.getTitle(), null, searchText, constituencyId, fromDate, toDate, watermark, actor, now);
            case APPROVED_VOTERS -> buildVoterReport(type.getTitle(), VerificationStatus.APPROVED, searchText, constituencyId, fromDate, toDate, watermark, actor, now);
            case PENDING_REGISTRATIONS -> buildVoterReport(type.getTitle(), VerificationStatus.PENDING, searchText, constituencyId, fromDate, toDate, watermark, actor, now);
            case CANDIDATE_DIRECTORY -> buildCandidateReport(searchText, constituencyId, watermark, actor, now);
            case PARTY_DIRECTORY -> buildPartyReport(searchText, watermark, actor, now);
            case CONSTITUENCY_SUMMARY -> buildConstituencyReport(searchText, watermark, actor, now);
            case ELECTION_SCHEDULE -> buildElectionReport(type.getTitle(), searchText, true, watermark, actor, now);
            case ELECTION_STATUS -> buildElectionReport(type.getTitle(), searchText, false, watermark, actor, now);
            case SIMULATED_PARTICIPATION -> buildParticipationReport(electionId, watermark, actor, now);
            case PUBLISHED_RESULTS -> buildPublishedResultsReport(electionId, watermark, actor, now);
            case AUDIT_LOG -> buildAuditLogReport(searchText, fromDate, toDate, watermark, actor, now);
        };
    }

    public Path exportPdf(ReportData data, Path destination) {
        accessControl.requireAuthenticated();
        Path saved = pdfExporter.export(data, destination);
        auditService.record("REPORTS", "EXPORT_PDF", "Exported PDF report: " + data.getTitle(), AuditResultStatus.SUCCESS);
        return saved;
    }

    public Path exportCsv(ReportData data, Path destination) {
        accessControl.requireAuthenticated();
        Path saved = csvExporter.export(data, destination);
        auditService.record("REPORTS", "EXPORT_CSV", "Exported CSV report: " + data.getTitle(), AuditResultStatus.SUCCESS);
        return saved;
    }

    public ReportData buildElectionResultReportForCurrentUser(long electionId) {
        UserSession session = accessControl.requireAuthenticated();
        ElectionResultSummary summary = resultService.getElectionResults(electionId);
        String watermark = settingsService.getStringSetting(
                SettingsService.KEY_DEMO_BANNER,
                "DEMONSTRATION / SIMULATED RESULTS — Educational Election Simulation Only");

        List<String> headers = List.of("Constituency", "Candidate Code", "Candidate Name", "Party", "Simulated Votes", "Vote Share (%)");
        List<List<String>> rows = new ArrayList<>();
        for (CandidateResult r : summary.getCandidateResults()) {
            String pDisp = r.getPartyCode() != null && r.getPartyCode().equalsIgnoreCase(r.getPartyName())
                    ? r.getPartyName()
                    : r.getPartyName() + " (" + r.getPartyCode() + ")";
            rows.add(List.of(
                    r.getConstituencyName(),
                    r.getCandidateCode(),
                    r.getCandidateName(),
                    pDisp,
                    String.valueOf(r.getVoteCount()),
                    String.format("%.2f%%", r.getPercentage())
            ));
        }

        String subtitle = "Election: " + summary.getElection().getElectionName()
                + " (" + summary.getElection().getElectionCode() + ") | Status: " + summary.getElection().getStatus()
                + " | Total Simulated Ballots: " + summary.getTotalBallotsCast()
                + " | Turnout: " + String.format("%.2f%%", summary.getTurnoutPercentage())
                + (summary.getLeadingPartySummary() != null && !summary.getLeadingPartySummary().isBlank()
                ? " | Leading Party: " + summary.getLeadingPartySummary() : "");

        return new ReportData(
                "Simulated Election Results — " + summary.getElection().getElectionName(),
                subtitle,
                watermark,
                session.getFullName(),
                LocalDateTime.now(clock),
                headers,
                rows
        );
    }

    private ReportData buildVoterReport(String title, VerificationStatus statusFilter, String searchText,
                                        Long constituencyId, LocalDate fromDate, LocalDate toDate,
                                        String watermark, String actor, LocalDateTime now) {
        List<Voter> voters = voterRepository.findAll(searchText, statusFilter, null, constituencyId).stream()
                .filter(v -> fromDate == null || (v.getRegistrationDate() != null && !v.getRegistrationDate().isBefore(fromDate)))
                .filter(v -> toDate == null || (v.getRegistrationDate() != null && !v.getRegistrationDate().isAfter(toDate)))
                .toList();

        List<String> headers = List.of("Voter Code", "Full Name", "Email", "Constituency", "City", "Reg. Date", "Verification", "Account");
        List<List<String>> rows = voters.stream()
                .map(v -> List.of(
                        v.getVoterCode(),
                        v.getFullName(),
                        v.getEmail(),
                        v.getConstituencyName(),
                        v.getCity(),
                        v.getRegistrationDate() != null ? v.getRegistrationDate().format(DATE_FMT) : "",
                        v.getVerificationStatus().name(),
                        v.getAccountStatus().name()
                ))
                .toList();
        return new ReportData(title, "Filtered fictional voter registry snapshot", watermark, actor, now, headers, rows);
    }

    private ReportData buildCandidateReport(String searchText, Long constituencyId, String watermark, String actor, LocalDateTime now) {
        List<Candidate> list = candidateRepository.search(searchText, constituencyId, null, null);
        List<String> headers = List.of("Code", "Candidate Name", "Age", "Party", "Constituency", "Email", "Status");
        List<List<String>> rows = list.stream()
                .map(c -> {
                    String pDisp = c.getPartyCode() != null && c.getPartyCode().equalsIgnoreCase(c.getPartyName())
                            ? c.getPartyName()
                            : c.getPartyName() + " (" + c.getPartyCode() + ")";
                    return List.of(
                            c.getCandidateCode(),
                            c.getFullName(),
                            String.valueOf(c.getAge()),
                            pDisp,
                            c.getConstituencyName(),
                            c.getEmail(),
                            c.getStatus().name()
                    );
                })
                .toList();
        return new ReportData("Candidate Report", "Registered candidates directory", watermark, actor, now, headers, rows);
    }

    private ReportData buildPartyReport(String searchText, String watermark, String actor, LocalDateTime now) {
        List<Party> list = partyRepository.findAll(searchText, null);
        List<String> headers = List.of("Party Code", "Party Name", "Status", "Candidates", "Description");
        List<List<String>> rows = list.stream()
                .map(p -> List.of(
                        p.getPartyCode(),
                        p.getPartyName(),
                        p.getStatus().name(),
                        String.valueOf(p.getCandidateCount()),
                        p.getDescription() != null ? p.getDescription() : ""
                ))
                .toList();
        return new ReportData("Party Directory Report", "Registered political parties directory", watermark, actor, now, headers, rows);
    }

    private ReportData buildConstituencyReport(String searchText, String watermark, String actor, LocalDateTime now) {
        List<Constituency> list = constituencyRepository.findAll(searchText, null);
        List<String> headers = List.of("Code", "Constituency Name", "City", "State", "Voters", "Candidates", "Status");
        List<List<String>> rows = list.stream()
                .map(c -> List.of(
                        c.getConstituencyCode(),
                        c.getConstituencyName(),
                        c.getCity(),
                        c.getState(),
                        String.valueOf(c.getVoterCount()),
                        String.valueOf(c.getCandidateCount()),
                        c.getStatus().name()
                ))
                .toList();
        return new ReportData("Constituency Report", "Fictional constituency coverage summary", watermark, actor, now, headers, rows);
    }

    private ReportData buildElectionReport(String title, String searchText, boolean scheduleView,
                                           String watermark, String actor, LocalDateTime now) {
        List<Election> list = electionRepository.findAll(searchText, null);
        List<String> headers = scheduleView
                ? List.of("Code", "Election Name", "Type", "Start Date/Time", "End Date/Time", "Status")
                : List.of("Code", "Election Name", "Status", "Constituencies", "Candidates", "Participants");
        List<List<String>> rows = list.stream()
                .map(e -> scheduleView
                        ? List.of(e.getElectionCode(), e.getElectionName(), e.getElectionType().getLabel(),
                                e.getStartDateTime().format(DT_FMT), e.getEndDateTime().format(DT_FMT), e.getStatus().name())
                        : List.of(e.getElectionCode(), e.getElectionName(), e.getStatus().name(),
                                String.valueOf(e.getConstituencyCount()), String.valueOf(e.getCandidateCount()),
                                String.valueOf(e.getTotalParticipants())))
                .toList();
        return new ReportData(title, "Simulated election governance overview", watermark, actor, now, headers, rows);
    }

    private ReportData buildParticipationReport(Long electionId, String watermark, String actor, LocalDateTime now) {
        List<String> headers = List.of("Participation ID", "Election Code", "Election Name", "Submitted At", "Status");
        List<VoterParticipation> records = new ArrayList<>();
        if (electionId != null) {
            records.addAll(votingRepository.findParticipationByElection(electionId));
        } else {
            for (Election e : electionRepository.findAll(null, null)) {
                records.addAll(votingRepository.findParticipationByElection(e.getElectionId()));
            }
        }
        // Strictly omits voter ballot selections; reports participation log only
        List<List<String>> rows = records.stream()
                .map(p -> List.of(
                        String.valueOf(p.getParticipationId()),
                        p.getElectionCode(),
                        p.getElectionName(),
                        p.getSubmittedAt() != null ? p.getSubmittedAt().format(DT_FMT) : "",
                        p.getParticipationStatus()
                ))
                .toList();
        return new ReportData("Simulated Participation Report",
                "Participation log without ballot selection linkage", watermark, actor, now, headers, rows);
    }

    private ReportData buildPublishedResultsReport(Long electionId, String watermark, String actor, LocalDateTime now) {
        List<Election> elections;
        if (electionId != null) {
            Election e = electionRepository.findById(electionId)
                    .orElseThrow(() -> new ValidationException("Selected election not found."));
            if (e.getStatus() != ElectionStatus.PUBLISHED) {
                throw new ValidationException("Selected election does not have PUBLISHED results yet.");
            }
            elections = List.of(e);
        } else {
            elections = electionRepository.findPublishedElections();
        }

        List<String> headers = List.of("Election", "Constituency", "Candidate", "Party", "Simulated Votes", "Share (%)");
        List<List<String>> rows = new ArrayList<>();
        for (Election e : elections) {
            ElectionResultSummary summary = resultService.getElectionResults(e.getElectionId());
            for (CandidateResult r : summary.getCandidateResults()) {
                rows.add(List.of(
                        e.getElectionName(),
                        r.getConstituencyName(),
                        r.getCandidateName(),
                        r.getPartyCode(),
                        String.valueOf(r.getVoteCount()),
                        String.format("%.2f%%", r.getPercentage())
                ));
            }
        }
        return new ReportData("Published Election Result Report",
                "Aggregated simulated results from published elections", watermark, actor, now, headers, rows);
    }

    private ReportData buildAuditLogReport(String searchText, LocalDate fromDate, LocalDate toDate,
                                           String watermark, String actor, LocalDateTime now) {
        List<AuditLog> logs = auditService.exportLogs(null, searchText, fromDate, toDate, 1000);
        List<String> headers = List.of("Audit ID", "Timestamp", "Actor", "Module", "Action", "Result", "Details");
        List<List<String>> rows = logs.stream()
                .map(l -> List.of(
                        String.valueOf(l.getAuditId()),
                        l.getTimestamp() != null ? l.getTimestamp().format(DT_FMT) : "",
                        l.getUsername() != null ? l.getUsername() : "SYSTEM",
                        l.getModule(),
                        l.getAction(),
                        l.getResultStatus().name(),
                        l.getDetails() != null ? l.getDetails() : ""
                ))
                .toList();
        return new ReportData("Audit Log Report", "System activity and security audit trail", watermark, actor, now, headers, rows);
    }
}
