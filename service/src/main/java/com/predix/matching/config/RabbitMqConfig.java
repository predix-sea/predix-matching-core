package com.predix.matching.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!h2")
public class RabbitMqConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public TopicExchange matchingExchange(PredixProperties properties) {
        return new TopicExchange(properties.getMq().getExchange(), true, false);
    }

    @Bean
    public Queue eventsQueue(PredixProperties properties) {
        return QueueBuilder.durable(properties.getMq().getEventsQueue()).build();
    }

    @Bean
    public Queue executionQueue(PredixProperties properties) {
        return QueueBuilder.durable(properties.getMq().getExecutionQueue()).build();
    }

    @Bean
    public Queue dlq(PredixProperties properties) {
        return QueueBuilder.durable(properties.getMq().getDlq()).build();
    }

    @Bean
    public Binding eventsBinding(Queue eventsQueue, TopicExchange matchingExchange, PredixProperties properties) {
        return BindingBuilder.bind(eventsQueue).to(matchingExchange).with("events.#");
    }

    @Bean
    public Binding executionBinding(Queue executionQueue, TopicExchange matchingExchange, PredixProperties properties) {
        return BindingBuilder.bind(executionQueue).to(matchingExchange).with("execution.#");
    }

    @Bean
    public Binding dlqBinding(Queue dlq, TopicExchange matchingExchange) {
        return BindingBuilder.bind(dlq).to(matchingExchange).with("dlq.#");
    }
}
