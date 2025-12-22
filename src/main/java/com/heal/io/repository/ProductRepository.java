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

    @Query(value = "SELECT p.* FROM product p " +
            "LEFT JOIN manufacturer m ON p.manufacturer_id = m.id " +
            "LEFT JOIN generic g ON p.generic_id = g.id " +
            "LEFT JOIN dosage_form df ON p.dosage_form_id = df.id " +
            "WHERE p.is_active = true AND (" +
            "  LOWER(CONCAT(p.name, ' ', COALESCE(p.strength, ''), ' ', COALESCE(df.name, ''), ' ', COALESCE(m.name, ''), ' ', COALESCE(g.name, ''))) "
            +
            "  LIKE ALL (SELECT '%' || LOWER(x) || '%' FROM unnest(regexp_split_to_array(trim(:search), '\\s+')) x)" +
            ") " +
            "ORDER BY " +
            "CASE " +
            "  WHEN LOWER(p.name) LIKE LOWER(CONCAT(split_part(trim(:search), ' ', 1), '%')) THEN 1 " +
            "  WHEN LOWER(p.name) LIKE LOWER(CONCAT('%', split_part(trim(:search), ' ', 1), '%')) THEN 2 " +
            "  ELSE 3 " +
            "END ASC, p.name ASC", countQuery = "SELECT count(*) FROM product p " +
                    "LEFT JOIN manufacturer m ON p.manufacturer_id = m.id " +
                    "LEFT JOIN generic g ON p.generic_id = g.id " +
                    "LEFT JOIN dosage_form df ON p.dosage_form_id = df.id " +
                    "WHERE p.is_active = true AND (" +
                    "  LOWER(CONCAT(p.name, ' ', COALESCE(p.strength, ''), ' ', COALESCE(df.name, ''), ' ', COALESCE(m.name, ''), ' ', COALESCE(g.name, ''))) "
                    +
                    "  LIKE ALL (SELECT '%' || LOWER(x) || '%' FROM unnest(regexp_split_to_array(trim(:search), '\\s+')) x)"
                    +
                    ")", nativeQuery = true)
    Page<Product> searchProducts(@Param("search") String search, Pageable pageable);

    long countByIsActiveTrue();
}
