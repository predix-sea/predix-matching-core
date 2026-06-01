package com.predix.matching.support;

import com.predix.matching.client.impl.MockMarketSchemaClient;
import com.predix.matching.domain.enums.MarketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")
public abstract class H2IntegrationTestBase {

    @Autowired
    protected MockMarketSchemaClient marketSchemaClient;

    @BeforeEach
    void baseSetup() {
        marketSchemaClient.setMarketStatus("mkt-h2", MarketStatus.OPEN);
    }

}
