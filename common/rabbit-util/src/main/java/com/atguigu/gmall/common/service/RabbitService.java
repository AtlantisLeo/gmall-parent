package com.atguigu.gmall.common.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.model.GmallCorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RabbitService {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate redisTemplate;

    public boolean sendMessage(String exchange,String routingKey,Object msg){
        GmallCorrelationData correlationData = new GmallCorrelationData();
        String correlationDataId = UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setExchange(exchange);
        correlationData.setId(correlationDataId);
        correlationData.setRoutingKey(routingKey);
        correlationData.setMessage(msg);
        redisTemplate.opsForValue().set(correlationDataId, JSON.toJSONString(correlationData),10, TimeUnit.MINUTES);
        rabbitTemplate.convertAndSend(exchange,routingKey,msg,correlationData);
        return true;
    }

    public boolean sendDelayMsg(String exchange,String routingKey,Object msg,int delayTime){
        GmallCorrelationData correlationData = new GmallCorrelationData();
        String correlationDataId = UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setExchange(exchange);
        correlationData.setId(correlationDataId);
        correlationData.setRoutingKey(routingKey);
        correlationData.setMessage(msg);
        correlationData.setDelayTime(delayTime);
        correlationData.setDelay(true);
        redisTemplate.opsForValue().set(correlationDataId, JSON.toJSONString(correlationData),10, TimeUnit.MINUTES);
        rabbitTemplate.convertAndSend(exchange,routingKey,msg,message->{
            message.getMessageProperties().setDelay(delayTime*1000);
            return message;
        },correlationData);
        return true;
    }
}
