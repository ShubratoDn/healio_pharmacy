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

       @Query(value = "SELECT DATE(s.sale_date) as saleDate, COALESCE(SUM(s.total_amount), 0) as totalAmount, " +
                     "COALESCE(SUM((SELECT SUM(si.profit_amount) FROM sale_item si WHERE si.sale_id = s.id)), 0) as totalProfit "
                     +
                     "FROM sale s WHERE s.sale_date >= :startDate AND s.sale_date < :endDate AND s.sale_status = 'COMPLETED' "
                     +
                     "GROUP BY DATE(s.sale_date) ORDER BY DATE(s.sale_date)", nativeQuery = true)
       List<Object[]> getSalesByDateRange(@Param("startDate") java.time.LocalDate startDate,
                     @Param("endDate") java.time.LocalDate endDate);

       @Query(value = "SELECT pc.name as categoryName, COALESCE(SUM(si.total_price), 0) as totalRevenue " +
                     "FROM sale s " +
                     "INNER JOIN sale_item si ON s.id = si.sale_id " +
                     "INNER JOIN product p ON si.product_id = p.id " +
                     "INNER JOIN product_category pc ON p.product_category_id = pc.id " +
                     "WHERE s.sale_date >= :startDate AND s.sale_date < :endDate AND s.sale_status = 'COMPLETED' " +
                     "GROUP BY pc.id, pc.name " +
                     "ORDER BY totalRevenue DESC", nativeQuery = true)
       List<Object[]> getSalesByCategory(@Param("startDate") java.time.LocalDate startDate,
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

       @Query(value = "SELECT " +
                     "(SELECT COALESCE(SUM(si.profit_amount), 0) FROM sale s1 " +
                     " JOIN sale_item si ON s1.id = si.sale_id " +
                     " WHERE s1.sale_date >= :startDate AND s1.sale_date < :endDate AND s1.sale_status = 'COMPLETED') as totalProfit, "
                     +
                     "(SELECT COALESCE(SUM(s2.discount_amount), 0) FROM sale s2 " +
                     " WHERE s2.sale_date >= :startDate AND s2.sale_date < :endDate AND s2.sale_status = 'COMPLETED') as totalDiscount", nativeQuery = true)
       List<Object[]> getProfitAndDiscountByDateRange(@Param("startDate") java.time.LocalDate startDate,
                     @Param("endDate") java.time.LocalDate endDate);
}
