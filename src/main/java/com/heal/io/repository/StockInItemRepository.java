package com.heal.io.repository;

import com.heal.io.entity.StockInItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface StockInItemRepository extends JpaRepository<StockInItem, Long> {
    
    @Query("SELECT si FROM StockInItem si " +
           "WHERE si.product.id = :productId " +
           "AND (:packageId IS NULL OR si.productPackage.id = :packageId) " +
           "ORDER BY si.stockIn.stockInDate DESC, si.createdAt DESC")
    java.util.List<StockInItem> findByProductAndPackageOrderByDateDesc(
            @Param("productId") Long productId,
            @Param("packageId") Long packageId);
    
    @Query("SELECT si.unitCost FROM StockInItem si " +
           "WHERE si.product.id = :productId " +
           "AND (:packageId IS NULL OR si.productPackage.id = :packageId) " +
           "ORDER BY si.stockIn.stockInDate DESC, si.createdAt DESC")
    java.util.List<BigDecimal> findUnitCostsByProductAndPackageOrderByDateDesc(
            @Param("productId") Long productId,
            @Param("packageId") Long packageId);
}

