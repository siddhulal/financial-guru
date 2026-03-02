package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "life_guidance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LifeGuidance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "guidance_type", length = 50, nullable = false)
    private String guidanceType;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "action_items", columnDefinition = "TEXT")
    private String actionItems;

    @Column(name = "source", length = 20)
    @Builder.Default
    private String source = "AI";

    @Column(name = "is_dismissed")
    @Builder.Default
    private Boolean isDismissed = false;

    @CreationTimestamp
    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;
}
