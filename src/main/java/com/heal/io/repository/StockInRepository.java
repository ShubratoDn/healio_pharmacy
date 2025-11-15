package com.heal.io.repository;

import com.heal.io.entity.StockIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockInRepository extends JpaRepository<StockIn, Long> {
    Optional<StockIn> findByStockInNumber(String stockInNumber);
    Page<StockIn> findAllByOrderByStockInDateDesc(Pageable pageable);
}

