package com.heal.io.controller;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ProductPackageRepository productPackageRepository;

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
        model.addAttribute("pageSize", size);
        model.addAttribute("totalElements", products.getTotalElements());
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
        model.addAttribute("packages", List.of()); // Empty list for new product
        return "products/form";
    }

    @PostMapping("/save")
    public String saveProduct(
            @ModelAttribute Product product,
            @RequestParam(value = "packageIds", required = false) List<Long> packageIds,
            @RequestParam(value = "packageDescriptions", required = false) List<String> packageDescriptions,
            @RequestParam(value = "packageSizes", required = false) List<String> packageSizes,
            @RequestParam(value = "unitPrices", required = false) List<String> unitPrices,
            @RequestParam(value = "quantities", required = false) List<String> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            @RequestParam(value = "isDefaults", required = false) List<String> isDefaults,
            RedirectAttributes redirectAttributes) {
        
        Product savedProduct;
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
            savedProduct = productRepository.save(existing);
            
            // Delete existing packages that are not in the submitted list
            List<ProductPackage> existingPackages = productPackageRepository.findByProductId(savedProduct.getId());
            if (packageIds != null) {
                existingPackages.stream()
                    .filter(pkg -> !packageIds.contains(pkg.getId()))
                    .forEach(productPackageRepository::delete);
            } else {
                existingPackages.forEach(productPackageRepository::delete);
            }
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
            savedProduct = productRepository.save(product);
        }
        
        // Save packages
        if (packageDescriptions != null && !packageDescriptions.isEmpty()) {
            for (int i = 0; i < packageDescriptions.size(); i++) {
                String description = packageDescriptions.get(i);
                if (description != null && !description.trim().isEmpty()) {
                    ProductPackage productPackage;
                    if (packageIds != null && i < packageIds.size() && packageIds.get(i) != null) {
                        // Update existing package
                        productPackage = productPackageRepository.findById(packageIds.get(i))
                                .orElse(new ProductPackage());
                    } else {
                        // Create new package
                        productPackage = new ProductPackage();
                    }
                    
                    productPackage.setProduct(savedProduct);
                    productPackage.setPackageDescription(description.trim());
                    productPackage.setPackageSize((packageSizes != null && i < packageSizes.size()) ? packageSizes.get(i) : null);
                    
                    try {
                        if (unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null && !unitPrices.get(i).trim().isEmpty()) {
                            productPackage.setUnitPrice(new BigDecimal(unitPrices.get(i).trim()));
                        }
                        if (quantities != null && i < quantities.size() && quantities.get(i) != null && !quantities.get(i).trim().isEmpty()) {
                            productPackage.setQuantityPerPackage(Integer.parseInt(quantities.get(i).trim()));
                        }
                        if (units != null && i < units.size() && units.get(i) != null && !units.get(i).trim().isEmpty()) {
                            productPackage.setUnitOfMeasure(units.get(i).trim());
                        }
                        if (isDefaults != null && i < isDefaults.size()) {
                            productPackage.setIsDefault("on".equals(isDefaults.get(i)) || "true".equals(isDefaults.get(i)));
                        } else {
                            productPackage.setIsDefault(false);
                        }
                    } catch (Exception e) {
                        // Log error but continue
                        redirectAttributes.addFlashAttribute("error", "Error saving package: " + e.getMessage());
                    }
                    
                    productPackageRepository.save(productPackage);
                }
            }
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
        model.addAttribute("packages", productPackageRepository.findByProductId(id));
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

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchProducts(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        Pageable pageable = PageRequest.of(0, 10); // Limit to 10 results for dropdown
        Page<Product> products = productRepository.searchProducts(q.trim(), pageable);
        
        List<Map<String, Object>> results = products.getContent().stream()
                .map(product -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", product.getId());
                    result.put("name", product.getName());
                    result.put("category", product.getProductCategory() != null ? product.getProductCategory().getName() : "");
                    result.put("manufacturer", product.getManufacturer() != null ? product.getManufacturer().getName() : "");
                    result.put("generic", product.getGeneric() != null ? product.getGeneric().getName() : "");
                    result.put("dosageForm", product.getDosageForm() != null ? product.getDosageForm().getName() : "");
                    result.put("strength", product.getStrength() != null ? product.getStrength() : "");
                    return result;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(results);
    }
}

