package com.civicflow.service;

import com.civicflow.exception.AuthorizationException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.model.*;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.repository.PartyRepository;
import com.civicflow.repository.ResultRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.UserSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes simulated election results from database aggregations and enforces publication access controls.
 */
public class ResultService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResultService.class);

    private final ElectionRepository electionRepository;
    private final ResultRepository resultRepository;
    private final PartyRepository partyRepository;
    private final AccessControlService accessControl;

    public ResultService(ElectionRepository electionRepository,
                         ResultRepository resultRepository,
                         AccessControlService accessControl) {
        this(electionRepository, resultRepository, null, accessControl);
    }

    public ResultService(ElectionRepository electionRepository,
                         ResultRepository resultRepository,
                         PartyRepository partyRepository,
                         AccessControlService accessControl) {
        this.electionRepository = electionRepository;
        this.resultRepository = resultRepository;
        this.partyRepository = partyRepository;
        this.accessControl = accessControl;
    }

    public List<Election> getViewableResultElections() {
        UserSession session = accessControl.requireAuthenticated();
        if (session.getRole() == RoleType.VOTER) {
            return electionRepository.findPublishedElections();
        }
        return electionRepository.findAll(null, null).stream()
                .filter(e -> e.getStatus() == ElectionStatus.CLOSED || e.getStatus() == ElectionStatus.PUBLISHED)
                .toList();
    }

    /**
     * Builds full result summary using database aggregation queries.
     * Voters may ONLY view PUBLISHED elections; interim/unpublished results are blocked.
     */
    public ElectionResultSummary getElectionResults(long electionId) {
        UserSession session = accessControl.requireAuthenticated();
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));

        if (session.getRole() == RoleType.VOTER && election.getStatus() != ElectionStatus.PUBLISHED) {
            throw new AuthorizationException("Election results are not yet published and cannot be viewed by voters.");
        }
        if (session.getRole().isAdmin()
                && election.getStatus() != ElectionStatus.CLOSED
                && election.getStatus() != ElectionStatus.PUBLISHED) {
            throw new AuthorizationException(
                    "Results can only be tallied after the election is CLOSED or PUBLISHED (current status: " + election.getStatus() + ").");
        }

        long eligibleVoters = resultRepository.countEligibleVoters(electionId);
        long totalParticipants = resultRepository.countTotalParticipants(electionId);
        long totalBallots = resultRepository.countTotalBallots(electionId);

        List<CandidateResult> candidateResults = resultRepository.aggregateCandidateResults(electionId);
        List<ConstituencyResult> constituencyResults = resultRepository.aggregateConstituencyStats(electionId);

        // Group candidates by constituency to compute accurate constituency-wise vote shares
        Map<Long, List<CandidateResult>> byConstituency = candidateResults.stream()
                .collect(Collectors.groupingBy(CandidateResult::getConstituencyId, LinkedHashMap::new, Collectors.toList()));

        for (ConstituencyResult cr : constituencyResults) {
            List<CandidateResult> list = byConstituency.getOrDefault(cr.getConstituencyId(), List.of());
            long constBallots = cr.getTotalBallots();
            for (CandidateResult cand : list) {
                double pct = constBallots > 0 ? roundTwoDecimals((cand.getVoteCount() * 100.0) / constBallots) : 0.0;
                cand.setPercentage(pct);
            }
            cr.setCandidateResults(list);
            cr.setPartyResults(calculatePartyResults(list, constBallots));
            cr.setTurnoutPercentage(roundTwoDecimals(cr.getTurnoutPercentage()));
            if (!list.isEmpty() && list.get(0).getVoteCount() > 0) {
                CandidateResult top = list.get(0);
                boolean tied = list.size() > 1 && list.get(1).getVoteCount() == top.getVoteCount();
                cr.setLeadingCandidateName(tied ? "Tied (" + top.getVoteCount() + " votes)" : top.getCandidateName() + " (" + top.getPartyCode() + ")");
            } else {
                cr.setLeadingCandidateName("No ballots cast");
            }
        }

        // If any candidate belongs to a constituency not already updated above, compute overall share safely
        for (CandidateResult cand : candidateResults) {
            if (cand.getPercentage() == 0.0 && cand.getVoteCount() > 0 && totalBallots > 0) {
                cand.setPercentage(roundTwoDecimals((cand.getVoteCount() * 100.0) / totalBallots));
            }
        }

        List<PartyResult> overallPartyResults = calculatePartyResults(candidateResults, totalBallots);
        String leadingPartySummary = determineLeadingPartySummary(overallPartyResults);

        ElectionResultSummary summary = new ElectionResultSummary();
        summary.setElection(election);
        summary.setTotalEligibleVoters(eligibleVoters);
        summary.setTotalParticipants(totalParticipants);
        summary.setTotalBallotsCast(totalBallots);
        summary.setTurnoutPercentage(eligibleVoters > 0 ? roundTwoDecimals((totalParticipants * 100.0) / eligibleVoters) : 0.0);
        summary.setPublished(election.getStatus() == ElectionStatus.PUBLISHED);
        summary.setCandidateResults(candidateResults);
        summary.setConstituencyResults(constituencyResults);
        summary.setPartyResults(overallPartyResults);
        summary.setLeadingPartySummary(leadingPartySummary);
        return summary;
    }

    /**
     * Aggregates party totals from candidate results, calculates percentage share, sorts descending by vote count,
     * and flags leading or tied parties without hardcoding outcomes.
     */
    public List<PartyResult> calculatePartyResults(List<CandidateResult> candidates, long totalBallots) {
        Map<String, List<CandidateResult>> byParty = new LinkedHashMap<>();
        if (candidates != null && !candidates.isEmpty()) {
            byParty = candidates.stream()
                    .collect(Collectors.groupingBy(
                            c -> (c.getPartyCode() != null && !c.getPartyCode().isBlank()) ? c.getPartyCode() : "IND",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }

        List<PartyResult> list = new ArrayList<>();
        for (Map.Entry<String, List<CandidateResult>> entry : byParty.entrySet()) {
            String code = entry.getKey();
            List<CandidateResult> cands = entry.getValue();
            String name = cands.get(0).getPartyName() != null ? cands.get(0).getPartyName() : code;
            long votes = cands.stream().mapToLong(CandidateResult::getVoteCount).sum();
            double pct = totalBallots > 0 ? roundTwoDecimals((votes * 100.0) / totalBallots) : 0.0;

            PartyResult pr = new PartyResult();
            pr.setPartyCode(code);
            pr.setPartyName(name);
            pr.setCandidateCount(cands.size());
            pr.setTotalVotes(votes);
            pr.setVotePercentage(pct);
            list.add(pr);
        }

        if (partyRepository != null) {
            try {
                List<Party> allActive = partyRepository.findAll(null, PartyStatus.ACTIVE);
                for (Party p : allActive) {
                    if (!byParty.containsKey(p.getPartyCode())) {
                        PartyResult pr = new PartyResult();
                        pr.setPartyId(p.getPartyId());
                        pr.setPartyCode(p.getPartyCode());
                        pr.setPartyName(p.getPartyName());
                        pr.setCandidateCount(0);
                        pr.setTotalVotes(0);
                        pr.setVotePercentage(0.0);
                        list.add(pr);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to retrieve active parties for result aggregation: {}", e.getMessage());
            }
        }

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        list.sort(Comparator.comparingLong(PartyResult::getTotalVotes).reversed()
                .thenComparing(PartyResult::getPartyName, String.CASE_INSENSITIVE_ORDER));

        if (!list.isEmpty()) {
            long topVotes = list.get(0).getTotalVotes();
            if (topVotes > 0) {
                List<PartyResult> topTied = list.stream()
                        .filter(p -> p.getTotalVotes() == topVotes)
                        .toList();
                if (topTied.size() > 1) {
                    for (PartyResult p : topTied) {
                        p.setLeading(true);
                        p.setTiedForLead(true);
                    }
                } else {
                    topTied.get(0).setLeading(true);
                }
            }
        }

        return list;
    }

    private String determineLeadingPartySummary(List<PartyResult> parties) {
        if (parties == null || parties.isEmpty()) {
            return "No parties fielded";
        }
        if (parties.get(0).getTotalVotes() == 0) {
            return "No ballots cast yet";
        }
        long topVotes = parties.get(0).getTotalVotes();
        List<PartyResult> leaders = parties.stream()
                .filter(p -> p.getTotalVotes() == topVotes)
                .toList();
        if (leaders.size() > 1) {
            String tied = leaders.stream()
                    .map(p -> p.getPartyName() + " (" + p.getTotalVotes() + " votes)")
                    .collect(Collectors.joining(", "));
            return "Tied: " + tied;
        }
        PartyResult leader = leaders.get(0);
        return leader.getPartyName() + " (" + leader.getTotalVotes() + " votes, " + leader.getVotePercentage() + "%)";
    }

    private static double roundTwoDecimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
