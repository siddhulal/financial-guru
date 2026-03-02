package com.financialguru.repository;

import com.financialguru.model.LifeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LifeProfileRepository extends JpaRepository<LifeProfile, UUID> {

    @Query("SELECT p FROM LifeProfile p ORDER BY p.createdAt ASC")
    Optional<LifeProfile> findFirst();
}
