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
import java.util.List;
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
    
    @Query(value = "SELECT COALESCE(SUM(si.profit_amount), 0) FROM sale s " +
           "INNER JOIN sale_item si ON s.id = si.sale_id " +
           "WHERE DATE(s.sale_date) = CURRENT_DATE AND s.sale_status = 'COMPLETED'", nativeQuery = true)
    Double getTodayTotalProfit();
    
    @Query(value = "SELECT COALESCE(SUM(s.discount_amount), 0) FROM sale s " +
           "WHERE DATE(s.sale_date) = CURRENT_DATE AND s.sale_status = 'COMPLETED'", nativeQuery = true)
    Double getTodayTotalDiscount();
    
    @Query("SELECT s FROM Sale s WHERE s.saleDate >= :startDate AND s.saleDate < :endDate")
    Page<Sale> findBySaleDateBetween(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate, 
                                      Pageable pageable);
    
    Page<Sale> findAllByOrderBySaleDateDesc(Pageable pageable);
    
    @Query(value = "SELECT DATE(s.sale_date) as saleDate, COALESCE(SUM(s.total_amount), 0) as totalAmount " +
           "FROM sale s WHERE s.sale_date >= :startDate AND s.sale_date < :endDate AND s.sale_status = 'COMPLETED' " +
           "GROUP BY DATE(s.sale_date) ORDER BY DATE(s.sale_date)", nativeQuery = true)
    List<Object[]> getSalesByDateRange(@Param("startDate") java.time.LocalDate startDate, 
                                       @Param("endDate") java.time.LocalDate endDate);
    
    @Query(value = "SELECT p.name as productName, COALESCE(SUM(si.quantity), 0) as totalQuantity, " +
           "COALESCE(SUM(si.total_price), 0) as totalRevenue " +
           "FROM sale s " +
           "INNER JOIN sale_item si ON s.id = si.sale_id " +
           "INNER JOIN product p ON si.product_id = p.id " +
           "WHERE s.sale_date >= :startDate AND s.sale_date < :endDate AND s.sale_status = 'COMPLETED' " +
           "GROUP BY p.id, p.name " +
           "ORDER BY totalQuantity DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getTopProductsByQuantity(@Param("startDate") java.time.LocalDate startDate, 
                                            @Param("endDate") java.time.LocalDate endDate,
                                            @Param("limit") int limit);
    
    @Query(value = "SELECT COALESCE(SUM(si.profit_amount), 0) as totalProfit, " +
           "COALESCE(SUM(s.discount_amount), 0) as totalDiscount " +
           "FROM sale s " +
           "LEFT JOIN sale_item si ON s.id = si.sale_id " +
           "WHERE s.sale_date >= :startDate AND s.sale_date < :endDate AND s.sale_status = 'COMPLETED'", nativeQuery = true)
    Object[] getProfitAndDiscountByDateRange(@Param("startDate") java.time.LocalDate startDate, 
                                              @Param("endDate") java.time.LocalDate endDate);
}

