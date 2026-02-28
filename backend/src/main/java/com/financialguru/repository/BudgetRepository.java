package com.financialguru.repository;

import com.financialguru.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByCategory(String category);

    List<Budget> findByIsActiveTrueOrderByCategoryAsc();
}
