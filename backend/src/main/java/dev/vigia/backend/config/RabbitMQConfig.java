package dev.vigia.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ALERTAS_EXCHANGE = "alertas.exchange";

    public static final String ALERTAS_QUEUE = "alertas.queue";

    @Bean
    public FanoutExchange alertasExchange() {
        return new FanoutExchange(ALERTAS_EXCHANGE);
    }

    @Bean
    public Queue alertasQueue() {
        return new Queue(ALERTAS_QUEUE, true);
    }

    @Bean
    public Binding alertasBinding(Queue alertasQueue, FanoutExchange alertasExchange) {
        return BindingBuilder.bind(alertasQueue).to(alertasExchange);
    }

    @Bean
    @SuppressWarnings("null")
    public Jackson2JsonMessageConverter messageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @SuppressWarnings("null")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
