package com.financialguru.controller;

import com.financialguru.dto.response.LifeGuidanceResponse;
import com.financialguru.model.LifeGuidance;
import com.financialguru.service.LifeCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/life-coach")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LifeCoachController {

    private final LifeCoachService lifeCoachService;

    @GetMapping
    public LifeGuidanceResponse getAllGuidance() {
        return lifeCoachService.getAllGuidance();
    }

    @PostMapping("/generate")
    public LifeGuidance generateGuidance() {
        return lifeCoachService.generateGuidance();
    }

    @PutMapping("/{id}/dismiss")
    public void dismissGuidance(@PathVariable UUID id) {
        lifeCoachService.dismissGuidance(id);
    }
}
