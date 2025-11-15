package com.heal.io.controller;

import com.heal.io.entity.Supplier;
import com.heal.io.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;

    @GetMapping
    public String listSuppliers(Model model) {
        List<Supplier> suppliers = supplierRepository.findByIsActiveTrue();
        model.addAttribute("suppliers", suppliers);
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String showSupplierForm(Model model) {
        model.addAttribute("supplier", new Supplier());
        return "suppliers/form";
    }

    @PostMapping("/save")
    public String saveSupplier(@ModelAttribute Supplier supplier, RedirectAttributes redirectAttributes) {
        supplierRepository.save(supplier);
        redirectAttributes.addFlashAttribute("success", "Supplier saved successfully!");
        return "redirect:/suppliers";
    }

    @GetMapping("/edit/{id}")
    public String editSupplier(@PathVariable Long id, Model model) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        model.addAttribute("supplier", supplier);
        return "suppliers/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteSupplier(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplier.setIsActive(false);
        supplierRepository.save(supplier);
        redirectAttributes.addFlashAttribute("success", "Supplier deleted successfully!");
        return "redirect:/suppliers";
    }
}

