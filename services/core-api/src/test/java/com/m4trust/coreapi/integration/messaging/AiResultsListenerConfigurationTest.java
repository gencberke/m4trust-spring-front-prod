package com.m4trust.coreapi.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.test.util.ReflectionTestUtils;

class AiResultsListenerConfigurationTest {

    @Test
    void listenerUsesBoundedRetryAndNeverRequeuesAfterExhaustion() throws Throwable {
        SimpleRabbitListenerContainerFactory factory = new AiResultsListenerConfiguration()
                .aiResultsListenerFactory(mock(ConnectionFactory.class));

        assertFalse((Boolean) ReflectionTestUtils.getField(factory,
                "defaultRequeueRejected"));

        Advice[] adviceChain = (Advice[]) ReflectionTestUtils.getField(factory, "adviceChain");
        assertEquals(1, adviceChain.length);
        MethodInterceptor retry = (MethodInterceptor) adviceChain[0];
        MethodInvocation invocation = mock(MethodInvocation.class);
        AtomicInteger attempts = new AtomicInteger();
        when(invocation.getArguments()).thenReturn(new Object[] {new Object(),
                new Message(new byte[0])});
        when(invocation.proceed()).thenAnswer(call -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("processing failed");
        });

        ListenerExecutionFailedException exhausted = assertThrows(
                ListenerExecutionFailedException.class, () -> retry.invoke(invocation));
        assertInstanceOf(AmqpRejectAndDontRequeueException.class, exhausted.getCause());
        assertEquals(3, attempts.get());
    }
}
