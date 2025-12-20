package com.heal.io.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StockInVoucherDTO {
    private String stockInNumber;
    private LocalDate stockInDate;
    private LocalDate receivedDate;
    private String supplierName;
    private String manufacturerNames;
    private String supplierPhone;
    private String supplierEmail;
    private String supplierAddress;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String receivedByName;
    private String insertedByName;
    private String notes;
    private List<StockInItemVoucherDTO> items;

    @Data
    @Builder
    public static class StockInItemVoucherDTO {
        private String productName;
        private String packageDescription;
        private String strength;
        private Integer quantity;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
        private BigDecimal sellingPrice;
        private String batchNumber;
        private String expiryDate;
        private String location;
    }
}

