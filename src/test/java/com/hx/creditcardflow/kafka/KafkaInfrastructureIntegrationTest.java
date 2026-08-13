package com.hx.creditcardflow.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaInfrastructureIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private NewTopic transactionEventsTopic;

    @Test
    void kafkaInfrastructureAndTransactionEventsTopicAreConfigured() {
        assertThat(applicationContext.getBean(KafkaTemplate.class)).isNotNull();
        assertThat(applicationContext.getBean(KafkaAdmin.class)).isNotNull();
        assertThat(transactionEventsTopic.name())
                .isEqualTo("creditcardflow.transaction-events");
        assertThat(transactionEventsTopic.numPartitions()).isEqualTo(1);
        assertThat(transactionEventsTopic.replicationFactor()).isEqualTo((short) 1);
    }
}
