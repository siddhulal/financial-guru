package com.financialguru.controller;

import com.financialguru.dto.response.CareerAdviceResponse;
import com.financialguru.service.CareerAdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CareerController {

    private final CareerAdvisorService careerAdvisorService;

    @GetMapping("/advice")
    public CareerAdviceResponse getCareerAdvice() {
        return careerAdvisorService.getCareerAdvice();
    }

    @PostMapping("/refresh")
    public CareerAdviceResponse refreshCareerAdvice() {
        return careerAdvisorService.refreshCareerAdvice();
    }
}
