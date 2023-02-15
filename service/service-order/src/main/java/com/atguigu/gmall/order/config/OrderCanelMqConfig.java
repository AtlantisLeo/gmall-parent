package com.atguigu.gmall.order.config;

import com.atguigu.gmall.common.model.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class OrderCanelMqConfig {

    @Bean
    public Queue delayedQueue(){
        return new Queue(MqConst.QUEUE_ORDER_CANCEL,true);
    }
    @Bean
    public CustomExchange delayedExchange(){
        HashMap<String, Object> map = new HashMap<>();
        map.put("x-delayed-type", "direct");
        return new CustomExchange(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,
                "x-delayed-message",true,false,map);
    }
    @Bean
    public Binding delayedBinding(){
        return BindingBuilder.bind(delayedQueue()).to(delayedExchange()).with(MqConst.ROUTING_ORDER_CANCEL).noargs();
    }

}
