package com.hx.creditcardflow.kafka;

import com.hx.creditcardflow.clearing.event.ClearingPostedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        name = "creditcardflow.kafka.publishing.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ClearingPostedKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClearingPostedKafkaPublisher.class);

    private final KafkaTemplate<String, ClearingPostedEvent> kafkaTemplate;
    private final String topicName;

    public ClearingPostedKafkaPublisher(
            KafkaTemplate<String, ClearingPostedEvent> kafkaTemplate,
            @Value("${creditcardflow.kafka.topic.transaction-events}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ClearingPostedEvent event) {
        try {
            kafkaTemplate.send(topicName, event.clearingReference(), event)
                    .whenComplete((result, failure) -> {
                        if (failure == null) {
                            log.info("Published clearing event: {}", event.clearingReference());
                        } else {
                            log.error("Failed to publish clearing event: {}", event.clearingReference(), failure);
                        }
                    });
        } catch (RuntimeException failure) {
            log.error("Failed to send clearing event: {}", event.clearingReference(), failure);
        }
    }
}
