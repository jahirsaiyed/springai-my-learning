package com.example.ecommerce.repository;

import com.example.ecommerce.entity.EcomProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EcomProductRepository extends JpaRepository<EcomProduct, String> {

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<EcomProduct> searchByCategory(String query, Pageable pageable);

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) = LOWER(:category)
        """)
    List<EcomProduct> findByCategory(String category, Pageable pageable);

    @Query("""
        SELECT p FROM EcomProduct p
        JOIN p.category pc
        WHERE LOWER(pc.categoryNameEn) LIKE LOWER(CONCAT('%', :query, '%'))
        AND LOWER(pc.categoryNameEn) = LOWER(:category)
        """)
    List<EcomProduct> searchByCategoryFiltered(String query, String category, Pageable pageable);
}
