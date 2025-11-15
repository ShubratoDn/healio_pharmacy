package com.heal.io.controller;

import com.heal.io.entity.StockIn;
import com.heal.io.repository.StockInRepository;
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
@RequestMapping("/stock-in")
@RequiredArgsConstructor
public class StockInController {

    private final StockInRepository stockInRepository;

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
        return "stock-in/form";
    }
}

