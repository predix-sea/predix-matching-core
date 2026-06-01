package com.predix.matching.client.impl;

import com.predix.matching.client.CtfExecutionGateway;
import com.predix.matching.client.dto.CtfSubmitResult;
import com.predix.matching.client.dto.TxStatusDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockCtfExecutionGateway implements CtfExecutionGateway {

    private final Map<String, TxStatusDto> txStore = new ConcurrentHashMap<>();

    @Override
    public CtfSubmitResult submitTrade(Map<String, Object> payload) {
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "");
        txStore.put(txHash, TxStatusDto.builder().txHash(txHash).status("PENDING").confirmations(0).build());
        return CtfSubmitResult.builder().txHash(txHash).status("SUBMITTED").build();
    }

    @Override
    public CtfSubmitResult cancelOrderOnChain(Map<String, Object> payload) {
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "");
        return CtfSubmitResult.builder().txHash(txHash).status("SUBMITTED").build();
    }

    @Override
    public TxStatusDto queryTxStatus(String txHash) {
        return txStore.getOrDefault(txHash,
                TxStatusDto.builder().txHash(txHash).status("UNKNOWN").confirmations(0).build());
    }
}
