package com.heal.io.controller;

import com.heal.io.entity.Inventory;
import com.heal.io.entity.ProductPackage;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InventoryRepository inventoryRepository;

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
        Double todayDiscount = saleRepository.getTodayTotalDiscount() != null ? saleRepository.getTodayTotalDiscount() : 0.0;
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
                    if (inventories.isEmpty()) return false;
                    
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
        model.addAttribute("todaySalesCount", saleRepository.countTodaySales() != null ? saleRepository.countTodaySales() : 0L);
        return "dashboard";
    }
}

