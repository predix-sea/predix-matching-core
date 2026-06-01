package com.predix.matching.client.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TxStatusDto {
    String txHash;
    String status;
    int confirmations;
}
