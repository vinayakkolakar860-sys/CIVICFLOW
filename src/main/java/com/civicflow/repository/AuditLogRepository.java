package com.civicflow.repository;

import com.civicflow.model.AuditLog;
import com.civicflow.model.PageResult;

import java.time.LocalDate;
import java.util.List;

public interface AuditLogRepository {
    void insert(AuditLog log);
    PageResult<AuditLog> search(String module, String searchText, LocalDate fromDate, LocalDate toDate, int pageNumber, int pageSize);
    List<AuditLog> findAllFiltered(String module, String searchText, LocalDate fromDate, LocalDate toDate, int limit);
    List<AuditLog> findRecent(int limit);
}
