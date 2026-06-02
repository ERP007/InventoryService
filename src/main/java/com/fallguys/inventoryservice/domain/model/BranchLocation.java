package com.fallguys.inventoryservice.domain.model;

import lombok.Getter;

@Getter
public class BranchLocation {

    private final Long id;
    private String name; // ex) 강남 1지점, 서울 본사 창고 등

    public BranchLocation(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
