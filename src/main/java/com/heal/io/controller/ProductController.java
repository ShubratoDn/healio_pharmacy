package com.heal.io.controller;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final DosageFormRepository dosageFormRepository;
    private final GenericRepository genericRepository;
    private final MedicineTypeRepository medicineTypeRepository;

    @GetMapping
    public String listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products;

        if (search != null && !search.isEmpty()) {
            products = productRepository.searchProducts(search, pageable);
        } else {
            products = productRepository.findByIsActiveTrue(pageable);
        }

        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("search", search);
        return "products/list";
    }

    @GetMapping("/new")
    public String showProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryRepository.findByIsActiveTrue());
        model.addAttribute("manufacturers", manufacturerRepository.findByIsActiveTrue());
        model.addAttribute("dosageForms", dosageFormRepository.findAll());
        model.addAttribute("generics", genericRepository.findAll());
        model.addAttribute("medicineTypes", medicineTypeRepository.findAll());
        return "products/form";
    }

    @PostMapping("/save")
    public String saveProduct(@ModelAttribute Product product, RedirectAttributes redirectAttributes) {
        if (product.getId() != null) {
            Product existing = productRepository.findById(product.getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            // Preserve existing relationships if not provided
            if (product.getProductCategory() != null && product.getProductCategory().getId() != null) {
                existing.setProductCategory(categoryRepository.findById(product.getProductCategory().getId()).orElse(null));
            }
            if (product.getManufacturer() != null && product.getManufacturer().getId() != null) {
                existing.setManufacturer(manufacturerRepository.findById(product.getManufacturer().getId()).orElse(null));
            }
            if (product.getMedicineType() != null && product.getMedicineType().getId() != null) {
                existing.setMedicineType(medicineTypeRepository.findById(product.getMedicineType().getId()).orElse(null));
            }
            if (product.getDosageForm() != null && product.getDosageForm().getId() != null) {
                existing.setDosageForm(dosageFormRepository.findById(product.getDosageForm().getId()).orElse(null));
            }
            if (product.getGeneric() != null && product.getGeneric().getId() != null) {
                existing.setGeneric(genericRepository.findById(product.getGeneric().getId()).orElse(null));
            }
            existing.setName(product.getName());
            existing.setStrength(product.getStrength());
            existing.setDescription(product.getDescription());
            existing.setRequiresPrescription(product.getRequiresPrescription());
            productRepository.save(existing);
        } else {
            // Load full entities for new product
            if (product.getProductCategory() != null && product.getProductCategory().getId() != null) {
                product.setProductCategory(categoryRepository.findById(product.getProductCategory().getId()).orElse(null));
            }
            if (product.getManufacturer() != null && product.getManufacturer().getId() != null) {
                product.setManufacturer(manufacturerRepository.findById(product.getManufacturer().getId()).orElse(null));
            }
            if (product.getMedicineType() != null && product.getMedicineType().getId() != null) {
                product.setMedicineType(medicineTypeRepository.findById(product.getMedicineType().getId()).orElse(null));
            }
            if (product.getDosageForm() != null && product.getDosageForm().getId() != null) {
                product.setDosageForm(dosageFormRepository.findById(product.getDosageForm().getId()).orElse(null));
            }
            if (product.getGeneric() != null && product.getGeneric().getId() != null) {
                product.setGeneric(genericRepository.findById(product.getGeneric().getId()).orElse(null));
            }
            productRepository.save(product);
        }
        redirectAttributes.addFlashAttribute("success", "Product saved successfully!");
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryRepository.findByIsActiveTrue());
        model.addAttribute("manufacturers", manufacturerRepository.findByIsActiveTrue());
        model.addAttribute("dosageForms", dosageFormRepository.findAll());
        model.addAttribute("generics", genericRepository.findAll());
        model.addAttribute("medicineTypes", medicineTypeRepository.findAll());
        return "products/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setIsActive(false);
        productRepository.save(product);
        redirectAttributes.addFlashAttribute("success", "Product deleted successfully!");
        return "redirect:/products";
    }
}

