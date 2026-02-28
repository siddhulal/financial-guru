package com.financialguru.service;

import com.financialguru.dto.response.AlertResponse;
import com.financialguru.model.Account;
import com.financialguru.model.Alert;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;

    public List<AlertResponse> getAllUnresolvedAlerts() {
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc()
            .stream()
            .map(AlertResponse::from)
            .collect(Collectors.toList());
    }

    public List<AlertResponse> getUnreadAlerts() {
        return alertRepository.findByIsReadFalseAndIsResolvedFalseOrderByCreatedAtDesc()
            .stream()
            .map(AlertResponse::from)
            .collect(Collectors.toList());
    }

    public List<AlertResponse> getRecentAlerts() {
        return alertRepository.findRecentUnresolved()
            .stream()
            .map(AlertResponse::from)
            .collect(Collectors.toList());
    }

    public long getUnreadCount() {
        return alertRepository.countByIsReadFalseAndIsResolvedFalse();
    }

    @Transactional
    public AlertResponse markAsRead(UUID id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + id));
        alert.setIsRead(true);
        return AlertResponse.from(alertRepository.save(alert));
    }

    @Transactional
    public AlertResponse markAsResolved(UUID id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Alert not found: " + id));
        alert.setIsResolved(true);
        alert.setIsRead(true);
        alert.setResolvedAt(OffsetDateTime.now());
        return AlertResponse.from(alertRepository.save(alert));
    }

    @Transactional
    public void deleteAlert(UUID id) {
        alertRepository.deleteById(id);
    }

    @Transactional
    public Alert createAlert(Alert.AlertType type, Alert.AlertSeverity severity,
                              String title, String message, Account account,
                              Transaction transaction, String aiExplanation) {
        Alert alert = Alert.builder()
            .type(type)
            .severity(severity)
            .title(title)
            .message(message)
            .account(account)
            .transaction(transaction)
            .aiExplanation(aiExplanation)
            .build();
        return alertRepository.save(alert);
    }
}
