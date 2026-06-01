package com.predix.matching.service;

import com.predix.matching.controller.dto.PageResponse;
import com.predix.matching.controller.dto.TradeResponse;
import com.predix.matching.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final TradeRepository tradeRepository;
    private final DtoMapper dtoMapper;

    public PageResponse<TradeResponse> listTrades(String marketId, String outcomeId, int page, int size) {
        return dtoMapper.toPage(tradeRepository.findByFilters(marketId, outcomeId, PageRequest.of(page, size))
                .map(dtoMapper::toTradeResponse));
    }
}
