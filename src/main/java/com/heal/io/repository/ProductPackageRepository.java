package com.heal.io.repository;

import com.heal.io.entity.ProductPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPackageRepository extends JpaRepository<ProductPackage, Long> {
    List<ProductPackage> findByProductId(Long productId);
}

