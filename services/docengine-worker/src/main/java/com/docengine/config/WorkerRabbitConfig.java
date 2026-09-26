package com.docengine.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerRabbitConfig {
    public static final String EXCHANGE = "docengine.jobs";
    public static final String QUEUE = "docengine.jobs.queue";
    public static final String ROUTING_KEY = "job.created";

    @Bean
    DirectExchange jobExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue jobQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding jobBinding(Queue jobQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(jobQueue).to(jobExchange).with(ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
