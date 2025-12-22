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
    private final ManufacturerRepository manufacturerRepository;
    private final DosageFormRepository dosageFormRepository;
    private final GenericRepository genericRepository;
    private final MedicineTypeRepository medicineTypeRepository;
    private final ProductPackageRepository productPackageRepository;
    private final StockInItemRepository stockInItemRepository;
    private final InventoryRepository inventoryRepository;

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
            @RequestParam(value = "lowStocks", required = false) List<String> lowStocks,
            RedirectAttributes redirectAttributes) {

        Product savedProduct;
        if (product.getId() != null) {
            Product existing = productRepository.findById(product.getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            // Handle relationships
            existing.setManufacturer((product.getManufacturer() != null && product.getManufacturer().getId() != null)
                    ? manufacturerRepository.findById(product.getManufacturer().getId()).orElse(null)
                    : null);
            existing.setMedicineType((product.getMedicineType() != null && product.getMedicineType().getId() != null)
                    ? medicineTypeRepository.findById(product.getMedicineType().getId()).orElse(null)
                    : null);
            existing.setDosageForm((product.getDosageForm() != null && product.getDosageForm().getId() != null)
                    ? dosageFormRepository.findById(product.getDosageForm().getId()).orElse(null)
                    : null);
            existing.setGeneric((product.getGeneric() != null && product.getGeneric().getId() != null)
                    ? genericRepository.findById(product.getGeneric().getId()).orElse(null)
                    : null);
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
            // Handle relationships - ensure transient objects from Spring binding are
            // cleared
            product.setManufacturer((product.getManufacturer() != null && product.getManufacturer().getId() != null)
                    ? manufacturerRepository.findById(product.getManufacturer().getId()).orElse(null)
                    : null);
            product.setMedicineType((product.getMedicineType() != null && product.getMedicineType().getId() != null)
                    ? medicineTypeRepository.findById(product.getMedicineType().getId()).orElse(null)
                    : null);
            product.setDosageForm((product.getDosageForm() != null && product.getDosageForm().getId() != null)
                    ? dosageFormRepository.findById(product.getDosageForm().getId()).orElse(null)
                    : null);
            product.setGeneric((product.getGeneric() != null && product.getGeneric().getId() != null)
                    ? genericRepository.findById(product.getGeneric().getId()).orElse(null)
                    : null);
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
                    productPackage.setPackageSize(
                            (packageSizes != null && i < packageSizes.size()) ? packageSizes.get(i) : null);

                    try {
                        if (unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null
                                && !unitPrices.get(i).trim().isEmpty()) {
                            productPackage.setUnitPrice(new BigDecimal(unitPrices.get(i).trim()));
                        }
                        if (quantities != null && i < quantities.size() && quantities.get(i) != null
                                && !quantities.get(i).trim().isEmpty()) {
                            productPackage.setQuantityPerPackage(Integer.parseInt(quantities.get(i).trim()));
                        }
                        if (units != null && i < units.size() && units.get(i) != null
                                && !units.get(i).trim().isEmpty()) {
                            productPackage.setUnitOfMeasure(units.get(i).trim());
                        }
                        if (lowStocks != null && i < lowStocks.size() && lowStocks.get(i) != null
                                && !lowStocks.get(i).trim().isEmpty()) {
                            try {
                                productPackage.setLowStock(Integer.parseInt(lowStocks.get(i).trim()));
                            } catch (NumberFormatException e) {
                                productPackage.setLowStock(null);
                            }
                        } else {
                            productPackage.setLowStock(null);
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

    @GetMapping("/{id}")
    public String viewProduct(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Initialize lazy-loaded relationships
        if (product.getManufacturer() != null) {
            product.getManufacturer().getName();
        }
        if (product.getGeneric() != null) {
            product.getGeneric().getName();
        }
        if (product.getDosageForm() != null) {
            product.getDosageForm().getName();
        }
        if (product.getMedicineType() != null) {
            product.getMedicineType().getName();
        }

        // Load packages
        List<ProductPackage> packages = productPackageRepository.findByProductId(id);
        packages.forEach(pkg -> {
            // Initialize package
            pkg.getPackageDescription();
        });

        // Load inventory grouped by package
        List<Inventory> inventories = inventoryRepository.findByProductId(id);
        Map<Long, List<Inventory>> inventoryByPackage = inventories.stream()
                .filter(inv -> inv.getIsActive() != null && inv.getIsActive())
                .collect(Collectors
                        .groupingBy(inv -> inv.getProductPackage() != null ? inv.getProductPackage().getId() : 0L));

        // Calculate total inventory stats
        int totalQuantity = inventories.stream()
                .filter(inv -> inv.getIsActive() != null && inv.getIsActive())
                .mapToInt(Inventory::getQuantity)
                .sum();
        int totalAvailable = inventories.stream()
                .filter(inv -> inv.getIsActive() != null && inv.getIsActive())
                .mapToInt(Inventory::getAvailableQuantity)
                .sum();

        // Calculate stock status: 0=Out of Stock, 1=Low Stock, 2=In Stock
        int stockStatus;
        if (totalAvailable == 0) {
            stockStatus = 0; // Out of Stock
        } else {
            // Determine low stock threshold - use the minimum lowStock from packages or
            // default reorder level
            Integer lowStockThreshold = null;
            for (ProductPackage pkg : packages) {
                if (pkg.getLowStock() != null) {
                    if (lowStockThreshold == null || pkg.getLowStock() < lowStockThreshold) {
                        lowStockThreshold = pkg.getLowStock();
                    }
                }
            }
            // If no package lowStock set, use default reorder level from inventory
            if (lowStockThreshold == null && !inventories.isEmpty()) {
                Inventory firstInv = inventories.stream()
                        .filter(inv -> inv.getIsActive() != null && inv.getIsActive())
                        .findFirst()
                        .orElse(null);
                if (firstInv != null) {
                    lowStockThreshold = firstInv.getReorderLevel() != null ? firstInv.getReorderLevel() : 10;
                } else {
                    lowStockThreshold = 10; // Default
                }
            } else if (lowStockThreshold == null) {
                lowStockThreshold = 10; // Default
            }

            if (totalAvailable <= lowStockThreshold) {
                stockStatus = 1; // Low Stock
            } else {
                stockStatus = 2; // In Stock
            }
        }

        // Get purchase prices for packages
        Map<Long, BigDecimal> purchasePrices = new HashMap<>();
        for (ProductPackage pkg : packages) {
            List<BigDecimal> unitCosts = stockInItemRepository.findUnitCostsByProductAndPackageOrderByDateDesc(
                    id, pkg.getId());
            if (!unitCosts.isEmpty()) {
                purchasePrices.put(pkg.getId(), unitCosts.get(0)); // Get the most recent price
            }
        }

        // Calculate percentage for progress bar
        double percentage = 0.0;
        if (totalQuantity > 0) {
            percentage = Math.min((totalAvailable * 100.0 / totalQuantity), 100.0);
        } else if (totalAvailable > 0) {
            percentage = 100.0;
        }

        model.addAttribute("product", product);
        model.addAttribute("packages", packages);
        model.addAttribute("inventoryByPackage", inventoryByPackage);
        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("totalAvailable", totalAvailable);
        model.addAttribute("stockStatus", stockStatus);
        model.addAttribute("inventoryPercentage", percentage);
        model.addAttribute("purchasePrices", purchasePrices);

        return "products/view";
    }

    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        model.addAttribute("product", product);
        model.addAttribute("manufacturers", manufacturerRepository.findByIsActiveTrue());
        model.addAttribute("dosageForms", dosageFormRepository.findAll());
        model.addAttribute("generics", genericRepository.findAll());
        model.addAttribute("medicineTypes", medicineTypeRepository.findAll());

        List<ProductPackage> packages = productPackageRepository.findByProductId(id);
        model.addAttribute("packages", packages);

        // Fetch last purchase price for each package
        Map<Long, BigDecimal> purchasePrices = new HashMap<>();
        for (ProductPackage pkg : packages) {
            List<BigDecimal> unitCosts = stockInItemRepository.findUnitCostsByProductAndPackageOrderByDateDesc(
                    id, pkg.getId());
            if (!unitCosts.isEmpty()) {
                purchasePrices.put(pkg.getId(), unitCosts.get(0)); // Get the most recent price
            }
        }
        model.addAttribute("purchasePrices", purchasePrices);

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
                    result.put("manufacturer",
                            product.getManufacturer() != null ? product.getManufacturer().getName() : "");
                    result.put("generic", product.getGeneric() != null ? product.getGeneric().getName() : "");
                    result.put("dosageForm", product.getDosageForm() != null ? product.getDosageForm().getName() : "");
                    result.put("strength", product.getStrength() != null ? product.getStrength() : "");
                    return result;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}
