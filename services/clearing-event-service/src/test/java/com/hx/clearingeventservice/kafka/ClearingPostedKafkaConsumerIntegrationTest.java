package com.hx.clearingeventservice.kafka;

import com.hx.clearingeventservice.event.ClearingPostedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "clearing-event-service.kafka.consumer.enabled=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "creditcardflow.transaction-events"
)
@DirtiesContext
class ClearingPostedKafkaConsumerIntegrationTest {

    private static final String JSON = """
            {
              "eventId":"73df179a-4aa4-4e2b-bec2-c2083ba479f0",
              "clearingReference":"CLR-630004",
              "authorizationReference":"AUTH-630004",
              "amount":87.40,
              "currency":"USD",
              "status":"POSTED",
              "occurredAt":"2026-08-13T18:00:00Z"
            }
            """;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoSpyBean
    private ClearingPostedKafkaConsumer consumer;

    @Test
    void receivesHeaderlessJsonAsLocalClearingPostedEvent() {
        kafkaTemplate.send("creditcardflow.transaction-events", "CLR-630004", JSON);

        verify(consumer, timeout(10_000)).consume(argThat(this::isExpectedLocalEvent));
    }

    private boolean isExpectedLocalEvent(ClearingPostedEvent event) {
        return event != null
                && event.getClass().equals(ClearingPostedEvent.class)
                && event.eventId().equals(UUID.fromString("73df179a-4aa4-4e2b-bec2-c2083ba479f0"))
                && event.clearingReference().equals("CLR-630004")
                && event.authorizationReference().equals("AUTH-630004")
                && event.amount().equals(new BigDecimal("87.40"))
                && event.currency().equals("USD")
                && event.status().equals("POSTED")
                && event.occurredAt().equals(Instant.parse("2026-08-13T18:00:00Z"));
    }
}
