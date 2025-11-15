package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medicine_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;
}

