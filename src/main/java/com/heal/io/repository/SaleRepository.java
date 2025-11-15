package com.heal.io.repository;

import com.heal.io.entity.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findBySaleNumber(String saleNumber);
    
    @Query("SELECT s FROM Sale s WHERE DATE(s.saleDate) = CURRENT_DATE")
    Page<Sale> findTodaySales(Pageable pageable);
    
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE DATE(s.saleDate) = CURRENT_DATE AND s.saleStatus = 'COMPLETED'")
    Double getTodayTotalSales();
    
    @Query("SELECT COUNT(s) FROM Sale s WHERE DATE(s.saleDate) = CURRENT_DATE AND s.saleStatus = 'COMPLETED'")
    Long countTodaySales();
    
    Page<Sale> findBySaleDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<Sale> findAllByOrderBySaleDateDesc(Pageable pageable);
}

