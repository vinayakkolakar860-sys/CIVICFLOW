package com.civicflow.repository;

import com.civicflow.model.AccountStatus;
import com.civicflow.model.PageResult;
import com.civicflow.model.VerificationStatus;
import com.civicflow.model.Voter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VoterRepository {
    PageResult<Voter> search(String query, VerificationStatus verificationStatus, AccountStatus accountStatus,
                             Long constituencyId, int pageNumber, int pageSize);
    List<Voter> findAll(String query, VerificationStatus verificationStatus, AccountStatus accountStatus, Long constituencyId);
    Optional<Voter> findById(long voterId);
    Optional<Voter> findByUserId(long userId);
    Optional<Voter> findByEmail(String email);
    Optional<Voter> findByCode(String voterCode);
    long insert(Connection conn, Voter voter) throws SQLException;
    void update(Voter voter);
    void updateVerificationStatus(long voterId, VerificationStatus status);
    void updateAccountStatus(Connection conn, long voterId, AccountStatus status) throws SQLException;
    long countAll();
    long countByVerificationStatus(VerificationStatus status);
    Map<String, Long> countByVerificationStatusGrouped();
    Map<String, Long> countByConstituency();
}
