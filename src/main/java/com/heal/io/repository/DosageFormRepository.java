package com.heal.io.repository;

import com.heal.io.entity.DosageForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DosageFormRepository extends JpaRepository<DosageForm, Long> {
    Optional<DosageForm> findByName(String name);
}

