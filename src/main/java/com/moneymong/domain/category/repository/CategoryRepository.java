package com.moneymong.domain.category.repository;

import com.moneymong.domain.category.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByAgencyId(Long agencyId);

    Optional<Category> findByAgencyIdAndName(Long agencyId, String name);
}
