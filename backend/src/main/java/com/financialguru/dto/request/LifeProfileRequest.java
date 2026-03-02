package com.financialguru.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LifeProfileRequest {

    private String firstName;
    private Integer birthYear;
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
}
