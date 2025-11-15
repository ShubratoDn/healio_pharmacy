package com.heal.io.repository;

import com.heal.io.entity.MedicineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicineTypeRepository extends JpaRepository<MedicineType, Long> {
    Optional<MedicineType> findByName(String name);
}

