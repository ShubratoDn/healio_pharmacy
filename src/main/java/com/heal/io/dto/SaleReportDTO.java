package com.heal.io.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReportDTO {
    private String saleNumber;
    private LocalDateTime saleDate;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discountPercentage;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal changeAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String soldByName;
    private String notes;
    
    @Builder.Default
    private List<SaleItemReportDTO> items = new ArrayList<>();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleItemReportDTO {
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountAmount;
        private BigDecimal totalPrice;
        private String batchNumber;
        private String expiryDate;
    }
}


