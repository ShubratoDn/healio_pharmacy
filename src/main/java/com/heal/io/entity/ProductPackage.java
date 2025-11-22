package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_package", indexes = {
        @Index(name = "idx_product_package_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPackage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "package_description", nullable = false, length = 500)
    private String packageDescription;

    @Column(name = "package_size", length = 200)
    private String packageSize;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity_per_package")
    private Integer quantityPerPackage;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}

