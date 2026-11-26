package com.civicflow.repository;

import com.civicflow.model.Party;
import com.civicflow.model.PartyStatus;

import java.util.List;
import java.util.Optional;

public interface PartyRepository {
    List<Party> findAll(String search, PartyStatus statusFilter);
    Optional<Party> findById(long partyId);
    Optional<Party> findByCode(String code);
    Optional<Party> findByName(String name);
    long insert(Party party);
    void update(Party party);
    boolean hasRelatedCandidates(long partyId);
    void delete(long partyId);
}
