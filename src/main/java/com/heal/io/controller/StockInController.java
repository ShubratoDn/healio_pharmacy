package com.heal.io.controller;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock-in")
@RequiredArgsConstructor
public class StockInController {

    private final StockInRepository stockInRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductPackageRepository productPackageRepository;
    private final InventoryRepository inventoryRepository;

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
        StockIn stockIn = new StockIn();
        stockIn.setStockInDate(LocalDate.now());
        stockIn.setReceivedDate(LocalDate.now());
        model.addAttribute("stockIn", stockIn);
        model.addAttribute("suppliers", supplierRepository.findByIsActiveTrue());
        return "stock-in/form";
    }
    
    @GetMapping("/api/products")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAllProducts() {
        List<Product> products = productRepository.findByIsActiveTrue(PageRequest.of(0, 1000)).getContent();
        List<Map<String, Object>> result = products.stream()
                .map(product -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", product.getId());
                    map.put("name", product.getName());
                    map.put("category", product.getProductCategory() != null ? product.getProductCategory().getName() : "");
                    map.put("manufacturer", product.getManufacturer() != null ? product.getManufacturer().getName() : "");
                    map.put("generic", product.getGeneric() != null ? product.getGeneric().getName() : "");
                    map.put("dosageForm", product.getDosageForm() != null ? product.getDosageForm().getName() : "");
                    map.put("strength", product.getStrength() != null ? product.getStrength() : "");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/api/product-packages/{productId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getProductPackages(@PathVariable Long productId) {
        List<ProductPackage> packages = productPackageRepository.findByProductId(productId);
        List<Map<String, Object>> result = packages.stream()
                .map(pkg -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", pkg.getId());
                    map.put("description", pkg.getPackageDescription());
                    map.put("size", pkg.getPackageSize());
                    map.put("unitPrice", pkg.getUnitPrice());
                    map.put("quantityPerPackage", pkg.getQuantityPerPackage());
                    map.put("unitOfMeasure", pkg.getUnitOfMeasure());
                    map.put("isDefault", pkg.getIsDefault());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/save")
    public String saveStockIn(
            @ModelAttribute StockIn stockIn,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(required = false) List<Long> packageIds,
            @RequestParam(required = false) List<Integer> quantities,
            @RequestParam(required = false) List<BigDecimal> unitCosts,
            @RequestParam(required = false) List<BigDecimal> sellingPrices,
            @RequestParam(required = false) List<String> expiryDates,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Generate stock in number if not provided
            if (stockIn.getStockInNumber() == null || stockIn.getStockInNumber().isEmpty()) {
                stockIn.setStockInNumber("STK-" + System.currentTimeMillis());
            }
            
            // Load supplier if provided
            if (supplierId != null) {
                stockIn.setSupplier(supplierRepository.findById(supplierId).orElse(null));
            }
            
            // Set current user as inserted by and received by
            if (authentication != null) {
                User currentUser = userRepository.findByUsername(authentication.getName())
                        .orElse(null);
                stockIn.setInsertedBy(currentUser);
                stockIn.setReceivedBy(currentUser);
            }
            
            // Calculate totals from items
            BigDecimal totalAmount = BigDecimal.ZERO;
            if (productIds != null && !productIds.isEmpty()) {
                for (int i = 0; i < productIds.size(); i++) {
                    if (productIds.get(i) != null && quantities != null && i < quantities.size() && 
                        unitCosts != null && i < unitCosts.size()) {
                        Integer qty = quantities.get(i);
                        BigDecimal unitCost = unitCosts.get(i);
                        if (qty != null && unitCost != null) {
                            totalAmount = totalAmount.add(unitCost.multiply(BigDecimal.valueOf(qty)));
                        }
                    }
                }
            }
            
            stockIn.setTotalAmount(totalAmount);
            
            // Calculate final amount
            BigDecimal discount = stockIn.getDiscountAmount() != null ? stockIn.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal tax = stockIn.getTaxAmount() != null ? stockIn.getTaxAmount() : BigDecimal.ZERO;
            stockIn.setFinalAmount(totalAmount.subtract(discount).add(tax));
            
            // Save stock in first
            StockIn savedStockIn = stockInRepository.save(stockIn);
            
            // Save stock in items and update inventory
            if (productIds != null && !productIds.isEmpty()) {
                for (int i = 0; i < productIds.size(); i++) {
                    if (productIds.get(i) == null) continue;
                    
                    final Long productId = productIds.get(i);
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
                    
                    Integer quantity = (quantities != null && i < quantities.size()) ? quantities.get(i) : 0;
                    BigDecimal unitCost = (unitCosts != null && i < unitCosts.size()) ? unitCosts.get(i) : BigDecimal.ZERO;
                    BigDecimal sellingPrice = (sellingPrices != null && i < sellingPrices.size()) ? sellingPrices.get(i) : null;
                    String expiryDateStr = (expiryDates != null && i < expiryDates.size()) ? expiryDates.get(i) : null;
                    
                    if (quantity == null || quantity <= 0 || unitCost == null) {
                        continue;
                    }
                    
                    LocalDate expiryDate = null;
                    if (expiryDateStr != null && !expiryDateStr.trim().isEmpty()) {
                        try {
                            expiryDate = LocalDate.parse(expiryDateStr);
                        } catch (Exception e) {
                            // Invalid date, skip
                        }
                    }
                    
                    ProductPackage productPackage = null;
                    if (packageIds != null && i < packageIds.size() && packageIds.get(i) != null) {
                        productPackage = productPackageRepository.findById(packageIds.get(i)).orElse(null);
                    }
                    
                    BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(quantity));
                    
                    // Create stock in item
                    StockInItem item = StockInItem.builder()
                            .stockIn(savedStockIn)
                            .product(product)
                            .productPackage(productPackage)
                            .quantity(quantity)
                            .unitCost(unitCost)
                            .totalCost(totalCost)
                            .sellingPrice(sellingPrice)
                            .batchNumber(null)
                            .expiryDate(expiryDate)
                            .location(null)
                            .notes(null)
                            .build();
                    
                    savedStockIn.getItems().add(item);
                    
                    // Update inventory
                    updateInventory(product, productPackage, quantity, unitCost, sellingPrice, null, expiryDate, null);
                }
            }
            
            stockInRepository.save(savedStockIn);
            redirectAttributes.addFlashAttribute("success", "Stock In saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving stock in: " + e.getMessage());
            e.printStackTrace();
        }
        
        return "redirect:/stock-in";
    }
    
    private void updateInventory(Product product, ProductPackage productPackage, Integer quantity, 
                                  BigDecimal unitCost, BigDecimal sellingPrice, String batchNumber, 
                                  LocalDate expiryDate, String location) {
        // Try to find existing inventory with same product, package, batch, and expiry
        Optional<Inventory> existingInventory = inventoryRepository.findByProductAndPackageAndBatchAndExpiry(
                product.getId(),
                productPackage != null ? productPackage.getId() : null,
                batchNumber,
                expiryDate
        );
        
        Inventory inventory;
        if (existingInventory.isPresent()) {
            inventory = existingInventory.get();
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventory.setLastRestockedAt(LocalDateTime.now());
        } else {
            inventory = Inventory.builder()
                    .product(product)
                    .productPackage(productPackage)
                    .batchNumber(batchNumber)
                    .expiryDate(expiryDate)
                    .quantity(quantity)
                    .reservedQuantity(0)
                    .availableQuantity(quantity)
                    .costPrice(unitCost)
                    .sellingPrice(sellingPrice)
                    .location(location)
                    .lastRestockedAt(LocalDateTime.now())
                    .build();
        }
        
        // Update prices if provided
        if (unitCost != null) {
            inventory.setCostPrice(unitCost);
        }
        if (sellingPrice != null) {
            inventory.setSellingPrice(sellingPrice);
        }
        
        inventoryRepository.save(inventory);
    }
}

