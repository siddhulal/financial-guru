package com.financialguru.controller;

import com.financialguru.dto.request.LifeProfileRequest;
import com.financialguru.dto.response.LifeProfileResponse;
import com.financialguru.service.LifeProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/life-profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LifeProfileController {

    private final LifeProfileService lifeProfileService;

    @GetMapping
    public LifeProfileResponse getProfile() {
        return lifeProfileService.getProfile();
    }

    @PutMapping
    public LifeProfileResponse updateProfile(@RequestBody LifeProfileRequest req) {
        return lifeProfileService.updateProfile(req);
    }
}
