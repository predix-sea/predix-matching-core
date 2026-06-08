package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
import com.predix.matching.idempotency.IdempotencyService;
import com.predix.matching.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingMatchRecoveryService {

    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final OrderMatchPersistenceService orderMatchPersistenceService;
    private final IdempotencyService idempotencyService;
    private final PredixProperties properties;

    @Transactional(readOnly = true)
    public int recoverPendingMatches() {
        int batchSize = properties.getPendingMatch().getBatchSize();
        List<OrderEntity> pending = orderRepository.findByStatusOrderByUpdatedAtAsc(
                OrderStatus.PENDING_MATCH, PageRequest.of(0, batchSize));

        int recovered = 0;
        for (OrderEntity order : pending) {
            if (retryFinalize(order)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean retryFinalize(OrderEntity order) {
        try {
            String idempotencyKey = idempotencyService.buildOrderKey(order.getUserId(), order.getClientOrderId());
            var matchResult = matchingCoreClient.submitOrder(order);
            if (matchResult.isRejected()) {
                orderMatchPersistenceService.finalizeRejected(order.getId(), matchResult, idempotencyKey);
                return true;
            }
            orderMatchPersistenceService.finalizeMatch(order.getId(), matchResult, idempotencyKey);
            log.info("Recovered pending match for orderId={}", order.getId());
            return true;
        } catch (Exception e) {
            log.warn("Pending match recovery failed for orderId={}", order.getId(), e);
            return false;
        }
    }
}
