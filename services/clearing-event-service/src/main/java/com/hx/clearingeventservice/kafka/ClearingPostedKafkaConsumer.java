package com.hx.clearingeventservice.kafka;

import com.hx.clearingeventservice.event.ClearingPostedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "clearing-event-service.kafka.consumer.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ClearingPostedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClearingPostedKafkaConsumer.class);

    @KafkaListener(
            topics = "${clearing-event-service.kafka.topic.transaction-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ClearingPostedEvent event) {
        log.info(
                "Consumed clearing event: eventId={}, clearingReference={}, status={}",
                event.eventId(), event.clearingReference(), event.status()
        );
    }
}
