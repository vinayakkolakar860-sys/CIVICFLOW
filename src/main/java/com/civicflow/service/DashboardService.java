package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.model.DashboardStats;
import com.civicflow.model.ElectionStatus;
import com.civicflow.model.VerificationStatus;
import com.civicflow.repository.*;
import com.civicflow.security.AccessControlService;

import java.util.Map;

/**
 * Aggregates real-time database metrics for the Admin Dashboard.
 * Never fabricates or randomizes statistics.
 */
public class DashboardService {

    private final VoterRepository voterRepository;
    private final CandidateRepository candidateRepository;
    private final ConstituencyRepository constituencyRepository;
    private final ElectionRepository electionRepository;
    private final VotingRepository votingRepository;
    private final AuditService auditService;
    private final AccessControlService accessControl;

    public DashboardService(VoterRepository voterRepository,
                            CandidateRepository candidateRepository,
                            ConstituencyRepository constituencyRepository,
                            ElectionRepository electionRepository,
                            VotingRepository votingRepository,
                            AuditService auditService,
                            AccessControlService accessControl) {
        this.voterRepository = voterRepository;
        this.candidateRepository = candidateRepository;
        this.constituencyRepository = constituencyRepository;
        this.electionRepository = electionRepository;
        this.votingRepository = votingRepository;
        this.auditService = auditService;
        this.accessControl = accessControl;
    }

    public DashboardStats loadAdminDashboardStats() {
        accessControl.requireAdmin();

        DashboardStats stats = new DashboardStats();
        stats.setTotalVoters(voterRepository.countAll());
        stats.setVerifiedVoters(voterRepository.countByVerificationStatus(VerificationStatus.APPROVED));
        stats.setPendingVoters(voterRepository.countByVerificationStatus(VerificationStatus.PENDING));
        stats.setTotalCandidates(candidateRepository.countAll());
        stats.setTotalConstituencies(constituencyRepository.countAll());
        stats.setTotalElections(electionRepository.countAll());

        Map<String, Long> electionStatusMap = electionRepository.countByStatus();
        stats.setElectionsByStatus(electionStatusMap);
        stats.setUpcomingElections(electionStatusMap.getOrDefault(ElectionStatus.SCHEDULED.name(), 0L));
        stats.setOpenElections(electionStatusMap.getOrDefault(ElectionStatus.OPEN.name(), 0L));
        stats.setClosedElections(electionStatusMap.getOrDefault(ElectionStatus.CLOSED.name(), 0L));
        stats.setPublishedResults(electionStatusMap.getOrDefault(ElectionStatus.PUBLISHED.name(), 0L));

        stats.setVotersByVerificationStatus(voterRepository.countByVerificationStatusGrouped());
        stats.setVotersByConstituency(voterRepository.countByConstituency());
        stats.setCandidatesByParty(candidateRepository.countByParty());
        stats.setParticipationByElection(votingRepository.countParticipationByElection());
        stats.setRecentActivity(auditService.getRecentActivity(15));
        return stats;
    }
}
