package com.heal.io.controller;

import com.heal.io.entity.Manufacturer;
import com.heal.io.entity.Supplier;
import com.heal.io.repository.ManufacturerRepository;
import com.heal.io.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final ManufacturerRepository manufacturerRepository;

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
    public String saveSupplier(
            @ModelAttribute Supplier supplier,
            @RequestParam(required = false) List<Long> manufacturerIds,
            RedirectAttributes redirectAttributes) {
        
        // Handle manufacturers relationship
        if (manufacturerIds != null && !manufacturerIds.isEmpty()) {
            Set<Manufacturer> manufacturers = new HashSet<>();
            for (Long manufacturerId : manufacturerIds) {
                manufacturerRepository.findById(manufacturerId)
                        .ifPresent(manufacturers::add);
            }
            supplier.setManufacturers(manufacturers);
        } else {
            supplier.setManufacturers(new HashSet<>());
        }
        
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

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchSuppliers(@RequestParam(required = false) String q) {
        List<Supplier> suppliers;
        if (q != null && !q.isEmpty()) {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Supplier> page = supplierRepository.searchSuppliers(q, pageable);
            suppliers = page.getContent();
        } else {
            suppliers = supplierRepository.findByIsActiveTrue();
        }

        // Initialize manufacturers for each supplier to avoid lazy loading issues
        suppliers.forEach(supplier -> {
            if (supplier.getManufacturers() != null) {
                supplier.getManufacturers().size(); // Force initialization
            }
        });

        List<Map<String, Object>> results = suppliers.stream()
                .map(supplier -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", supplier.getId());
                    
                    // Build display text: Name - Phone (Manufacturers)
                    StringBuilder text = new StringBuilder(supplier.getName());
                    if (supplier.getPhone() != null && !supplier.getPhone().isEmpty()) {
                        text.append(" - ").append(supplier.getPhone());
                    }
                    if (supplier.getManufacturers() != null && !supplier.getManufacturers().isEmpty()) {
                        String manufacturers = supplier.getManufacturers().stream()
                                .map(Manufacturer::getName)
                                .collect(Collectors.joining(", "));
                        text.append(" (").append(manufacturers).append(")");
                    }
                    
                    map.put("text", text.toString());
                    map.put("name", supplier.getName());
                    map.put("phone", supplier.getPhone() != null ? supplier.getPhone() : "");
                    map.put("manufacturers", supplier.getManufacturers() != null ? 
                            supplier.getManufacturers().stream()
                                    .map(Manufacturer::getName)
                                    .collect(Collectors.joining(", ")) : "");
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}

