package com.heal.io.repository;

import com.heal.io.entity.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findBySaleNumber(String saleNumber);
    
    @Query(value = "SELECT * FROM sale WHERE DATE(sale_date) = CURRENT_DATE", nativeQuery = true)
    Page<Sale> findTodaySales(Pageable pageable);
    
    @Query(value = "SELECT COALESCE(SUM(total_amount), 0) FROM sale WHERE DATE(sale_date) = CURRENT_DATE AND sale_status = 'COMPLETED'", nativeQuery = true)
    Double getTodayTotalSales();
    
    @Query(value = "SELECT COUNT(*) FROM sale WHERE DATE(sale_date) = CURRENT_DATE AND sale_status = 'COMPLETED'", nativeQuery = true)
    Long countTodaySales();
    
    @Query("SELECT s FROM Sale s WHERE s.saleDate >= :startDate AND s.saleDate < :endDate")
    Page<Sale> findBySaleDateBetween(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate, 
                                      Pageable pageable);
    
    Page<Sale> findAllByOrderBySaleDateDesc(Pageable pageable);
}

