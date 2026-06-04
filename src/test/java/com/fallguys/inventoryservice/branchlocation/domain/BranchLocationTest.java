package com.fallguys.inventoryservice.branchlocation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BranchLocationTest {

    @Test
    void create는_식별자_없이_지점명으로_생성한다() {
        BranchLocation branchLocation = BranchLocation.create("수원 영통지점");

        assertThat(branchLocation.getId()).isNull();
        assertThat(branchLocation.getName()).isEqualTo("수원 영통지점");
    }

    @Test
    void create는_지점명이_null이면_예외를_던진다() {
        assertThatThrownBy(() -> BranchLocation.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create는_지점명이_공백뿐이면_예외를_던진다() {
        assertThatThrownBy(() -> BranchLocation.create("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of는_식별자와_지점명으로_복원한다() {
        BranchLocation branchLocation = BranchLocation.of(9L, "수원 영통지점");

        assertThat(branchLocation.getId()).isEqualTo(9L);
        assertThat(branchLocation.getName()).isEqualTo("수원 영통지점");
    }
}
