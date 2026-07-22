package com.m4trust.coreapi.integration.messaging;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Small bounded retry budget; exhausted processing failures follow the queue DLX binding. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.messaging.topology", name = "enabled", havingValue = "true")
class AiResultsListenerConfiguration {
    @Bean
    SimpleRabbitListenerContainerFactory aiResultsListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxRetries(2)
                .backOffOptions(250, 2.0, 2_000)
                .recoverer(new RejectAndDontRequeueRecoverer()).build());
        return factory;
    }
}
