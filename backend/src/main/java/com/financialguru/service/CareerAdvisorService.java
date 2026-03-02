package com.financialguru.service;

import com.financialguru.dto.response.CareerAdviceResponse;
import com.financialguru.model.LifeGuidance;
import com.financialguru.model.LifeProfile;
import com.financialguru.repository.LifeGuidanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerAdvisorService {

    private final LifeProfileService lifeProfileService;
    private final LifeGuidanceRepository lifeGuidanceRepository;
    private final GeminiService geminiService;
    private final OllamaService ollamaService;

    public CareerAdviceResponse getCareerAdvice() {
        LifeProfile profile = lifeProfileService.getOrCreateProfile();

        if (profile.getJobTitle() == null || profile.getJobTitle().isBlank()) {
            return CareerAdviceResponse.builder()
                .profileComplete(false)
                .profileMessage("Complete your Life Profile with job title, industry, location, and salary to get career advice.")
                .source("NONE")
                .build();
        }

        // Estimate market salary ranges based on YoE and industry
        int yoe = profile.getTotalYearsExperience() != null ? profile.getTotalYearsExperience() : 3;
        SalaryBenchmark benchmark = estimateSalaryBenchmark(profile.getJobTitle(), profile.getIndustry(), yoe);

        BigDecimal currentSalary = profile.getAnnualSalary() != null ? profile.getAnnualSalary() : BigDecimal.ZERO;
        BigDecimal gap = benchmark.p50.subtract(currentSalary);
        boolean underpaid = gap.compareTo(new BigDecimal("5000")) > 0;

        // Job change recommendation logic
        int yearsAtCurrent = profile.getYearsAtCurrentJob() != null ? profile.getYearsAtCurrentJob() : 0;
        String recommendation;
        String reasoning;
        if (yearsAtCurrent >= 2 && underpaid) {
            recommendation = "RECOMMENDED";
            reasoning = "You've been at your current company for " + yearsAtCurrent +
                " years and your salary is ~$" + gap.abs().setScale(0, RoundingMode.HALF_UP).toPlainString() +
                " below market median. Switching jobs typically yields 10-20% salary increases vs 3-5% internal raises.";
        } else if (yearsAtCurrent < 1) {
            recommendation = "NOT_NOW";
            reasoning = "You've been at your current job less than a year. Build credibility and relationships before exploring other options.";
        } else if (!underpaid) {
            recommendation = "NEUTRAL";
            reasoning = "Your compensation appears competitive with market rates. Focus on skill development and impact before making a move.";
        } else {
            recommendation = "NEUTRAL";
            reasoning = "Consider timing your job search strategically. Gather competing offers to either switch or negotiate internally.";
        }

        // Build career-only prompt (NEVER include financial account data)
        String aiSource;
        String narrative;
        List<String> skillsToLearn;
        String careerPath;

        try {
            String prompt = buildCareerPrompt(profile, benchmark, underpaid, recommendation);
            String rawResponse;
            if (geminiService.isConfigured()) {
                rawResponse = geminiService.chat(prompt);
                aiSource = "GEMINI";
            } else {
                rawResponse = ollamaService.chat(prompt);
                aiSource = "OLLAMA";
            }
            narrative = rawResponse.trim();
            skillsToLearn = extractSkillsFromNarrative(rawResponse, profile.getSkills(), profile.getIndustry());
            careerPath = extractCareerPath(rawResponse);
        } catch (Exception e) {
            log.warn("AI career advice failed, using rule-based fallback: {}", e.getMessage());
            aiSource = "RULE";
            narrative = buildFallbackNarrative(profile, benchmark, underpaid, recommendation);
            skillsToLearn = getDefaultSkills(profile.getIndustry());
            careerPath = buildDefaultCareerPath(profile);
        }

        // Compute percentile
        String percentile = computePercentileLabel(currentSalary, benchmark);

        return CareerAdviceResponse.builder()
            .jobTitle(profile.getJobTitle())
            .industry(profile.getIndustry() != null ? profile.getIndustry() : "Technology")
            .location(buildLocation(profile))
            .percentileLabel(percentile)
            .currentSalary(currentSalary)
            .marketP25(benchmark.p25)
            .marketP50(benchmark.p50)
            .marketP75(benchmark.p75)
            .salaryGap(gap)
            .isUnderpaid(underpaid)
            .jobChangeRecommendation(recommendation)
            .jobChangeReasoning(reasoning)
            .skillsToLearn(skillsToLearn)
            .careerPathAdvice(careerPath)
            .aiNarrative(narrative)
            .source(aiSource)
            .profileComplete(true)
            .build();
    }

    public CareerAdviceResponse refreshCareerAdvice() {
        CareerAdviceResponse advice = getCareerAdvice();
        if (advice.isProfileComplete()) {
            LifeGuidance guidance = LifeGuidance.builder()
                .guidanceType("CAREER")
                .title("Career Analysis: " + advice.getJobTitle())
                .content(advice.getAiNarrative())
                .actionItems(buildActionItemsJson(advice))
                .source(advice.getSource())
                .build();
            lifeGuidanceRepository.save(guidance);
        }
        return advice;
    }

    private String buildCareerPrompt(LifeProfile p, SalaryBenchmark benchmark, boolean underpaid, String recommendation) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a career advisor helping a professional make smart career decisions.\n\n");
        sb.append("Profile:\n");
        sb.append("- Role: ").append(p.getJobTitle()).append("\n");
        if (p.getIndustry() != null) sb.append("- Industry: ").append(p.getIndustry()).append("\n");
        if (p.getCity() != null) sb.append("- Location: ").append(p.getCity());
        if (p.getState() != null) sb.append(", ").append(p.getState());
        sb.append("\n");
        if (p.getTotalYearsExperience() != null) sb.append("- Total YoE: ").append(p.getTotalYearsExperience()).append(" years\n");
        if (p.getYearsAtCurrentJob() != null) sb.append("- Years at current company: ").append(p.getYearsAtCurrentJob()).append("\n");
        if (p.getSkills() != null && !p.getSkills().isBlank()) sb.append("- Current skills: ").append(p.getSkills()).append("\n");
        sb.append("\nMarket Salary Data (2026):\n");
        sb.append("- P25: $").append(benchmark.p25.toPlainString()).append("\n");
        sb.append("- P50 (median): $").append(benchmark.p50.toPlainString()).append("\n");
        sb.append("- P75: $").append(benchmark.p75.toPlainString()).append("\n");
        sb.append("- Current compensation: ").append(underpaid ? "Below market median" : "At or above market median").append("\n");
        sb.append("- Job change signal: ").append(recommendation).append("\n");
        if (p.getIsMarried() != null && p.getIsMarried()) sb.append("- Family: Married").append(p.getNumberOfKids() != null && p.getNumberOfKids() > 0 ? ", " + p.getNumberOfKids() + " kids" : "").append("\n");
        sb.append("\nProvide:\n");
        sb.append("1. 2-3 specific skills to learn in the next 12 months (label them with [SKILL: ...])\n");
        sb.append("2. Career path advice for 3-5 year horizon\n");
        sb.append("3. A 3-4 paragraph career narrative with actionable insights\n");
        sb.append("Keep tone professional, data-driven, and encouraging.");
        return sb.toString();
    }

    private String buildFallbackNarrative(LifeProfile p, SalaryBenchmark benchmark, boolean underpaid, String recommendation) {
        return String.format(
            "Based on your profile as a %s with %d years of experience, " +
            "the market shows compensation ranging from $%s (P25) to $%s (P75) with a median of $%s. " +
            "%s " +
            "Focus on building expertise in high-demand areas within your industry and maintain an active network. " +
            "Regularly benchmark your compensation every 12-18 months.",
            p.getJobTitle(),
            p.getTotalYearsExperience() != null ? p.getTotalYearsExperience() : 0,
            benchmark.p25.toPlainString(),
            benchmark.p75.toPlainString(),
            benchmark.p50.toPlainString(),
            underpaid ? "Your current salary is below market median — consider negotiating or exploring external opportunities." :
                "Your compensation is competitive with the market."
        );
    }

    private String buildDefaultCareerPath(LifeProfile p) {
        int yoe = p.getTotalYearsExperience() != null ? p.getTotalYearsExperience() : 3;
        if (yoe < 3) return "Focus on deepening technical expertise and taking on increasing ownership of projects.";
        if (yoe < 7) return "Transition toward leading projects and mentoring junior team members. Consider a senior or lead role.";
        return "Explore principal, staff, or management tracks depending on your career goals.";
    }

    private List<String> getDefaultSkills(String industry) {
        if (industry != null && industry.toLowerCase().contains("tech")) {
            return List.of("System Design", "Cloud Architecture (AWS/GCP)", "AI/ML fundamentals");
        }
        return List.of("Data Analysis", "Strategic Communication", "Project Management");
    }

    private List<String> extractSkillsFromNarrative(String narrative, String existingSkills, String industry) {
        // Extract [SKILL: ...] tags from AI response
        List<String> skills = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[SKILL:\\s*([^\\]]+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(narrative);
        while (matcher.find() && skills.size() < 5) {
            skills.add(matcher.group(1).trim());
        }
        if (skills.isEmpty()) {
            return getDefaultSkills(industry);
        }
        return skills;
    }

    private String extractCareerPath(String narrative) {
        // Return the last paragraph as career path advice
        String[] paragraphs = narrative.split("\n\n");
        if (paragraphs.length > 1) {
            return paragraphs[paragraphs.length - 1].trim();
        }
        return narrative.length() > 300 ? narrative.substring(narrative.length() - 300) : narrative;
    }

    private String computePercentileLabel(BigDecimal salary, SalaryBenchmark b) {
        if (salary.compareTo(BigDecimal.ZERO) == 0) return "Unknown";
        if (salary.compareTo(b.p25) < 0) return "Below P25";
        if (salary.compareTo(b.p50) < 0) {
            int pct = 25 + (int)(25.0 * salary.subtract(b.p25).doubleValue() / b.p50.subtract(b.p25).doubleValue());
            return "P" + pct;
        }
        if (salary.compareTo(b.p75) < 0) {
            int pct = 50 + (int)(25.0 * salary.subtract(b.p50).doubleValue() / b.p75.subtract(b.p50).doubleValue());
            return "P" + pct;
        }
        return "Above P75";
    }

    private String buildLocation(LifeProfile p) {
        if (p.getCity() != null && p.getState() != null) return p.getCity() + ", " + p.getState();
        if (p.getCity() != null) return p.getCity();
        if (p.getState() != null) return p.getState();
        return "United States";
    }

    private String buildActionItemsJson(CareerAdviceResponse advice) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < advice.getSkillsToLearn().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"Learn ").append(advice.getSkillsToLearn().get(i)).append("\"");
        }
        if (!advice.getSkillsToLearn().isEmpty()) sb.append(",");
        sb.append("\"").append(advice.getJobChangeRecommendation()).append(": ").append(advice.getJobChangeReasoning().substring(0, Math.min(80, advice.getJobChangeReasoning().length()))).append("\"");
        sb.append("]");
        return sb.toString();
    }

    // ---- Salary estimation engine ----

    private SalaryBenchmark estimateSalaryBenchmark(String jobTitle, String industry, int yoe) {
        // Experience multiplier
        double expMult = 1.0 + (yoe * 0.05); // 5% per year of experience
        expMult = Math.min(expMult, 2.5); // cap at 2.5x

        // Base salaries by role keyword
        double base = 100_000;
        String title = jobTitle.toLowerCase();
        if (title.contains("software") || title.contains("engineer") || title.contains("developer")) {
            base = 130_000;
            if (title.contains("senior") || title.contains("sr")) base = 160_000;
            if (title.contains("principal") || title.contains("staff")) base = 200_000;
        } else if (title.contains("data scientist") || title.contains("ml") || title.contains("machine learning")) {
            base = 145_000;
            if (title.contains("senior")) base = 175_000;
        } else if (title.contains("product manager") || title.contains("pm")) {
            base = 140_000;
            if (title.contains("senior") || title.contains("director")) base = 175_000;
        } else if (title.contains("designer") || title.contains("ux") || title.contains("ui")) {
            base = 110_000;
            if (title.contains("senior")) base = 140_000;
        } else if (title.contains("manager") || title.contains("director")) {
            base = 130_000;
            if (title.contains("director")) base = 160_000;
            if (title.contains("vp")) base = 200_000;
        } else if (title.contains("analyst")) {
            base = 85_000;
            if (title.contains("senior")) base = 110_000;
        } else if (title.contains("devops") || title.contains("sre") || title.contains("platform")) {
            base = 140_000;
            if (title.contains("senior")) base = 170_000;
        }

        // Experience-adjusted median
        double p50 = base * Math.min(expMult, 1.8); // smooth cap
        double p25 = p50 * 0.82;
        double p75 = p50 * 1.22;

        return new SalaryBenchmark(
            BigDecimal.valueOf(Math.round(p25 / 1000) * 1000L),
            BigDecimal.valueOf(Math.round(p50 / 1000) * 1000L),
            BigDecimal.valueOf(Math.round(p75 / 1000) * 1000L)
        );
    }

    record SalaryBenchmark(BigDecimal p25, BigDecimal p50, BigDecimal p75) {}
}
