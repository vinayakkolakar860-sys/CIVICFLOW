package com.civicflow.repository;

import com.civicflow.model.Constituency;
import com.civicflow.model.ConstituencyStatus;

import java.util.List;
import java.util.Optional;

public interface ConstituencyRepository {
    List<Constituency> findAll(String search, ConstituencyStatus statusFilter);
    Optional<Constituency> findById(long constituencyId);
    Optional<Constituency> findByCode(String code);
    long insert(Constituency constituency);
    void update(Constituency constituency);
    boolean hasRelatedRecords(long constituencyId);
    void delete(long constituencyId);
    long countAll();
}
