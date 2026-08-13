package com.hx.creditcardflow.kafka;

import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.event.ClearingPostedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "creditcardflow.kafka.publishing.enabled=true",
        "creditcardflow.kafka.consumer.enabled=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "creditcardflow.transaction-events"
)
@DirtiesContext
class ClearingPostedKafkaRoundTripIntegrationTest {

    @Autowired
    private ClearingPostedKafkaPublisher publisher;

    @MockitoSpyBean
    private ClearingPostedKafkaConsumer consumer;

    @Test
    void productionPublisherRoundTripsEventToProductionConsumer() {
        ClearingPostedEvent event = new ClearingPostedEvent(
                UUID.fromString("73df179a-4aa4-4e2b-bec2-c2083ba479f0"),
                "CLR-630003",
                "AUTH-630003",
                new BigDecimal("87.40"),
                "USD",
                ClearingStatus.POSTED,
                Instant.parse("2026-08-13T18:00:00Z")
        );

        publisher.publish(event);

        verify(consumer, timeout(10_000)).consume(event);
    }
}
