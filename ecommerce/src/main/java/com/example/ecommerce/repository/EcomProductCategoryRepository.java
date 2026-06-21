package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomProductCategoryRepository extends JpaRepository<EcomProductCategory, String> {

    @Query("""
        SELECT pc.categoryNameEn, COUNT(p) FROM EcomProduct p
        JOIN p.category pc
        GROUP BY pc.categoryNameEn
        ORDER BY pc.categoryNameEn
        """)
    List<Object[]> findAllWithProductCounts();
}
