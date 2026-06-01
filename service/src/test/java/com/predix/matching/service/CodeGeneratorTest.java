package com.predix.matching.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesDistinctCodes() {
        assertThat(generator.orderCode()).startsWith("ORD");
        assertThat(generator.tradeCode()).startsWith("TRD");
        assertThat(generator.taskCode()).startsWith("TSK");
        assertThat(generator.orderCode()).isNotEqualTo(generator.orderCode());
    }
}
