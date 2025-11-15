package com.heal.io.controller;

import com.heal.io.entity.Sale;
import com.heal.io.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleRepository saleRepository;

    @GetMapping
    public String listSales(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Sale> sales = saleRepository.findAllByOrderBySaleDateDesc(pageable);
        
        model.addAttribute("sales", sales);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", sales.getTotalPages());
        return "sales/list";
    }

    @GetMapping("/new")
    public String newSale(Model model) {
        model.addAttribute("sale", new Sale());
        return "sales/form";
    }
}

