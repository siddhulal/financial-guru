package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrainReportResponse {

    // User profile
    private Integer age;
    private Integer targetRetirementAge;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlySpend;
    private BigDecimal monthlySavings;
    private double savingsRatePct;
    private BigDecimal netWorth;
    private BigDecimal currentInvestments;
    private boolean profileComplete;   // false if age is missing

    // Retirement analysis
    private BigDecimal fiNumber;                // target corpus (annual expenses x 25)
    private double projectedCorpusAtTargetAge;  // what you'll have at targetRetirementAge
    private int projectedRetirementAge;         // age when portfolio actually hits FI number
    private boolean onTrack;
    private int yearsEarlyOrLate;               // positive = early, negative = late
    private BigDecimal monthlyGapToTarget;      // extra/month needed to retire on time (0 if on track)
    private int retirementReadinessScore;       // 0-100
    private String retirementReadinessGrade;    // A/B/C/D/F
    private String trajectoryHeadline;          // e.g. "You'll retire at 71 -- 6 years late"

    // Year-by-year wealth projections (age 'current' to 85)
    private List<AgeProjection> currentPathProjections;
    private List<AgeProjection> optimalPathProjections;  // at 20% savings rate

    // What-If scenarios
    private List<ScenarioResult> scenarios;

    // Top spending categories with retirement impact
    private List<CategoryInsight> topCategories;

    // Ranked action roadmap
    private List<ActionItem> actionRoadmap;

    // AI narrative
    private String aiNarrative;
    private boolean aiAvailable;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class AgeProjection {
        private int age;
        private int year;
        private BigDecimal portfolioValue;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ScenarioResult {
        private String id;           // "save_200", "save_500", "cut_dining"
        private String description;  // "Save $200 more/month"
        private BigDecimal extraMonthly;
        private int projectedRetirementAge;
        private int yearsEarlier;    // vs current path
        private BigDecimal portfolioAtTargetAge;
        private String headline;     // "Retire at 63 -- 2 years earlier"
        private String color;        // "green", "gold", "blue"
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class CategoryInsight {
        private String category;
        private BigDecimal monthlyAvg;
        private double pctOfIncome;
        private double benchmarkPct;
        private String status;        // OVER / OK / GOOD
        private BigDecimal annualCost;
        private BigDecimal retirementImpact10yr; // FV if monthly overspend invested at 7% for 10yr
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ActionItem {
        private int rank;
        private String title;
        private String detail;
        private BigDecimal monthlyImpact;
        private int yearsEarlierRetirement;
        private String type;   // SPENDING / SAVING / INVESTING / DEBT
    }
}
