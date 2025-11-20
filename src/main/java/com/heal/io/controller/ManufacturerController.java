package com.heal.io.controller;

import com.heal.io.entity.Manufacturer;
import com.heal.io.repository.ManufacturerRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/manufacturers")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerRepository manufacturerRepository;

    @GetMapping
    public String listManufacturers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Manufacturer> manufacturers;

        if (search != null && !search.isEmpty()) {
            manufacturers = manufacturerRepository.searchManufacturers(search, pageable);
        } else {
            manufacturers = manufacturerRepository.findByIsActiveTrue(pageable);
        }

        model.addAttribute("manufacturers", manufacturers);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", manufacturers.getTotalPages());
        model.addAttribute("search", search);
        return "manufacturers/list";
    }

    @GetMapping("/new")
    public String showManufacturerForm(Model model) {
        model.addAttribute("manufacturer", new Manufacturer());
        return "manufacturers/form";
    }

    @PostMapping("/save")
    public String saveManufacturer(@ModelAttribute Manufacturer manufacturer, RedirectAttributes redirectAttributes) {
        manufacturerRepository.save(manufacturer);
        redirectAttributes.addFlashAttribute("success", "Manufacturer saved successfully!");
        return "redirect:/manufacturers";
    }

    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createManufacturer(@ModelAttribute Manufacturer manufacturer) {
        Manufacturer saved = manufacturerRepository.save(manufacturer);
        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("name", saved.getName());
        response.put("text", saved.getName());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/edit/{id}")
    public String editManufacturer(@PathVariable Long id, Model model) {
        Manufacturer manufacturer = manufacturerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Manufacturer not found"));
        model.addAttribute("manufacturer", manufacturer);
        return "manufacturers/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteManufacturer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Manufacturer manufacturer = manufacturerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Manufacturer not found"));
        manufacturer.setIsActive(false);
        manufacturerRepository.save(manufacturer);
        redirectAttributes.addFlashAttribute("success", "Manufacturer deleted successfully!");
        return "redirect:/manufacturers";
    }

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchManufacturers(@RequestParam(required = false) String q) {
        List<Manufacturer> manufacturers;
        if (q != null && !q.isEmpty()) {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Manufacturer> page = manufacturerRepository.searchManufacturers(q, pageable);
            manufacturers = page.getContent();
        } else {
            manufacturers = manufacturerRepository.findByIsActiveTrue();
        }

        List<Map<String, Object>> results = manufacturers.stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId());
                    map.put("text", m.getName());
                    map.put("name", m.getName());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}

