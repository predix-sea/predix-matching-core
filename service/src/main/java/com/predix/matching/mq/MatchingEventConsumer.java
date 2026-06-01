package com.predix.matching.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!h2")
@RequiredArgsConstructor
public class MatchingEventConsumer {

    @RabbitListener(queues = "${predix.mq.events-queue}")
    public void consume(MatchingEventMessage message) {
        log.info("Received matching event type={} refId={} messageId={} correlationId={}",
                message.getEventType(), message.getRefId(), message.getMessageId(), message.getCorrelationId());
    }
}
