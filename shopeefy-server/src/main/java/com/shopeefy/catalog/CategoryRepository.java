package com.shopeefy.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c.id from Category c where c.name = :name and c.level = 3")
    List<Long> findLeafIdsByName(String name);

    @Query("select c from Category c where c.name = :name and c.level = :level and "
            + "((:parent is null and c.parent is null) or c.parent = :parent)")
    Optional<Category> find(String name, int level, Category parent);
}
