package com.hx.creditcardflow.kafka;

import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.event.ClearingPostedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearingPostedKafkaPublisherTest {

    private static final String TOPIC = "creditcardflow.transaction-events";

    @Mock
    private KafkaTemplate<String, ClearingPostedEvent> kafkaTemplate;

    @Test
    void sendsExpectedTopicKeyAndEvent() {
        ClearingPostedEvent event = event();
        when(kafkaTemplate.send(TOPIC, "CLR-630001", event))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publish(event);

        verify(kafkaTemplate).send(TOPIC, "CLR-630001", event);
    }

    @Test
    void containsAsynchronousSendFailure() {
        ClearingPostedEvent event = event();
        CompletableFuture<SendResult<String, ClearingPostedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(TOPIC, "CLR-630001", event)).thenReturn(failed);

        assertThatCode(() -> publisher().publish(event)).doesNotThrowAnyException();
    }

    @Test
    void containsImmediateSendException() {
        ClearingPostedEvent event = event();
        when(kafkaTemplate.send(TOPIC, "CLR-630001", event))
                .thenThrow(new IllegalStateException("send rejected"));

        assertThatCode(() -> publisher().publish(event)).doesNotThrowAnyException();
    }

    private ClearingPostedKafkaPublisher publisher() {
        return new ClearingPostedKafkaPublisher(kafkaTemplate, TOPIC);
    }

    private static ClearingPostedEvent event() {
        return new ClearingPostedEvent(
                UUID.randomUUID(),
                "CLR-630001",
                "AUTH-630001",
                new BigDecimal("125.75"),
                "USD",
                ClearingStatus.POSTED,
                Instant.parse("2026-08-13T12:00:00Z")
        );
    }
}
