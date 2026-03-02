package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "life_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LifeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "country", length = 50)
    @Builder.Default
    private String country = "US";

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "employment_type", length = 50)
    @Builder.Default
    private String employmentType = "FULL_TIME";

    @Column(name = "years_at_current_job")
    private Integer yearsAtCurrentJob;

    @Column(name = "total_years_experience")
    private Integer totalYearsExperience;

    @Column(name = "annual_salary", precision = 14, scale = 2)
    private BigDecimal annualSalary;

    @Column(name = "annual_bonus", precision = 14, scale = 2)
    private BigDecimal annualBonus;

    @Column(name = "equity_annual_value", precision = 14, scale = 2)
    private BigDecimal equityAnnualValue;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "is_married")
    @Builder.Default
    private Boolean isMarried = false;

    @Column(name = "spouse_employed")
    @Builder.Default
    private Boolean spouseEmployed = false;

    @Column(name = "spouse_job_title", length = 200)
    private String spouseJobTitle;

    @Column(name = "spouse_annual_income", precision = 14, scale = 2)
    private BigDecimal spouseAnnualIncome;

    @Column(name = "number_of_kids")
    @Builder.Default
    private Integer numberOfKids = 0;

    @Column(name = "kids_ages", columnDefinition = "TEXT")
    private String kidsAges;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
