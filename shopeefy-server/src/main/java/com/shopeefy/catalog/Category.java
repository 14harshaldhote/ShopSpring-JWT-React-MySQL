package com.shopeefy.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Three-level category tree, e.g. Women > Clothing > women_dress. */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    private int level;

    protected Category() {
    }

    public Category(String name, Category parent, int level) {
        this.name = name;
        this.parent = parent;
        this.level = level;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Category getParent() { return parent; }
    public int getLevel() { return level; }
}
