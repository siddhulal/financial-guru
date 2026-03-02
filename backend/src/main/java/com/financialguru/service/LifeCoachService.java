package com.financialguru.service;

import com.financialguru.dto.response.LifeGuidanceResponse;
import com.financialguru.model.Account;
import com.financialguru.model.LifeGuidance;
import com.financialguru.model.LifeProfile;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.LifeGuidanceRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LifeCoachService {

    private final LifeProfileService lifeProfileService;
    private final LifeGuidanceRepository lifeGuidanceRepository;
    private final GeminiService geminiService;
    private final OllamaService ollamaService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public LifeGuidanceResponse getAllGuidance() {
        List<LifeGuidance> all = lifeGuidanceRepository.findByIsDismissedFalseOrderByGeneratedAtDesc();

        LifeGuidance thisMonth = all.stream()
            .filter(g -> "MONTHLY".equals(g.getGuidanceType()))
            .findFirst()
            .orElse(null);

        List<LifeGuidance> careerItems = all.stream()
            .filter(g -> "CAREER".equals(g.getGuidanceType()))
            .limit(5)
            .toList();

        List<LifeGuidance> history = lifeGuidanceRepository
            .findByGuidanceTypeOrderByGeneratedAtDesc("MONTHLY", PageRequest.of(0, 6));

        return LifeGuidanceResponse.builder()
            .thisMonth(thisMonth)
            .careerItems(careerItems)
            .history(history)
            .geminiAvailable(geminiService.isConfigured())
            .build();
    }

    @Transactional
    public LifeGuidance generateGuidance() {
        LifeProfile profile = lifeProfileService.getOrCreateProfile();
        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        // Compute privacy-safe financial metrics (% only, no raw $ amounts)
        FinancialMetrics metrics = computeFinancialMetrics();

        String prompt = buildLifeCoachPrompt(profile, metrics, month);

        String content;
        String source;
        try {
            if (geminiService.isConfigured()) {
                content = geminiService.chat(prompt);
                source = "GEMINI";
            } else {
                content = ollamaService.chat(prompt);
                source = "OLLAMA";
            }
        } catch (Exception e) {
            log.warn("AI life coach failed, using rule-based: {}", e.getMessage());
            content = buildFallbackGuidance(profile, metrics, month);
            source = "RULE";
        }

        String actionItems = extractActionItems(content);

        LifeGuidance guidance = LifeGuidance.builder()
            .guidanceType("MONTHLY")
            .title("Life Coach Guidance — " + month)
            .content(content)
            .actionItems(actionItems)
            .source(source)
            .isDismissed(false)
            .build();

        return lifeGuidanceRepository.save(guidance);
    }

    @Transactional
    public void dismissGuidance(UUID id) {
        lifeGuidanceRepository.findById(id).ifPresent(g -> {
            g.setIsDismissed(true);
            lifeGuidanceRepository.save(g);
        });
    }

    private String buildLifeCoachPrompt(LifeProfile p, FinancialMetrics m, String month) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a holistic life coach helping someone make smart life decisions in ").append(month).append(".\n\n");

        sb.append("=== CAREER & LIFE CONTEXT ===\n");
        if (p.getFirstName() != null) sb.append("Name: ").append(p.getFirstName()).append("\n");
        if (p.getBirthYear() != null) sb.append("Age: ").append(2026 - p.getBirthYear()).append("\n");
        if (p.getJobTitle() != null) sb.append("Role: ").append(p.getJobTitle()).append("\n");
        if (p.getIndustry() != null) sb.append("Industry: ").append(p.getIndustry()).append("\n");
        if (p.getCity() != null && p.getState() != null) sb.append("Location: ").append(p.getCity()).append(", ").append(p.getState()).append("\n");
        if (p.getTotalYearsExperience() != null) sb.append("Experience: ").append(p.getTotalYearsExperience()).append(" years\n");
        if (p.getYearsAtCurrentJob() != null) sb.append("At current company: ").append(p.getYearsAtCurrentJob()).append(" years\n");
        if (p.getSkills() != null && !p.getSkills().isBlank()) sb.append("Skills: ").append(p.getSkills()).append("\n");

        sb.append("\n=== FAMILY CONTEXT ===\n");
        sb.append("Married: ").append(Boolean.TRUE.equals(p.getIsMarried()) ? "Yes" : "No").append("\n");
        if (Boolean.TRUE.equals(p.getSpouseEmployed())) {
            sb.append("Spouse: Employed").append(p.getSpouseJobTitle() != null ? " as " + p.getSpouseJobTitle() : "").append("\n");
        }
        sb.append("Kids: ").append(p.getNumberOfKids() != null ? p.getNumberOfKids() : 0).append("\n");
        if (p.getKidsAges() != null && !p.getKidsAges().isBlank()) sb.append("Kids ages: ").append(p.getKidsAges()).append("\n");

        // NOTE: Only percentages — no raw dollar amounts from bank accounts
        sb.append("\n=== FINANCIAL HEALTH (% metrics only) ===\n");
        sb.append("Savings rate: ").append(m.savingsRatePct).append("%\n");
        sb.append("Credit utilization: ").append(m.creditUtilizationPct).append("%\n");
        sb.append("Emergency fund: ").append(m.emergencyFundMonths).append(" months of coverage\n");
        sb.append("Debt load: ").append(m.debtStatusLabel).append("\n");

        sb.append("\n=== GUIDANCE REQUEST ===\n");
        sb.append("Please provide holistic life coaching for this month covering:\n");
        sb.append("1. Career: One specific action to advance their career this month\n");
        sb.append("2. Financial: One specific financial focus area (using the health metrics above)\n");
        sb.append("3. Family: One relevant family/relationship consideration\n");
        sb.append("4. Growth: One personal development recommendation\n");
        sb.append("5. Action items: List exactly 5 concrete action items prefixed with [ACTION: ...]\n\n");
        sb.append("Be specific, practical, and encouraging. No generic advice.");

        return sb.toString();
    }

    private String buildFallbackGuidance(LifeProfile p, FinancialMetrics m, String month) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Life Guidance — ").append(month).append("\n\n");

        sb.append("**Career**: ");
        if (p.getJobTitle() != null) {
            sb.append("As a ").append(p.getJobTitle()).append(", focus on one high-impact project or initiative this month that demonstrates leadership.\n\n");
        } else {
            sb.append("Update your resume and LinkedIn profile to reflect recent achievements.\n\n");
        }

        sb.append("**Financial**: ");
        if (m.savingsRatePct < 10) {
            sb.append("Your savings rate is below 10%. Identify one recurring expense to reduce this month.\n\n");
        } else if (m.savingsRatePct < 20) {
            sb.append("Your savings rate of ~").append(m.savingsRatePct).append("% is decent. Push toward 20% by automating transfers on payday.\n\n");
        } else {
            sb.append("Great savings rate! Consider maximizing tax-advantaged accounts (401k, IRA) before investing in taxable accounts.\n\n");
        }

        sb.append("**Family**: ");
        if (Boolean.TRUE.equals(p.getIsMarried()) && p.getNumberOfKids() != null && p.getNumberOfKids() > 0) {
            sb.append("With ").append(p.getNumberOfKids()).append(" kid(s), ensure you have a current will and beneficiary designations updated on all accounts.\n\n");
        } else if (Boolean.TRUE.equals(p.getIsMarried())) {
            sb.append("Schedule a monthly financial check-in with your spouse to align on goals and spending.\n\n");
        } else {
            sb.append("Invest in relationships and your support network — career success is more meaningful with strong connections.\n\n");
        }

        sb.append("**Growth**: Dedicate 30 minutes daily to learning a new skill relevant to your field.\n\n");

        sb.append("[ACTION: Review your monthly spending categories]\n");
        sb.append("[ACTION: Schedule one career networking conversation]\n");
        sb.append("[ACTION: Check and update beneficiary designations]\n");
        sb.append("[ACTION: Block 2 hours for a focused deep-work project]\n");
        sb.append("[ACTION: Set a specific savings target for this month]");

        return sb.toString();
    }

    private String extractActionItems(String content) {
        List<String> items = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[ACTION:\\s*([^\\]]+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            items.add(matcher.group(1).trim());
        }
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "'")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private FinancialMetrics computeFinancialMetrics() {
        // Compute % metrics only — never pass raw $ to external AI
        try {
            List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();

            // Credit utilization %
            BigDecimal totalBalance = BigDecimal.ZERO;
            BigDecimal totalLimit = BigDecimal.ZERO;
            for (Account a : accounts) {
                if (a.getType() == Account.AccountType.CREDIT_CARD) {
                    if (a.getCurrentBalance() != null) totalBalance = totalBalance.add(a.getCurrentBalance());
                    if (a.getCreditLimit() != null) totalLimit = totalLimit.add(a.getCreditLimit());
                }
            }
            int utilPct = totalLimit.compareTo(BigDecimal.ZERO) > 0
                ? totalBalance.divide(totalLimit, 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).intValue()
                : 0;

            // Rough savings rate from last 3 months (income vs spending, derived from transactions, as %)
            LocalDate since = LocalDate.now().minusMonths(3);
            BigDecimal totalSpend = transactionRepository.sumAllSpending(since, LocalDate.now());
            if (totalSpend == null) totalSpend = BigDecimal.ZERO;

            // Checking balance as proxy for cash on hand
            BigDecimal checkingBalance = accounts.stream()
                .filter(a -> a.getType() == Account.AccountType.CHECKING)
                .map(a -> a.getCurrentBalance() != null ? a.getCurrentBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Estimate emergency fund months (checking / avg monthly spend)
            BigDecimal avgMonthlySpend = totalSpend.divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP);
            int emFundMonths = avgMonthlySpend.compareTo(BigDecimal.ZERO) > 0
                ? checkingBalance.divide(avgMonthlySpend, 0, RoundingMode.HALF_UP).intValue()
                : 0;

            // Savings rate estimate (rough — % of spend that goes to savings)
            // Use 20% as default if no income data
            int savingsRate = 15; // default fallback

            String debtLabel = utilPct < 20 ? "Low" : utilPct < 50 ? "Moderate" : "High";

            return new FinancialMetrics(savingsRate, utilPct, Math.min(emFundMonths, 24), debtLabel);
        } catch (Exception e) {
            log.warn("Could not compute financial metrics: {}", e.getMessage());
            return new FinancialMetrics(0, 0, 0, "Unknown");
        }
    }

    record FinancialMetrics(int savingsRatePct, int creditUtilizationPct, int emergencyFundMonths, String debtStatusLabel) {}
}
