package com.financialguru.service;

import com.financialguru.dto.request.LifeProfileRequest;
import com.financialguru.dto.response.LifeProfileResponse;
import com.financialguru.model.LifeProfile;
import com.financialguru.repository.LifeProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;

@Service
@RequiredArgsConstructor
@Slf4j
public class LifeProfileService {

    private final LifeProfileRepository lifeProfileRepository;

    public LifeProfile getOrCreateProfile() {
        return lifeProfileRepository.findFirst()
            .orElseGet(() -> {
                LifeProfile p = LifeProfile.builder()
                    .country("US")
                    .employmentType("FULL_TIME")
                    .isMarried(false)
                    .spouseEmployed(false)
                    .numberOfKids(0)
                    .build();
                return lifeProfileRepository.save(p);
            });
    }

    @Transactional
    public LifeProfileResponse updateProfile(LifeProfileRequest req) {
        LifeProfile p = getOrCreateProfile();
        if (req.getFirstName() != null) p.setFirstName(req.getFirstName());
        if (req.getBirthYear() != null) p.setBirthYear(req.getBirthYear());
        if (req.getCity() != null) p.setCity(req.getCity());
        if (req.getState() != null) p.setState(req.getState());
        if (req.getCountry() != null) p.setCountry(req.getCountry());
        if (req.getJobTitle() != null) p.setJobTitle(req.getJobTitle());
        if (req.getCompany() != null) p.setCompany(req.getCompany());
        if (req.getIndustry() != null) p.setIndustry(req.getIndustry());
        if (req.getEmploymentType() != null) p.setEmploymentType(req.getEmploymentType());
        if (req.getYearsAtCurrentJob() != null) p.setYearsAtCurrentJob(req.getYearsAtCurrentJob());
        if (req.getTotalYearsExperience() != null) p.setTotalYearsExperience(req.getTotalYearsExperience());
        if (req.getAnnualSalary() != null) p.setAnnualSalary(req.getAnnualSalary());
        if (req.getAnnualBonus() != null) p.setAnnualBonus(req.getAnnualBonus());
        if (req.getEquityAnnualValue() != null) p.setEquityAnnualValue(req.getEquityAnnualValue());
        if (req.getSkills() != null) p.setSkills(req.getSkills());
        if (req.getIsMarried() != null) p.setIsMarried(req.getIsMarried());
        if (req.getSpouseEmployed() != null) p.setSpouseEmployed(req.getSpouseEmployed());
        if (req.getSpouseJobTitle() != null) p.setSpouseJobTitle(req.getSpouseJobTitle());
        if (req.getSpouseAnnualIncome() != null) p.setSpouseAnnualIncome(req.getSpouseAnnualIncome());
        if (req.getNumberOfKids() != null) p.setNumberOfKids(req.getNumberOfKids());
        if (req.getKidsAges() != null) p.setKidsAges(req.getKidsAges());
        if (req.getNotes() != null) p.setNotes(req.getNotes());
        return toResponse(lifeProfileRepository.save(p));
    }

    public LifeProfileResponse getProfile() {
        return toResponse(getOrCreateProfile());
    }

    public LifeProfileResponse toResponse(LifeProfile p) {
        int currentYear = Year.now().getValue();
        Integer age = p.getBirthYear() != null ? currentYear - p.getBirthYear() : null;

        BigDecimal household = BigDecimal.ZERO;
        if (p.getAnnualSalary() != null) household = household.add(p.getAnnualSalary());
        if (p.getAnnualBonus() != null) household = household.add(p.getAnnualBonus());
        if (Boolean.TRUE.equals(p.getSpouseEmployed()) && p.getSpouseAnnualIncome() != null) {
            household = household.add(p.getSpouseAnnualIncome());
        }

        return LifeProfileResponse.builder()
            .id(p.getId())
            .firstName(p.getFirstName())
            .birthYear(p.getBirthYear())
            .age(age)
            .city(p.getCity())
            .state(p.getState())
            .country(p.getCountry())
            .jobTitle(p.getJobTitle())
            .company(p.getCompany())
            .industry(p.getIndustry())
            .employmentType(p.getEmploymentType())
            .yearsAtCurrentJob(p.getYearsAtCurrentJob())
            .totalYearsExperience(p.getTotalYearsExperience())
            .annualSalary(p.getAnnualSalary())
            .annualBonus(p.getAnnualBonus())
            .equityAnnualValue(p.getEquityAnnualValue())
            .skills(p.getSkills())
            .isMarried(p.getIsMarried())
            .spouseEmployed(p.getSpouseEmployed())
            .spouseJobTitle(p.getSpouseJobTitle())
            .spouseAnnualIncome(p.getSpouseAnnualIncome())
            .numberOfKids(p.getNumberOfKids())
            .kidsAges(p.getKidsAges())
            .notes(p.getNotes())
            .householdAnnualIncome(household)
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }
}
