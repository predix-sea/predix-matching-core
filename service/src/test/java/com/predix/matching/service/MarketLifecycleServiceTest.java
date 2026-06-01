package com.predix.matching.service;

import com.predix.matching.client.MarketSchemaClient;
import com.predix.matching.client.dto.MarketInfoDto;
import com.predix.matching.domain.enums.MarketStatus;
import com.predix.matching.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketLifecycleServiceTest {

    @Mock
    private MarketSchemaClient marketSchemaClient;

    @InjectMocks
    private MarketLifecycleService service;

    @Test
    void rejectsOrderWhenMarketClosed() {
        when(marketSchemaClient.getMarket("m1")).thenReturn(MarketInfoDto.builder()
                .marketId("m1")
                .status(MarketStatus.CLOSED)
                .outcomeIds(List.of("yes"))
                .build());
        when(marketSchemaClient.isTradingAllowed(MarketStatus.CLOSED)).thenReturn(false);

        assertThatThrownBy(() -> service.validateForPlaceOrder("m1", "yes"))
                .isInstanceOf(BusinessException.class);
    }
}
