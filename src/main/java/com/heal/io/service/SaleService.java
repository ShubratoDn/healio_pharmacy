package com.heal.io.service;

import com.heal.io.entity.*;
import com.heal.io.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

    private final SaleRepository saleRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ProductPackageRepository productPackageRepository;

    /**
     * Generate a unique sale number
     */
    public String generateSaleNumber() {
        String prefix = "SALE-";
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timePart = String.valueOf(System.currentTimeMillis()).substring(7);
        return prefix + datePart + "-" + timePart;
    }

    /**
     * Calculate sale totals
     */
    public void calculateSaleTotals(Sale sale) {
        BigDecimal subtotal = BigDecimal.ZERO;
        
        // Calculate subtotal from items
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            for (SaleItem item : sale.getItems()) {
                if (item.getTotalPrice() != null) {
                    subtotal = subtotal.add(item.getTotalPrice());
                }
            }
        }
        
        sale.setSubtotal(subtotal);
        
        // Calculate discount
        BigDecimal discountAmount = sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO;
        if (sale.getDiscountPercentage() != null && sale.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = subtotal.multiply(sale.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            sale.setDiscountAmount(discountAmount);
        }
        
        // Calculate tax
        BigDecimal taxAmount = sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO;
        
        // Calculate total
        BigDecimal total = subtotal.subtract(discountAmount).add(taxAmount);
        sale.setTotalAmount(total);
        
        // Calculate change
        BigDecimal paidAmount = sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal change = paidAmount.subtract(total);
        sale.setChangeAmount(change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO);
        
        // Only auto-calculate payment status if it's not already set (preserve user's selection)
        if (sale.getPaymentStatus() == null || sale.getPaymentStatus().isEmpty()) {
            if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
                sale.setPaymentStatus("PENDING");
            } else if (paidAmount.compareTo(total) < 0) {
                sale.setPaymentStatus("PARTIAL");
            } else {
                sale.setPaymentStatus("PAID");
            }
        }
    }

    /**
     * Check if inventory is available for sale
     */
    public boolean checkInventoryAvailability(Long productId, Long packageId, Integer quantity) {
        List<Inventory> inventories = inventoryRepository.findByProductId(productId);
        
        if (inventories.isEmpty()) {
            return false;
        }
        
        // If package is specified, check specific inventory
        if (packageId != null) {
            Optional<Inventory> inventory = inventories.stream()
                    .filter(inv -> inv.getProductPackage() != null && 
                                  inv.getProductPackage().getId().equals(packageId) &&
                                  inv.getAvailableQuantity() >= quantity)
                    .findFirst();
            return inventory.isPresent();
        }
        
        // Otherwise, check total available quantity across all packages
        int totalAvailable = inventories.stream()
                .mapToInt(Inventory::getAvailableQuantity)
                .sum();
        
        return totalAvailable >= quantity;
    }

    /**
     * Get available inventory for a product
     */
    public int getAvailableInventory(Long productId, Long packageId) {
        List<Inventory> inventories = inventoryRepository.findByProductId(productId);
        
        if (packageId != null) {
            return inventories.stream()
                    .filter(inv -> inv.getProductPackage() != null && 
                                  inv.getProductPackage().getId().equals(packageId))
                    .mapToInt(Inventory::getAvailableQuantity)
                    .sum();
        }
        
        return inventories.stream()
                .mapToInt(Inventory::getAvailableQuantity)
                .sum();
    }

    /**
     * Update inventory when a sale is completed
     */
    @Transactional
    public void updateInventoryForSale(Sale sale) {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            return;
        }
        
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            
            Product product = item.getProduct();
            ProductPackage productPackage = item.getProductPackage();
            Integer quantity = item.getQuantity();
            
            // Find available inventory (FIFO - First In First Out)
            List<Inventory> inventories = inventoryRepository.findByProductId(product.getId());
            
            int remainingQuantity = quantity;
            
            for (Inventory inventory : inventories) {
                if (remainingQuantity <= 0) {
                    break;
                }
                
                // Match package if specified
                if (productPackage != null) {
                    if (inventory.getProductPackage() == null || 
                        !inventory.getProductPackage().getId().equals(productPackage.getId())) {
                        continue;
                    }
                }
                
                // Match batch and expiry if specified in sale item
                if (item.getBatchNumber() != null && !item.getBatchNumber().isEmpty()) {
                    if (!item.getBatchNumber().equals(inventory.getBatchNumber())) {
                        continue;
                    }
                }
                
                if (item.getExpiryDate() != null) {
                    if (!item.getExpiryDate().equals(inventory.getExpiryDate())) {
                        continue;
                    }
                }
                
                int available = inventory.getAvailableQuantity();
                if (available > 0) {
                    int toDeduct = Math.min(remainingQuantity, available);
                    inventory.setQuantity(inventory.getQuantity() - toDeduct);
                    inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - toDeduct));
                    remainingQuantity -= toDeduct;
                    
                    // Set inventory reference in sale item
                    item.setInventory(inventory);
                    
                    inventoryRepository.save(inventory);
                }
            }
            
            if (remainingQuantity > 0) {
                log.warn("Insufficient inventory for product {}: requested {}, available {}", 
                        product.getId(), quantity, quantity - remainingQuantity);
            }
        }
    }

    /**
     * Calculate profit for sale items
     */
    public void calculateProfit(SaleItem item) {
        if (item.getProduct() == null || item.getInventory() == null) {
            return;
        }
        
        BigDecimal costPrice = item.getInventory().getCostPrice();
        if (costPrice == null) {
            costPrice = BigDecimal.ZERO;
        }
        
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 0);
        
        BigDecimal totalCost = costPrice.multiply(quantity);
        BigDecimal totalPrice = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
        
        BigDecimal profit = totalPrice.subtract(totalCost);
        item.setCostPrice(costPrice);
        item.setProfitAmount(profit);
    }
}
