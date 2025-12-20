package com.heal.io.controller;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import com.heal.io.service.SaleService;
import com.heal.io.service.SaleReportService;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleRepository saleRepository;
    private final SaleService saleService;
    private final SaleReportService saleReportService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductPackageRepository productPackageRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String listSales(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Sale> sales;
        
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
            sales = saleRepository.findBySaleDateBetween(start, end, pageable);
        } else if (search != null && !search.isEmpty()) {
            // For now, just get all sales - search filtering can be added via repository query
            // TODO: Implement proper search query in SaleRepository
            sales = saleRepository.findAllByOrderBySaleDateDesc(pageable);
        } else {
            sales = saleRepository.findAllByOrderBySaleDateDesc(pageable);
        }
        
        // Calculate page summary statistics
        BigDecimal pageTotalSales = sales.getContent().stream()
                .map(sale -> sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int pageTotalItems = sales.getContent().stream()
                .mapToInt(sale -> sale.getItems() != null ? sale.getItems().size() : 0)
                .sum();
        
        model.addAttribute("sales", sales);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", sales.getTotalPages());
        model.addAttribute("search", search);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("status", status);
        model.addAttribute("pageTotalSales", pageTotalSales);
        model.addAttribute("pageTotalItems", pageTotalItems);
        return "sales/list";
    }

    @GetMapping("/new")
    public String newSale(Model model) {
        Sale sale = new Sale();
        sale.setSaleDate(LocalDateTime.now());
        sale.setSaleNumber(saleService.generateSaleNumber());
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setDiscountAmount(BigDecimal.ZERO);
        sale.setTaxAmount(BigDecimal.ZERO);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setPaidAmount(BigDecimal.ZERO);
        sale.setChangeAmount(BigDecimal.ZERO);
        sale.setPaymentStatus("PAID");
        sale.setSaleStatus("COMPLETED");
        sale.setPaymentMethod("CASH");
        
        model.addAttribute("sale", sale);
        return "sales/form";
    }

    @GetMapping("/{id}")
    public String viewSale(@PathVariable Long id, Model model) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found"));
        model.addAttribute("sale", sale);
        return "sales/view";
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadSaleInvoicePdf(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "attachment") String disposition) {
        try {
            Sale sale = saleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Sale not found"));
            
            byte[] pdfBytes = saleReportService.generateSaleInvoicePdf(sale);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            String filename = "invoice-" + sale.getSaleNumber() + ".pdf";
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

    @GetMapping("/{id}/edit")
    public String editSale(@PathVariable Long id, Model model) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found"));
        model.addAttribute("sale", sale);
        return "sales/form";
    }

    @PostMapping("/save")
    public String saveSale(
            @ModelAttribute Sale sale,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(required = false) List<Long> packageIds,
            @RequestParam(required = false) List<Long> inventoryIds,
            @RequestParam(required = false) List<Integer> quantities,
            @RequestParam(required = false) List<BigDecimal> unitPrices,
            @RequestParam(required = false) List<BigDecimal> discountAmounts,
            @RequestParam(required = false) List<String> batchNumbers,
            @RequestParam(required = false) List<String> expiryDates,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Load existing sale if editing
            Sale existingSale = null;
            if (sale.getId() != null) {
                existingSale = saleRepository.findById(sale.getId())
                        .orElseThrow(() -> new RuntimeException("Sale not found"));
                // Preserve sale number and other important fields from existing sale
                sale.setSaleNumber(existingSale.getSaleNumber());
                sale.setCreatedAt(existingSale.getCreatedAt());
                sale.setCreatedBy(existingSale.getCreatedBy());
            } else {
                // Generate sale number if not provided (new sale)
                if (sale.getSaleNumber() == null || sale.getSaleNumber().isEmpty()) {
                    sale.setSaleNumber(saleService.generateSaleNumber());
                }
            }
            
            // Set sale date if not provided
            if (sale.getSaleDate() == null) {
                sale.setSaleDate(LocalDateTime.now());
            }
            
            // Initialize items list if null
            if (sale.getItems() == null) {
                sale.setItems(new ArrayList<>());
            }
            
            // Load customer if provided
            if (customerId != null) {
                Customer customer = customerRepository.findById(customerId).orElse(null);
                sale.setCustomer(customer);
                if (customer != null) {
                    sale.setCustomerName(customer.getName());
                    sale.setCustomerPhone(customer.getPhone());
                }
            }
            
            // Set current user as sold by
            if (authentication != null) {
                User currentUser = userRepository.findByUsername(authentication.getName())
                        .orElse(null);
                sale.setSoldBy(currentUser);
            }
            
            // Clear existing items
            sale.getItems().clear();
            
            // Add sale items
            if (productIds != null && !productIds.isEmpty()) {
                for (int idx = 0; idx < productIds.size(); idx++) {
                    final int i = idx;
                    if (productIds.get(i) == null) continue;
                    
                    Product product = productRepository.findById(productIds.get(i))
                            .orElseThrow(() -> new RuntimeException("Product not found: " + productIds.get(i)));
                    
                    Integer quantity = (quantities != null && i < quantities.size()) ? quantities.get(i) : 1;
                    BigDecimal unitPrice = (unitPrices != null && i < unitPrices.size()) ? unitPrices.get(i) : BigDecimal.ZERO;
                    BigDecimal discountAmount = (discountAmounts != null && i < discountAmounts.size()) ? discountAmounts.get(i) : BigDecimal.ZERO;
                    String batchNumber = (batchNumbers != null && i < batchNumbers.size()) ? batchNumbers.get(i) : null;
                    String expiryDateStr = (expiryDates != null && i < expiryDates.size()) ? expiryDates.get(i) : null;
                    
                    if (quantity == null || quantity <= 0) {
                        continue;
                    }
                    
                    ProductPackage productPackage = null;
                    if (packageIds != null && i < packageIds.size() && packageIds.get(i) != null) {
                        productPackage = productPackageRepository.findById(packageIds.get(i)).orElse(null);
                    }
                    
                    Inventory inventory = null;
                    if (inventoryIds != null && i < inventoryIds.size() && inventoryIds.get(i) != null) {
                        inventory = inventoryRepository.findById(inventoryIds.get(i)).orElse(null);
                    }
                    
                    BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity))
                            .subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);
                    
                    SaleItem item = SaleItem.builder()
                            .sale(sale)
                            .product(product)
                            .productPackage(productPackage)
                            .inventory(inventory)
                            .productName(product.getName())
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .discountAmount(discountAmount)
                            .totalPrice(totalPrice)
                            .batchNumber(batchNumber)
                            .expiryDate(expiryDateStr != null && !expiryDateStr.isEmpty() ? 
                                       LocalDate.parse(expiryDateStr) : null)
                            .build();
                    
                    sale.getItems().add(item);
                }
            }
            
            // Calculate totals
            saleService.calculateSaleTotals(sale);
            
            // Update inventory if sale is completed
            if ("COMPLETED".equals(sale.getSaleStatus())) {
                saleService.updateInventoryForSale(sale);
                
                // Calculate profit for each item
                for (SaleItem item : sale.getItems()) {
                    saleService.calculateProfit(item);
                }
            }
            
            saleRepository.save(sale);
            redirectAttributes.addFlashAttribute("success", 
                    sale.getId() != null && existingSale != null ? "Sale updated successfully!" : "Sale saved successfully!");
            return "redirect:/sales/" + sale.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving sale: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/sales/new";
        }
    }

    // API Endpoints for AJAX calls
    
    @GetMapping("/api/customers/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchCustomers(@RequestParam String q) {
        Pageable pageable = PageRequest.of(0, 20);
        List<Customer> customers = customerRepository.searchCustomers(q, pageable).getContent();
        List<Map<String, Object>> result = customers.stream()
                .map(customer -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", customer.getId());
                    map.put("name", customer.getName());
                    map.put("phone", customer.getPhone());
                    map.put("email", customer.getEmail());
                    map.put("address", customer.getAddress());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/products/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchProducts(@RequestParam String q) {
        Pageable pageable = PageRequest.of(0, 20);
        List<Product> products = productRepository.searchProducts(q, pageable).getContent();
        List<Map<String, Object>> result = products.stream()
                .map(product -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", product.getId());
                    map.put("name", product.getName());
                    map.put("category", product.getProductCategory() != null ? product.getProductCategory().getName() : "");
                    map.put("manufacturer", product.getManufacturer() != null ? product.getManufacturer().getName() : "");
                    map.put("generic", product.getGeneric() != null ? product.getGeneric().getName() : "");
                    map.put("strength", product.getStrength() != null ? product.getStrength() : "");
                    map.put("requiresPrescription", product.getRequiresPrescription());
                    
                    // Get available inventory
                    int availableQty = saleService.getAvailableInventory(product.getId(), null);
                    map.put("availableQuantity", availableQty);
                    
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
                    
                    // Get available inventory for this package
                    int availableQty = saleService.getAvailableInventory(productId, pkg.getId());
                    map.put("availableQuantity", availableQty);
                    
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/inventory/{productId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getInventory(@PathVariable Long productId,
                                                                   @RequestParam(required = false) Long packageId) {
        List<Inventory> inventories = inventoryRepository.findByProductId(productId);
        List<Map<String, Object>> result = inventories.stream()
                .filter(inv -> packageId == null || 
                              (inv.getProductPackage() != null && inv.getProductPackage().getId().equals(packageId)))
                .filter(inv -> inv.getAvailableQuantity() > 0)
                .map(inventory -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", inventory.getId());
                    map.put("batchNumber", inventory.getBatchNumber());
                    map.put("expiryDate", inventory.getExpiryDate());
                    map.put("availableQuantity", inventory.getAvailableQuantity());
                    map.put("sellingPrice", inventory.getSellingPrice());
                    map.put("costPrice", inventory.getCostPrice());
                    map.put("packageId", inventory.getProductPackage() != null ? inventory.getProductPackage().getId() : null);
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/check-inventory")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkInventory(
            @RequestParam Long productId,
            @RequestParam(required = false) Long packageId,
            @RequestParam Integer quantity) {
        boolean available = saleService.checkInventoryAvailability(productId, packageId, quantity);
        int availableQty = saleService.getAvailableInventory(productId, packageId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("available", available);
        result.put("availableQuantity", availableQty);
        result.put("requestedQuantity", quantity);
        
        return ResponseEntity.ok(result);
    }
}

