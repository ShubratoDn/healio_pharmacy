package com.heal.io.repository;

import com.heal.io.entity.StockIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockInRepository extends JpaRepository<StockIn, Long> {
       Optional<StockIn> findByStockInNumber(String stockInNumber);

       Page<StockIn> findAllByOrderByStockInDateDesc(Pageable pageable);

       @Query("SELECT s FROM StockIn s WHERE " +
                     "(:search IS NULL OR :search = '' OR " +
                     "LOWER(s.stockInNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(s.supplier.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "(s.supplier.companyName IS NOT NULL AND LOWER(s.supplier.companyName) LIKE LOWER(CONCAT('%', :search, '%'))) OR "
                     +
                     "LOWER(s.paymentStatus) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                     "ORDER BY s.stockInDate DESC")
       Page<StockIn> searchStockIns(@Param("search") String search, Pageable pageable);

       @Query("SELECT s FROM StockIn s WHERE " +
                     "s.stockInDate >= :startDate AND s.stockInDate <= :endDate " +
                     "ORDER BY s.stockInDate DESC")
       Page<StockIn> findByStockInDateBetween(@Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     Pageable pageable);

       @Query("SELECT s FROM StockIn s WHERE " +
                     "s.stockInDate >= :startDate AND s.stockInDate <= :endDate AND " +
                     "(:search IS NULL OR :search = '' OR " +
                     "LOWER(s.stockInNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "LOWER(s.supplier.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                     "(s.supplier.companyName IS NOT NULL AND LOWER(s.supplier.companyName) LIKE LOWER(CONCAT('%', :search, '%'))) OR "
                     +
                     "LOWER(s.paymentStatus) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                     "ORDER BY s.stockInDate DESC")
       Page<StockIn> findByStockInDateBetweenAndSearch(@Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     @Param("search") String search,
                     Pageable pageable);

       @Query(value = "SELECT DATE(s.stock_in_date) as date, COALESCE(SUM(s.total_amount), 0) as totalAmount " +
                     "FROM stock_in s " +
                     "WHERE s.stock_in_date >= :startDate AND s.stock_in_date <= :endDate " +
                     "GROUP BY DATE(s.stock_in_date) " +
                     "ORDER BY DATE(s.stock_in_date)", nativeQuery = true)
       List<Object[]> getStockInAnalysis(@Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);
}
