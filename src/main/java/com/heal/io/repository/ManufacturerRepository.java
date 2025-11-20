package com.heal.io.repository;

import com.heal.io.entity.Manufacturer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {
    Optional<Manufacturer> findByName(String name);
    List<Manufacturer> findByIsActiveTrue();
    
    @Query("SELECT m FROM Manufacturer m WHERE m.isActive = true AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "m.phone LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(m.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Manufacturer> searchManufacturers(@Param("search") String search, Pageable pageable);
    
    Page<Manufacturer> findByIsActiveTrue(Pageable pageable);
}

