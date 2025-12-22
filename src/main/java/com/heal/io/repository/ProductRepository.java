package com.heal.io.repository;

import com.heal.io.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBrandId(Long brandId);

    Optional<Product> findBySlug(String slug);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByProductCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "LEFT JOIN p.manufacturer m " +
            "LEFT JOIN p.generic g " +
            "LEFT JOIN p.dosageForm df " +
            "WHERE p.isActive = true AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(df.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY " +
            "CASE " +
            "  WHEN LOWER(p.name) LIKE LOWER(CONCAT(:search, '%')) THEN 1 " +
            "  WHEN LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) THEN 2 " +
            "  ELSE 3 " +
            "END ASC, p.name ASC")
    Page<Product> searchProducts(@Param("search") String search, Pageable pageable);

    long countByIsActiveTrue();
}
