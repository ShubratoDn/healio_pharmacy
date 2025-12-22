package com.heal.io.controller;

import com.heal.io.entity.Inventory;
import com.heal.io.entity.ProductPackage;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.heal.io.entity.Sale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InventoryRepository inventoryRepository;
    private final StockInRepository stockInRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalProducts", productRepository.countByIsActiveTrue());

        // Today's sales
        Double todaySales = saleRepository.getTodayTotalSales() != null ? saleRepository.getTodayTotalSales() : 0.0;
        model.addAttribute("todaySales", todaySales);

        // Today's profit (sum of profit_amount from sale_items)
        Double todayProfit = saleRepository.getTodayTotalProfit() != null ? saleRepository.getTodayTotalProfit() : 0.0;
        model.addAttribute("todayProfit", todayProfit);

        // Today's discount (sum of discount_amount from sales)
        Double todayDiscount = saleRepository.getTodayTotalDiscount() != null ? saleRepository.getTodayTotalDiscount()
                : 0.0;
        model.addAttribute("todayDiscount", todayDiscount);

        // Total profit = Profit - Discount
        Double totalProfit = todayProfit - todayDiscount;
        model.addAttribute("totalProfit", totalProfit);

        // Calculate low stock count (matching the logic from InventoryController)
        List<Inventory> allInventories = inventoryRepository.findAllActiveInventories();

        // Group by product-package combination
        Map<String, List<Inventory>> groupedInventories = allInventories.stream()
                .collect(Collectors.groupingBy(inv -> {
                    Long productId = inv.getProduct() != null ? inv.getProduct().getId() : 0L;
                    Long packageId = inv.getProductPackage() != null ? inv.getProductPackage().getId() : 0L;
                    return productId + "_" + packageId;
                }));

        // Count low stock items
        long lowStockCount = groupedInventories.values().stream()
                .filter(inventories -> {
                    if (inventories.isEmpty())
                        return false;

                    Inventory firstInv = inventories.get(0);
                    ProductPackage productPackage = firstInv.getProductPackage();

                    // Sum available quantities for this product-package combination
                    int totalAvailable = inventories.stream()
                            .mapToInt(Inventory::getAvailableQuantity)
                            .sum();

                    // Check if low stock
                    if (productPackage != null && productPackage.getLowStock() != null) {
                        return totalAvailable <= productPackage.getLowStock();
                    } else {
                        Integer reorderLevel = firstInv.getReorderLevel() != null ? firstInv.getReorderLevel() : 10;
                        return totalAvailable <= reorderLevel;
                    }
                })
                .count();

        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("todaySalesCount",
                saleRepository.countTodaySales() != null ? saleRepository.countTodaySales() : 0L);

        // Recent sales
        Pageable pageable = PageRequest.of(0, 5);
        List<Sale> recentSales = saleRepository.findAllByOrderBySaleDateDesc(pageable).getContent();
        model.addAttribute("recentSales", recentSales);

        return "dashboard";
    }

    @GetMapping("/api/dashboard/sales-by-date")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSalesByDate(
            @RequestParam(defaultValue = "7") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> results = saleRepository.getSalesByDateRange(startDate, endDate.plusDays(1));

        List<String> dates = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        List<Double> profits = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

        // Fill in all dates in range, even if no sales
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            dates.add(date.format(formatter));
            amounts.add(0.0);
            profits.add(0.0);
        }

        // Update with actual sales data
        for (Object[] result : results) {
            LocalDate saleDate = ((java.sql.Date) result[0]).toLocalDate();
            BigDecimal totalAmount = (BigDecimal) result[1];
            BigDecimal totalProfit = (BigDecimal) result[2];

            int index = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, saleDate);
            if (index >= 0 && index < days) {
                amounts.set(index, totalAmount.doubleValue());
                profits.set(index, totalProfit.doubleValue());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dates", dates);
        response.put("amounts", amounts);
        response.put("profits", profits);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/dashboard/sales-by-dosage-form")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSalesByDosageForm(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> results = saleRepository.getSalesByDosageForm(startDate, endDate.plusDays(1));

        List<Map<String, Object>> data = new ArrayList<>();

        for (Object[] result : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", result[0]);
            item.put("value", ((BigDecimal) result[1]).doubleValue());
            data.add(item);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/dashboard/stock-in-analysis")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStockInAnalysis(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> results = stockInRepository.getStockInAnalysis(startDate, endDate);

        List<String> dates = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

        // Fill dates
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            dates.add(date.format(formatter));
            amounts.add(0.0);
        }

        for (Object[] result : results) {
            LocalDate stockDate = ((java.sql.Date) result[0]).toLocalDate();
            BigDecimal amount = (BigDecimal) result[1];

            int index = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, stockDate);
            if (index >= 0 && index < days) {
                amounts.set(index, amount.doubleValue());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dates", dates);
        response.put("amounts", amounts);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/dashboard/top-products")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTopProducts(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> results = saleRepository.getTopProductsByQuantity(startDate, endDate.plusDays(1), limit);

        List<String> productNames = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();

        for (Object[] result : results) {
            productNames.add((String) result[0]);
            quantities.add(((Number) result[1]).intValue());
            revenues.add(((BigDecimal) result[2]).doubleValue());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("products", productNames);
        response.put("quantities", quantities);
        response.put("revenues", revenues);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/dashboard/profit-discount")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProfitAndDiscount(
            @RequestParam(defaultValue = "7") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> results = saleRepository.getProfitAndDiscountByDateRange(startDate, endDate.plusDays(1));

        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        if (!results.isEmpty()) {
            Object[] result = results.get(0);
            totalProfit = result[0] != null ? (BigDecimal) result[0] : BigDecimal.ZERO;
            totalDiscount = result[1] != null ? (BigDecimal) result[1] : BigDecimal.ZERO;
        }

        BigDecimal totalNetProfit = totalProfit.subtract(totalDiscount);

        Map<String, Object> response = new HashMap<>();
        response.put("profit", totalProfit.doubleValue());
        response.put("discount", totalDiscount.doubleValue());
        response.put("netProfit", totalNetProfit.doubleValue());

        return ResponseEntity.ok(response);
    }
}
