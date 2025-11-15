package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dosage_form")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DosageForm extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;
}

