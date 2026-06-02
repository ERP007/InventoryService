package com.fallguys.inventoryservice.domain.model;

public enum UserRole {
    ADMIN,
    HQ_MANAGER,
    HQ_STAFF,
    BRANCH_MANAGER,
    BRANCH_STAFF;

    public boolean canWrite() {
        return this == ADMIN || this == HQ_MANAGER;
    }

    public boolean isBranchUser() {
        return this == BRANCH_MANAGER || this == BRANCH_STAFF;
    }
}
