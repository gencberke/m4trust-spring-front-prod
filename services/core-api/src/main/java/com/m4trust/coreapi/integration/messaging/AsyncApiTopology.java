package com.m4trust.coreapi.integration.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Local broker declarations matching contracts/asyncapi/m4trust-ai-v1.yaml. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(prefix = "app.messaging.topology", name = "enabled", havingValue = "true")
class AsyncApiTopology {

    static final String COMMANDS_EXCHANGE = "m4trust.ai.commands";
    static final String EVENTS_EXCHANGE = "m4trust.ai.events";
    static final String DEAD_LETTER_EXCHANGE = "m4trust.ai.dead-letter";
    static final String DOCUMENT_EXTRACTION_QUEUE = "m4trust.ai.document-extraction.v1";
    static final String VIDEO_ANALYSIS_QUEUE = "m4trust.ai.video-analysis.v1";
    static final String CANCELLATION_QUEUE = "m4trust.ai.cancellation.v1";
    static final String RESULTS_QUEUE = "m4trust.core.ai-results.v1";
    static final String DEAD_LETTER_QUEUE = "m4trust.ai.dead-letter.v1";

    @Bean
    TopicExchange commandsExchange() { return new TopicExchange(COMMANDS_EXCHANGE, true, false); }

    @Bean
    TopicExchange eventsExchange() { return new TopicExchange(EVENTS_EXCHANGE, true, false); }

    @Bean
    TopicExchange deadLetterExchange() { return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean
    Queue documentExtractionQueue() { return durableWorkQueue(DOCUMENT_EXTRACTION_QUEUE); }

    @Bean
    Queue videoAnalysisQueue() { return durableWorkQueue(VIDEO_ANALYSIS_QUEUE); }

    @Bean
    Queue cancellationQueue() { return durableWorkQueue(CANCELLATION_QUEUE); }

    @Bean
    Queue resultsQueue() { return durableWorkQueue(RESULTS_QUEUE); }

    @Bean
    Queue deadLetterQueue() { return QueueBuilder.durable(DEAD_LETTER_QUEUE).build(); }

    @Bean
    Binding documentExtractionBinding(@Qualifier("documentExtractionQueue") Queue queue,
            @Qualifier("commandsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.document-extraction.requested.v1");
    }

    @Bean
    Binding videoAnalysisBinding(@Qualifier("videoAnalysisQueue") Queue queue,
            @Qualifier("commandsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.video-analysis.requested.v1");
    }

    @Bean
    Binding cancellationBinding(@Qualifier("cancellationQueue") Queue queue,
            @Qualifier("commandsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.job.cancel.requested.v1");
    }

    @Bean
    Binding documentCompletedBinding(@Qualifier("resultsQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.document-extraction.completed.v1");
    }

    @Bean
    Binding documentFailedBinding(@Qualifier("resultsQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.document-extraction.failed.v1");
    }

    @Bean
    Binding videoCompletedBinding(@Qualifier("resultsQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.video-analysis.completed.v1");
    }

    @Bean
    Binding videoFailedBinding(@Qualifier("resultsQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("ai.video-analysis.failed.v1");
    }

    @Bean
    Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue queue,
            @Qualifier("deadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("#");
    }

    private static Queue durableWorkQueue(String name) {
        return QueueBuilder.durable(name).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }
}
