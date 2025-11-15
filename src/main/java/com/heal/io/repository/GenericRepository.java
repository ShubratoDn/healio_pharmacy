package com.heal.io.repository;

import com.heal.io.entity.Generic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenericRepository extends JpaRepository<Generic, Long> {
    Optional<Generic> findByName(String name);
}

