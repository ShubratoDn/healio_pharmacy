package com.heal.io.controller;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import com.heal.io.service.StockInVoucherService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final StockInVoucherService stockInVoucherService;

    @GetMapping
    public String listStockIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockIn> stockIns;
        
        boolean hasDateRange = startDate != null && !startDate.isEmpty() && 
                              endDate != null && !endDate.isEmpty();
        boolean hasSearch = search != null && !search.trim().isEmpty();
        
        if (hasDateRange) {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            if (hasSearch) {
                stockIns = stockInRepository.findByStockInDateBetweenAndSearch(
                    start, end, search.trim(), pageable);
            } else {
                stockIns = stockInRepository.findByStockInDateBetween(start, end, pageable);
            }
        } else if (hasSearch) {
            stockIns = stockInRepository.searchStockIns(search.trim(), pageable);
        } else {
            stockIns = stockInRepository.findAllByOrderByStockInDateDesc(pageable);
        }
        
        model.addAttribute("stockIns", stockIns);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", stockIns.getTotalPages());
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "stock-in/list";
    }

    @GetMapping("/new")
    public String newStockIn(Model model) {
        StockIn stockIn = new StockIn();
        stockIn.setStockInDate(LocalDate.now());
        stockIn.setReceivedDate(LocalDate.now());
        stockIn.setPaymentStatus("PAID");
        stockIn.setPaymentMethod("CASH");
        model.addAttribute("stockIn", stockIn);
        model.addAttribute("suppliers", supplierRepository.findByIsActiveTrue());
        return "stock-in/form";
    }
    
    @GetMapping("/edit/{id}")
    public String editStockIn(@PathVariable Long id, Model model) {
        StockIn stockIn = stockInRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock In not found: " + id));
        
        // Initialize lazy-loaded collections and related entities
        if (stockIn.getItems() != null && !stockIn.getItems().isEmpty()) {
            stockIn.getItems().forEach(item -> {
                if (item.getProduct() != null) {
                    item.getProduct().getName(); // Initialize product
                }
                if (item.getProductPackage() != null) {
                    item.getProductPackage().getPackageDescription(); // Initialize package
                }
            });
        }
        
        model.addAttribute("stockIn", stockIn);
        model.addAttribute("suppliers", supplierRepository.findByIsActiveTrue());
        return "stock-in/form";
    }
    
    @GetMapping("/api/items/{id}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStockInItems(@PathVariable Long id) {
        StockIn stockIn = stockInRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock In not found: " + id));
        
        List<Map<String, Object>> result = new ArrayList<>();
        if (stockIn.getItems() != null) {
            for (StockInItem item : stockIn.getItems()) {
                // Initialize product to avoid lazy loading issues
                item.getProduct().getName();
                if (item.getProduct().getDosageForm() != null) {
                    item.getProduct().getDosageForm().getName();
                }
                if (item.getProduct().getManufacturer() != null) {
                    item.getProduct().getManufacturer().getName();
                }
                
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productId", item.getProduct().getId());
                
                // Build product display name
                StringBuilder productName = new StringBuilder(item.getProduct().getName());
                if (item.getProduct().getStrength() != null && !item.getProduct().getStrength().isEmpty()) {
                    productName.append(" [").append(item.getProduct().getStrength()).append("]");
                }
                if (item.getProduct().getDosageForm() != null) {
                    productName.append(" (").append(item.getProduct().getDosageForm().getName()).append(")");
                }
                if (item.getProduct().getManufacturer() != null) {
                    productName.append(" - ").append(item.getProduct().getManufacturer().getName());
                }
                itemMap.put("productName", productName.toString());
                
                itemMap.put("packageId", item.getProductPackage() != null ? item.getProductPackage().getId() : null);
                itemMap.put("packageDescription", item.getProductPackage() != null ? item.getProductPackage().getPackageDescription() : null);
                itemMap.put("packageUnitPrice", item.getProductPackage() != null ? item.getProductPackage().getUnitPrice() : null);
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("unitCost", item.getUnitCost());
                itemMap.put("totalCost", item.getTotalCost());
                itemMap.put("sellingPrice", item.getSellingPrice());
                result.add(itemMap);
            }
        }
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    public String viewStockIn(@PathVariable Long id, Model model) {
        StockIn stockIn = stockInRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock In not found: " + id));
        
        // Initialize lazy-loaded collections and related entities
        if (stockIn.getItems() != null && !stockIn.getItems().isEmpty()) {
            // Force initialization of items and their related entities
            stockIn.getItems().forEach(item -> {
                if (item.getProduct() != null) {
                    item.getProduct().getName(); // Initialize product
                    if (item.getProduct().getDosageForm() != null) {
                        item.getProduct().getDosageForm().getName(); // Initialize dosage form
                    }
                    if (item.getProduct().getManufacturer() != null) {
                        item.getProduct().getManufacturer().getName(); // Initialize manufacturer
                    }
                }
                if (item.getProductPackage() != null) {
                    item.getProductPackage().getPackageDescription(); // Initialize package
                }
            });
            
            // Calculate total quantity
            int totalQuantity = stockIn.getItems().stream()
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                    .sum();
            model.addAttribute("totalQuantity", totalQuantity);
        } else {
            model.addAttribute("totalQuantity", 0);
        }
        
        model.addAttribute("stockIn", stockIn);
        return "stock-in/view";
    }
    
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadStockInVoucherPdf(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "attachment") String disposition) {
        try {
            StockIn stockIn = stockInRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock In not found"));
            
            // Initialize supplier and manufacturers to avoid lazy loading issues
            if (stockIn.getSupplier() != null) {
                stockIn.getSupplier().getName(); // Initialize supplier
                if (stockIn.getSupplier().getManufacturers() != null) {
                    stockIn.getSupplier().getManufacturers().size(); // Initialize manufacturers
                }
            }
            
            byte[] pdfBytes = stockInVoucherService.generateStockInVoucherPdf(stockIn);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            String filename = "voucher-" + stockIn.getStockInNumber() + ".pdf";
            if ("inline".equals(disposition)) {
                headers.setContentDispositionFormData("inline", filename);
            } else {
                headers.setContentDispositionFormData("attachment", filename);
            }
            headers.setContentLength(pdfBytes.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
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
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/api/package/{packageId}/unit-price")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePackageUnitPrice(
            @PathVariable Long packageId,
            @RequestParam(required = false) BigDecimal unitPrice) {
        try {
            ProductPackage productPackage = productPackageRepository.findById(packageId)
                    .orElseThrow(() -> new RuntimeException("Package not found: " + packageId));
            
            productPackage.setUnitPrice(unitPrice);
            productPackageRepository.save(productPackage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Package unit price updated successfully");
            response.put("unitPrice", productPackage.getUnitPrice());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating package unit price: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
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
            
            // Handle paid amount based on payment status
            // The paid amount is already bound from the form via @ModelAttribute
            // Only clear it if payment status is not PARTIAL
            if (!"PARTIAL".equals(stockIn.getPaymentStatus())) {
                stockIn.setPaidAmount(null);
            }
            // If PARTIAL, the paid amount from the form is already set, so we keep it as is
            
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
            return "redirect:/stock-in/" + savedStockIn.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving stock in: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/stock-in";
        }
    }
    
    @PostMapping("/update/{id}")
    public String updateStockIn(
            @PathVariable Long id,
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
            StockIn existingStockIn = stockInRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock In not found: " + id));
            
            // Update basic information
            existingStockIn.setStockInNumber(stockIn.getStockInNumber());
            existingStockIn.setStockInDate(stockIn.getStockInDate());
            existingStockIn.setReceivedDate(stockIn.getReceivedDate());
            existingStockIn.setPaymentStatus(stockIn.getPaymentStatus());
            existingStockIn.setPaymentMethod(stockIn.getPaymentMethod());
            existingStockIn.setNotes(stockIn.getNotes());
            existingStockIn.setDiscountAmount(stockIn.getDiscountAmount());
            existingStockIn.setTaxAmount(stockIn.getTaxAmount());
            
            // Load supplier if provided
            if (supplierId != null) {
                existingStockIn.setSupplier(supplierRepository.findById(supplierId).orElse(null));
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
            
            existingStockIn.setTotalAmount(totalAmount);
            
            // Calculate final amount
            BigDecimal discount = existingStockIn.getDiscountAmount() != null ? existingStockIn.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal tax = existingStockIn.getTaxAmount() != null ? existingStockIn.getTaxAmount() : BigDecimal.ZERO;
            existingStockIn.setFinalAmount(totalAmount.subtract(discount).add(tax));
            
            // Handle paid amount based on payment status - set AFTER payment status is set
            if ("PARTIAL".equals(existingStockIn.getPaymentStatus())) {
                // Set paid amount as provided by user
                existingStockIn.setPaidAmount(stockIn.getPaidAmount());
            } else {
                // Clear paid amount if not PARTIAL
                existingStockIn.setPaidAmount(null);
            }
            
            // Clear existing items
            existingStockIn.getItems().clear();
            
            // Save stock in first
            StockIn savedStockIn = stockInRepository.save(existingStockIn);
            
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
            redirectAttributes.addFlashAttribute("success", "Stock In updated successfully!");
            return "redirect:/stock-in/" + savedStockIn.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating stock in: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/stock-in";
        }
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

