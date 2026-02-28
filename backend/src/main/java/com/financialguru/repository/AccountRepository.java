package com.financialguru.repository;

import com.financialguru.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByIsActiveTrueOrderByCreatedAtDesc();

    List<Account> findByInstitution(String institution);

    java.util.Optional<Account> findByInstitutionAndLast4(String institution, String last4);

    List<Account> findByTypeOrderByNameAsc(Account.AccountType type);

    @Query("SELECT a FROM Account a WHERE a.isActive = true AND a.promoAprEndDate IS NOT NULL ORDER BY a.promoAprEndDate ASC")
    List<Account> findAccountsWithPromoAprExpiring();

    @Query("SELECT a FROM Account a WHERE a.isActive = true AND a.paymentDueDay IS NOT NULL ORDER BY a.paymentDueDay ASC")
    List<Account> findAccountsWithPaymentDueDays();
}
