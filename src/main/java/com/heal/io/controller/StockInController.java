package com.heal.io.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import com.heal.io.service.InvoiceProcessingService;
import com.heal.io.service.StockInVoucherService;
import com.heal.io.util.NetworkUtil;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final InvoiceProcessingService invoiceProcessingService;
    
    // Temporary storage for processed products by session ID
    private final Map<String, List<Map<String, Object>>> sessionProductsCache = new HashMap<>();

    @GetMapping
    public String listStockIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
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
            
            // Save old items before clearing to subtract from inventory
            // Initialize items collection to ensure it's loaded
            List<StockInItem> oldItems = new ArrayList<>(existingStockIn.getItems());
            
            // Subtract old quantities from inventory
            for (StockInItem oldItem : oldItems) {
                // Initialize lazy-loaded relationships
                Product product = oldItem.getProduct();
                ProductPackage productPackage = oldItem.getProductPackage();
                
                // Access product name to ensure it's loaded
                if (product != null) {
                    product.getName();
                }
                
                subtractFromInventory(product, productPackage, 
                                     oldItem.getQuantity(), oldItem.getBatchNumber(), 
                                     oldItem.getExpiryDate());
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
    
    private void subtractFromInventory(Product product, ProductPackage productPackage, Integer quantity,
                                      String batchNumber, LocalDate expiryDate) {
        // Try to find existing inventory with same product, package, batch, and expiry
        Optional<Inventory> existingInventory = inventoryRepository.findByProductAndPackageAndBatchAndExpiry(
                product.getId(),
                productPackage != null ? productPackage.getId() : null,
                batchNumber,
                expiryDate
        );
        
        if (existingInventory.isPresent()) {
            Inventory inventory = existingInventory.get();
            int newQuantity = inventory.getQuantity() - quantity;
            
            // Ensure quantity doesn't go negative
            if (newQuantity < 0) {
                newQuantity = 0;
            }
            
            inventory.setQuantity(newQuantity);
            // Recalculate available quantity: available = total - reserved
            inventory.setAvailableQuantity(Math.max(0, newQuantity - inventory.getReservedQuantity()));
            inventory.setLastRestockedAt(LocalDateTime.now());
            
            inventoryRepository.save(inventory);
        }
        // If inventory doesn't exist, there's nothing to subtract (shouldn't happen in normal flow)
    }

    /**
     * Generate QR code for mobile scanning
     */
    @GetMapping("/api/qr-code")
    @ResponseBody
    public ResponseEntity<Map<String, String>> generateQRCode(
            @RequestHeader(value = "Host", required = false) String host,
            HttpServletRequest request) {
        try {
            // Generate a unique session ID for this scan
            String sessionId = UUID.randomUUID().toString();
            
            // Build the full URL for mobile scanning - use local IP instead of localhost
            String scheme = request.getScheme(); // http or https
            int serverPort = request.getServerPort();
            String contextPath = request.getContextPath();
            
            // Get local IP address instead of localhost
            String localIp = NetworkUtil.getLocalIpAddress();
            
            String baseUrl;
            if (serverPort == 80 || serverPort == 443) {
                baseUrl = scheme + "://" + localIp;
            } else {
                baseUrl = scheme + "://" + localIp + ":" + serverPort;
            }
            if (!contextPath.isEmpty() && !contextPath.equals("/")) {
                baseUrl += contextPath;
            }
            
            String mobileUrl = baseUrl + "/stock-in/mobile-scan?sessionId=" + sessionId;
            String qrContent = mobileUrl;
            
            // Generate QR code image
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 300, 300, hints);

            BufferedImage qrImage = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 300, 300);
            graphics.setColor(Color.BLACK);

            for (int i = 0; i < 300; i++) {
                for (int j = 0; j < 300; j++) {
                    if (bitMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, String> response = new HashMap<>();
            response.put("qrCode", "data:image/png;base64," + base64Image);
            response.put("sessionId", sessionId);
            response.put("mobileUrl", mobileUrl);
            response.put("uploadUrl", "/stock-in/api/mobile-upload?sessionId=" + sessionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Error generating QR code: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Process invoice image uploaded from mobile or desktop
     */
    @PostMapping("/api/process-invoice")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> processInvoice(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String sessionId) {
        try {
            if (image.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "No image provided");
                return ResponseEntity.badRequest().body(response);
            }

            // Convert image to base64
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Process image with AI
            List<InvoiceProcessingService.ExtractedProduct> extractedProducts = 
                    invoiceProcessingService.processInvoiceImage(base64Image);

            // Try to match products with database
            List<Map<String, Object>> matchedProducts = new ArrayList<>();
            for (InvoiceProcessingService.ExtractedProduct extracted : extractedProducts) {
                Map<String, Object> productInfo = matchProduct(extracted);
                matchedProducts.add(productInfo);
            }

            // Store processed products in cache for this session
            if (sessionId != null && !sessionId.isEmpty()) {
                System.out.println("Storing " + matchedProducts.size() + " products for sessionId: " + sessionId);
                sessionProductsCache.put(sessionId, matchedProducts);
                System.out.println("Cache now contains sessions: " + sessionProductsCache.keySet());
                // Clean up old sessions after 1 hour (in a real app, use a proper cache with TTL)
            } else {
                System.out.println("WARNING: sessionId is null or empty, products not cached! sessionId=" + sessionId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("products", matchedProducts);
            response.put("sessionId", sessionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error processing invoice: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Mobile endpoint for uploading images
     */
    @GetMapping("/mobile-scan")
    public String mobileScanPage(@RequestParam(required = false) String sessionId, Model model) {
        model.addAttribute("sessionId", sessionId);
        return "stock-in/mobile-scan";
    }

    /**
     * Mobile upload endpoint (same as process-invoice but returns mobile-friendly response)
     */
    @PostMapping("/api/mobile-upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> mobileUpload(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String sessionId) {
        System.out.println("Mobile upload received - sessionId: " + sessionId);
        ResponseEntity<Map<String, Object>> result = processInvoice(image, sessionId);
        
        // Add mobile-specific response data
        if (result.getBody() != null && (Boolean) result.getBody().get("success")) {
            result.getBody().put("message", "Image processed successfully! Products have been added to your stock-in form. You can close this page.");
        }
        
        return result;
    }

    /**
     * Get processed products for a session (used for polling from desktop)
     */
    @GetMapping("/api/session-products/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSessionProducts(@PathVariable String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        System.out.println("Checking session products for sessionId: " + sessionId);
        System.out.println("Cache keys: " + sessionProductsCache.keySet());
        
        List<Map<String, Object>> products = sessionProductsCache.get(sessionId);
        if (products != null && !products.isEmpty()) {
            System.out.println("Found " + products.size() + " products for session: " + sessionId);
            response.put("success", true);
            response.put("products", products);
            // Remove from cache after retrieving (one-time use)
            sessionProductsCache.remove(sessionId);
        } else {
            System.out.println("No products found for session: " + sessionId);
            response.put("success", false);
            response.put("message", "No products found for this session");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Match extracted product with database product
     */
    private Map<String, Object> matchProduct(InvoiceProcessingService.ExtractedProduct extracted) {
        Map<String, Object> result = new HashMap<>();
        result.put("extracted", extracted);
        result.put("matched", false);
        result.put("productId", null);
        result.put("productName", extracted.getProductName());
        result.put("strength", extracted.getStrength());
        result.put("dosageForm", extracted.getDosageForm());
        result.put("manufacturer", extracted.getManufacturer());
        result.put("quantity", extracted.getQuantity());
        result.put("unitPrice", extracted.getUnitPrice());
        result.put("totalPrice", extracted.getTotalPrice());
        result.put("packageDescription", extracted.getPackageDescription());

        // Try to find matching product in database
        if (extracted.getProductName() != null && !extracted.getProductName().trim().isEmpty()) {
            String searchTerm = extracted.getProductName().trim();
            
            // Search for products
            Page<Product> products = productRepository.searchProducts(searchTerm, PageRequest.of(0, 5));
            
            if (products.hasContent()) {
                Product matchedProduct = products.getContent().get(0);
                result.put("matched", true);
                result.put("productId", matchedProduct.getId());
                
                // Get packages for this product
                List<ProductPackage> packages = productPackageRepository.findByProductId(matchedProduct.getId());
                List<Map<String, Object>> packageList = packages.stream()
                        .map(pkg -> {
                            Map<String, Object> pkgMap = new HashMap<>();
                            pkgMap.put("id", pkg.getId());
                            pkgMap.put("description", pkg.getPackageDescription());
                            pkgMap.put("unitPrice", pkg.getUnitPrice());
                            return pkgMap;
                        })
                        .collect(Collectors.toList());
                result.put("packages", packageList);
            }
        }

        return result;
    }
}

