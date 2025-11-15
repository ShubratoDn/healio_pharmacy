package com.heal.io.controller;

import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalProducts", productRepository.countByIsActiveTrue());
        model.addAttribute("todaySales", saleRepository.getTodayTotalSales() != null ? saleRepository.getTodayTotalSales() : 0.0);
        model.addAttribute("lowStockCount", inventoryRepository.countLowStockItems());
        model.addAttribute("totalCustomers", customerRepository.countByIsActiveTrue());
        model.addAttribute("todaySalesCount", saleRepository.countTodaySales() != null ? saleRepository.countTodaySales() : 0L);
        return "dashboard";
    }
}

