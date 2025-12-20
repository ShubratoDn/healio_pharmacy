package com.heal.io.controller;

import com.heal.io.entity.Inventory;
import com.heal.io.entity.Product;
import com.heal.io.entity.ProductPackage;
import com.heal.io.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    @GetMapping
    public String listInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            Model model) {
        
        // Get all active inventory entries
        List<Inventory> allInventories = inventoryRepository.findAllActiveInventories();
        
        // Filter by search if provided
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            allInventories = allInventories.stream()
                    .filter(inv -> {
                        Product product = inv.getProduct();
                        if (product == null) return false;
                        
                        boolean matchesName = product.getName() != null && 
                                             product.getName().toLowerCase().contains(searchLower);
                        boolean matchesManufacturer = product.getManufacturer() != null && 
                                                     product.getManufacturer().getName() != null &&
                                                     product.getManufacturer().getName().toLowerCase().contains(searchLower);
                        boolean matchesGeneric = product.getGeneric() != null && 
                                                product.getGeneric().getName() != null &&
                                                product.getGeneric().getName().toLowerCase().contains(searchLower);
                        
                        return matchesName || matchesManufacturer || matchesGeneric;
                    })
                    .collect(Collectors.toList());
        }
        
        // Group inventory by product and package
        Map<String, List<Inventory>> groupedInventories = allInventories.stream()
                .collect(Collectors.groupingBy(inv -> {
                    Long productId = inv.getProduct() != null ? inv.getProduct().getId() : 0L;
                    Long packageId = inv.getProductPackage() != null ? inv.getProductPackage().getId() : 0L;
                    return productId + "_" + packageId;
                }));
        
        // Create inventory data for each product-package combination
        List<Map<String, Object>> inventoryData = groupedInventories.entrySet().stream()
                .map(entry -> {
                    List<Inventory> inventories = entry.getValue();
                    if (inventories.isEmpty()) return null;
                    
                    Inventory firstInv = inventories.get(0);
                    Product product = firstInv.getProduct();
                    ProductPackage productPackage = firstInv.getProductPackage();
                    
                    // Aggregate quantities for this product-package combination
                    int totalQuantity = inventories.stream()
                            .mapToInt(Inventory::getQuantity)
                            .sum();
                    
                    int totalAvailable = inventories.stream()
                            .mapToInt(Inventory::getAvailableQuantity)
                            .sum();
                    
                    int totalReserved = inventories.stream()
                            .mapToInt(Inventory::getReservedQuantity)
                            .sum();
                    
                    // Check if low stock
                    boolean isLowStock = false;
                    if (productPackage != null && productPackage.getLowStock() != null) {
                        isLowStock = totalAvailable <= productPackage.getLowStock();
                    } else {
                        Integer reorderLevel = firstInv.getReorderLevel() != null ? firstInv.getReorderLevel() : 10;
                        isLowStock = totalAvailable <= reorderLevel;
                    }
                    
                    Map<String, Object> data = new HashMap<>();
                    data.put("product", product);
                    data.put("productPackage", productPackage);
                    data.put("totalQuantity", totalQuantity);
                    data.put("totalAvailable", totalAvailable);
                    data.put("totalReserved", totalReserved);
                    data.put("isLowStock", isLowStock);
                    data.put("inventoryCount", inventories.size());
                    return data;
                })
                .filter(data -> data != null)
                .collect(Collectors.toList());
        
        // Manual pagination
        int totalElements = inventoryData.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        int end = Math.min(start + size, totalElements);
        
        List<Map<String, Object>> paginatedData = start < totalElements 
            ? inventoryData.subList(start, end)
            : List.of();
        
        // Create a dummy Page object for pagination display
        Page<Map<String, Object>> pageResult = new PageImpl<>(paginatedData, PageRequest.of(page, size), totalElements);
        
        model.addAttribute("inventoryData", paginatedData);
        model.addAttribute("products", pageResult);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("search", search);
        
        return "inventory/list";
    }
}

