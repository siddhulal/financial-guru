package com.financialguru.dto.response;

import com.financialguru.model.LifeGuidance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LifeGuidanceResponse {

    private LifeGuidance thisMonth;
    private List<LifeGuidance> careerItems;
    private List<LifeGuidance> history;
    private boolean geminiAvailable;
}
