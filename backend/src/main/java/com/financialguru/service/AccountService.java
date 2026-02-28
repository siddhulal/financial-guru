package com.financialguru.service;

import com.financialguru.dto.request.AccountRequest;
import com.financialguru.dto.response.AccountResponse;
import com.financialguru.model.Account;
import com.financialguru.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findByIsActiveTrueOrderByCreatedAtDesc()
            .stream()
            .map(AccountResponse::from)
            .collect(Collectors.toList());
    }

    public AccountResponse getAccount(UUID id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        Account account = Account.builder()
            .name(request.getName())
            .institution(request.getInstitution())
            .type(request.getType())
            .last4(request.getLast4())
            .creditLimit(request.getCreditLimit())
            .currentBalance(request.getCurrentBalance())
            .availableCredit(request.getAvailableCredit())
            .apr(request.getApr())
            .promoApr(request.getPromoApr())
            .promoAprEndDate(request.getPromoAprEndDate())
            .paymentDueDay(request.getPaymentDueDay())
            .minPayment(request.getMinPayment())
            .rewardsProgram(request.getRewardsProgram())
            .color(request.getColor())
            .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse updateAccount(UUID id, AccountRequest request) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found: " + id));

        // Partial update — only overwrite fields that are explicitly provided
        if (request.getName() != null)           account.setName(request.getName());
        if (request.getInstitution() != null)    account.setInstitution(request.getInstitution());
        if (request.getType() != null)           account.setType(request.getType());
        if (request.getLast4() != null)          account.setLast4(request.getLast4());
        if (request.getCreditLimit() != null)    account.setCreditLimit(request.getCreditLimit());
        if (request.getCurrentBalance() != null) account.setCurrentBalance(request.getCurrentBalance());
        if (request.getAvailableCredit() != null)account.setAvailableCredit(request.getAvailableCredit());
        if (request.getApr() != null)            account.setApr(request.getApr());
        if (request.getPromoApr() != null)       account.setPromoApr(request.getPromoApr());
        if (request.getPromoAprEndDate() != null)account.setPromoAprEndDate(request.getPromoAprEndDate());
        if (request.getPaymentDueDay() != null)  account.setPaymentDueDay(request.getPaymentDueDay());
        if (request.getMinPayment() != null)     account.setMinPayment(request.getMinPayment());
        if (request.getRewardsProgram() != null) account.setRewardsProgram(request.getRewardsProgram());
        if (request.getColor() != null)          account.setColor(request.getColor());

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public void deactivateAccount(UUID id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        account.setIsActive(false);
        accountRepository.save(account);
    }

    public List<Account> getAccountsWithPromoAprExpiring() {
        return accountRepository.findAccountsWithPromoAprExpiring();
    }

    public List<Account> getAccountsWithPaymentDueDays() {
        return accountRepository.findAccountsWithPaymentDueDays();
    }
}
