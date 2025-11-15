package com.heal.io.repository;

import com.heal.io.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByProductId(Long productId);
    
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND " +
           "(:packageId IS NULL OR i.productPackage.id = :packageId) AND " +
           "(:batchNumber IS NULL OR i.batchNumber = :batchNumber) AND " +
           "(:expiryDate IS NULL OR i.expiryDate = :expiryDate)")
    Optional<Inventory> findByProductAndPackageAndBatchAndExpiry(
            @Param("productId") Long productId,
            @Param("packageId") Long packageId,
            @Param("batchNumber") String batchNumber,
            @Param("expiryDate") LocalDate expiryDate);
    
    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.availableQuantity <= i.reorderLevel AND i.isActive = true")
    Long countLowStockItems();
    
    @Query("SELECT i FROM Inventory i WHERE i.availableQuantity <= i.reorderLevel AND i.isActive = true")
    List<Inventory> findLowStockItems();
    
    @Query("SELECT i FROM Inventory i WHERE i.expiryDate <= :date AND i.expiryDate IS NOT NULL AND i.quantity > 0 AND i.isActive = true")
    List<Inventory> findExpiringItems(@Param("date") LocalDate date);
}

