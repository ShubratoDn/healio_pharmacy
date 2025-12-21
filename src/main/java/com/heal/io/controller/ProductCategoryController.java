package com.heal.io.controller;

import com.heal.io.entity.ProductCategory;
import com.heal.io.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryRepository categoryRepository;

    @GetMapping
    public String listCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductCategory> categories;

        if (search != null && !search.isEmpty()) {
            categories = categoryRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(search, pageable);
        } else {
            categories = categoryRepository.findByIsActiveTrue(pageable);
        }

        model.addAttribute("categories", categories);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", categories.getTotalPages());
        model.addAttribute("search", search);
        return "categories/list";
    }

    @GetMapping("/new")
    public String showCategoryForm(Model model) {
        model.addAttribute("category", new ProductCategory());
        return "categories/form";
    }

    @PostMapping("/save")
    public String saveCategory(@ModelAttribute ProductCategory category, RedirectAttributes redirectAttributes) {
        try {
            categoryRepository.save(category);
            redirectAttributes.addFlashAttribute("success", "Category saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving category: " + e.getMessage());
        }
        return "redirect:/categories";
    }

    @GetMapping("/edit/{id}")
    public String editCategory(@PathVariable Long id, Model model) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        model.addAttribute("category", category);
        return "categories/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setIsActive(false);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        return "redirect:/categories";
    }
}

