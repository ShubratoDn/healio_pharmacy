package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory", 
        indexes = {
            @Index(name = "idx_inventory_product", columnList = "product_id"),
            @Index(name = "idx_inventory_expiry", columnList = "expiry_date"),
            @Index(name = "idx_inventory_available", columnList = "available_quantity")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                name = "uk_inventory_product_package_batch_expiry",
                columnNames = {"product_id", "product_package_id", "batch_number", "expiry_date"}
            )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_package_id")
    private ProductPackage productPackage;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity = 0;

    @Column(name = "reorder_level")
    private Integer reorderLevel = 10;

    @Column(name = "max_stock_level")
    private Integer maxStockLevel;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(length = 100)
    private String location;

    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;

    @PrePersist
    @PreUpdate
    private void calculateAvailableQuantity() {
        this.availableQuantity = this.quantity - this.reservedQuantity;
    }
}

