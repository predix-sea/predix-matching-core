package com.predix.matching.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String orderCode() {
        return "ORD" + randomSuffix(9);
    }

    public String tradeCode() {
        return "TRD" + randomSuffix(9);
    }

    public String taskCode() {
        return "TSK" + randomSuffix(9);
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
