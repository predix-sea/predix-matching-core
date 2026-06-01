package com.predix.matching.client.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CtfSubmitResult {
    String txHash;
    String status;
}
