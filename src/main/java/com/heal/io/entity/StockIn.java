package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_in", indexes = {
        @Index(name = "idx_stock_in_date", columnList = "stock_in_date"),
        @Index(name = "idx_stock_in_supplier", columnList = "supplier_id"),
        @Index(name = "idx_stock_in_number", columnList = "stock_in_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIn extends BaseEntity {

    @Column(name = "stock_in_number", nullable = false, unique = true, length = 50)
    private String stockInNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "stock_in_date", nullable = false)
    private LocalDate stockInDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "PENDING";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inserted_by")
    private User insertedBy;

    @OneToMany(mappedBy = "stockIn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockInItem> items = new ArrayList<>();
}

