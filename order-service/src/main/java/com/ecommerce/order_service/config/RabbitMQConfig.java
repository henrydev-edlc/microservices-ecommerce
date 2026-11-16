package com.ecommerce.order_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "order-events";

    @Bean
    public MessageConverter messageConverter(){
        return new JacksonJsonMessageConverter();

    }

    @Bean
    public TopicExchange orderEventsExchange(){
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue orderConfirmedQueue(){
        return new Queue("order-confirmed-queue", true);
    }

    @Bean
    public Binding confirmedBinding(Queue orderConfirmedQueue, TopicExchange ordersEventsExchange){
        return BindingBuilder.bind(orderConfirmedQueue).to(ordersEventsExchange).with("order.confirmed");
    }

    @Bean
    public Queue orderCancelQueue(){
        return new Queue("order-cancelled-queue", true);
    }

    @Bean
    public Binding cancelBinding(Queue orderCancelQueue, TopicExchange ordersEventsExchange){
        return BindingBuilder.bind(orderCancelQueue).to(ordersEventsExchange).with("order.cancelled");
    }

}
