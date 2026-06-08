package com.predix.matching.testsupport.inmemory.model;

import com.predix.matching.engine.model.TradeFill;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MatchResult {
    BookOrder incomingOrder;
    @Singular
    List<TradeFill> fills;
    boolean fullyFilled;
    boolean rejected;
    String rejectReason;
}
