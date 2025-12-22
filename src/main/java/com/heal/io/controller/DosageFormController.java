package com.heal.io.controller;

import com.heal.io.entity.DosageForm;
import com.heal.io.repository.DosageFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dosage-forms")
@RequiredArgsConstructor
public class DosageFormController {

    private final DosageFormRepository dosageFormRepository;

    @GetMapping
    public String listDosageForms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DosageForm> dosageForms;

        if (search != null && !search.isEmpty()) {
            dosageForms = dosageFormRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(search, pageable);
        } else {
            dosageForms = dosageFormRepository.findByIsActiveTrue(pageable);
        }

        model.addAttribute("dosageForms", dosageForms);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", dosageForms.getTotalPages());
        model.addAttribute("search", search);
        return "dosage_forms/list";
    }

    @GetMapping("/new")
    public String showDosageFormForm(Model model) {
        model.addAttribute("dosageForm", new DosageForm());
        return "dosage_forms/form";
    }

    @PostMapping("/save")
    public String saveDosageForm(@ModelAttribute DosageForm dosageForm, RedirectAttributes redirectAttributes) {
        try {
            dosageFormRepository.save(dosageForm);
            redirectAttributes.addFlashAttribute("success", "Dosage Form saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving dosage form: " + e.getMessage());
        }
        return "redirect:/dosage-forms";
    }

    @GetMapping("/edit/{id}")
    public String editDosageForm(@PathVariable Long id, Model model) {
        DosageForm dosageForm = dosageFormRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dosage Form not found"));
        model.addAttribute("dosageForm", dosageForm);
        return "dosage_forms/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteDosageForm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        DosageForm dosageForm = dosageFormRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dosage Form not found"));
        dosageForm.setIsActive(false);
        dosageFormRepository.save(dosageForm);
        redirectAttributes.addFlashAttribute("success", "Dosage Form deleted successfully!");
        return "redirect:/dosage-forms";
    }
}
