package com.heal.io.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "generic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Generic extends BaseEntity {

    @Column(nullable = false, unique = true, length = 500)
    private String name;

    @Column(unique = true, length = 500)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;
}

