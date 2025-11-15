package com.heal.io.controller;

import com.heal.io.entity.StockIn;
import com.heal.io.entity.User;
import com.heal.io.repository.StockInRepository;
import com.heal.io.repository.SupplierRepository;
import com.heal.io.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/stock-in")
@RequiredArgsConstructor
public class StockInController {

    private final StockInRepository stockInRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String listStockIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockIn> stockIns = stockInRepository.findAllByOrderByStockInDateDesc(pageable);
        
        model.addAttribute("stockIns", stockIns);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", stockIns.getTotalPages());
        return "stock-in/list";
    }

    @GetMapping("/new")
    public String newStockIn(Model model) {
        model.addAttribute("stockIn", new StockIn());
        model.addAttribute("suppliers", supplierRepository.findByIsActiveTrue());
        return "stock-in/form";
    }

    @PostMapping("/save")
    public String saveStockIn(
            @ModelAttribute StockIn stockIn,
            @RequestParam(required = false) Long supplierId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        // Generate stock in number if not provided
        if (stockIn.getStockInNumber() == null || stockIn.getStockInNumber().isEmpty()) {
            stockIn.setStockInNumber("STK-" + System.currentTimeMillis());
        }
        
        // Load supplier if provided
        if (supplierId != null) {
            stockIn.setSupplier(supplierRepository.findById(supplierId).orElse(null));
        }
        
        // Set current user as inserted by and received by
        if (authentication != null) {
            User currentUser = userRepository.findByUsername(authentication.getName())
                    .orElse(null);
            stockIn.setInsertedBy(currentUser);
            stockIn.setReceivedBy(currentUser);
        }
        
        // Calculate final amount if not set
        if (stockIn.getFinalAmount() == null) {
            java.math.BigDecimal total = stockIn.getTotalAmount() != null ? stockIn.getTotalAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal discount = stockIn.getDiscountAmount() != null ? stockIn.getDiscountAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal tax = stockIn.getTaxAmount() != null ? stockIn.getTaxAmount() : java.math.BigDecimal.ZERO;
            stockIn.setFinalAmount(total.subtract(discount).add(tax));
        }
        
        stockInRepository.save(stockIn);
        redirectAttributes.addFlashAttribute("success", "Stock In saved successfully!");
        return "redirect:/stock-in";
    }
}

