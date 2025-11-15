package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "supplier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(length = 20)
    private String phone;

    @Column
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "supplier_manufacturer",
            joinColumns = @JoinColumn(name = "supplier_id"),
            inverseJoinColumns = @JoinColumn(name = "manufacturer_id")
    )
    @Builder.Default
    private Set<Manufacturer> manufacturers = new HashSet<>();
}

