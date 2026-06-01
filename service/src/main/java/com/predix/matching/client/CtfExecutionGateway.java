package com.predix.matching.client;

import com.predix.matching.client.dto.CtfSubmitResult;
import com.predix.matching.client.dto.TxStatusDto;

import java.util.Map;

public interface CtfExecutionGateway {

    CtfSubmitResult submitTrade(Map<String, Object> payload);

    CtfSubmitResult cancelOrderOnChain(Map<String, Object> payload);

    TxStatusDto queryTxStatus(String txHash);
}
