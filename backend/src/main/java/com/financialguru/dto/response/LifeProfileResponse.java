package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifeProfileResponse {

    private UUID id;
    private String firstName;
    private Integer birthYear;
    private Integer age;  // computed: currentYear - birthYear
    private String city;
    private String state;
    private String country;
    private String jobTitle;
    private String company;
    private String industry;
    private String employmentType;
    private Integer yearsAtCurrentJob;
    private Integer totalYearsExperience;
    private BigDecimal annualSalary;
    private BigDecimal annualBonus;
    private BigDecimal equityAnnualValue;
    private String skills;
    private Boolean isMarried;
    private Boolean spouseEmployed;
    private String spouseJobTitle;
    private BigDecimal spouseAnnualIncome;
    private Integer numberOfKids;
    private String kidsAges;
    private String notes;
    private BigDecimal householdAnnualIncome;  // computed
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
