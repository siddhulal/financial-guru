package com.financialguru.controller;

import com.financialguru.dto.response.WeeklyDigestResponse;
import com.financialguru.service.WeeklyDigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/digest")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DigestController {

    private final WeeklyDigestService weeklyDigestService;

    @GetMapping
    public WeeklyDigestResponse getDigest() {
        return weeklyDigestService.getDigest();
    }
}
