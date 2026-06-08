package com.predix.matching.service;

import com.predix.matching.client.MatchingCoreClient;
import com.predix.matching.config.PredixProperties;
import com.predix.matching.domain.entity.OrderEntity;
import com.predix.matching.domain.enums.OrderStatus;
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
public class PendingCancelRecoveryService {

    private final OrderRepository orderRepository;
    private final MatchingCoreClient matchingCoreClient;
    private final OrderMatchPersistenceService orderMatchPersistenceService;
    private final PredixProperties properties;

    @Transactional(readOnly = true)
    public int recoverPendingCancels() {
        int batchSize = properties.getPendingCancel().getBatchSize();
        List<OrderEntity> pending = orderRepository.findByStatusOrderByUpdatedAtAsc(
                OrderStatus.PENDING_CANCEL, PageRequest.of(0, batchSize));

        int recovered = 0;
        for (OrderEntity order : pending) {
            if (retryCancel(order)) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean retryCancel(OrderEntity order) {
        try {
            matchingCoreClient.cancelOrder(order);
            orderMatchPersistenceService.finalizeCancel(order.getId());
            log.info("Recovered pending cancel for orderId={}", order.getId());
            return true;
        } catch (Exception e) {
            log.warn("Pending cancel recovery failed for orderId={}", order.getId(), e);
            return false;
        }
    }
}
