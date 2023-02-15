package com.atguigu.gmall.common.config;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.model.GmallCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MQProducerAckConfig implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 修饰一个非静态的void（）方法,在服务器加载Servlet的时候运行，
     * 并且只会被服务器执行一次,在构造函数之后执行，init（）方法之前执行。
     */
    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnCallback(this);
    }


    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息发送成功：" + JSON.toJSONString(correlationData));
        } else {
            log.info("消息发送失败：" + cause + " 数据：" + JSON.toJSONString(correlationData));
            this.retrySendMsg(correlationData);
        }

    }

    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        System.out.println("消息主体: " + new String(message.getBody()));
        System.out.println("应答码: " + replyCode);
        System.out.println("描述：" + replyText);
        System.out.println("消息使用的交换器 exchange : " + exchange);
        System.out.println("消息使用的路由键 routing : " + routingKey);
        String correlationDataId = (String) message.getMessageProperties().getHeaders().get("spring_returned_message_correlation");
        String correlationData = (String) redisTemplate.opsForValue().get(correlationDataId);
        this.retrySendMsg(JSON.parseObject(correlationData, GmallCorrelationData.class));
    }


    private void retrySendMsg(CorrelationData correlationData) {
        GmallCorrelationData gmallCorrelationData = (GmallCorrelationData) correlationData;
        int retryCount = gmallCorrelationData.getRetryCount();
        if (retryCount>3){
            log.error("重试次数已到，发送消息失败:"+JSON.toJSONString(gmallCorrelationData));
        }else {
            retryCount+=1;
            gmallCorrelationData.setRetryCount(retryCount);
            redisTemplate.opsForValue().set(gmallCorrelationData.getId(), JSON.toJSONString(gmallCorrelationData),10, TimeUnit.MINUTES);
            if (gmallCorrelationData.isDelay()){
                rabbitTemplate.convertAndSend(gmallCorrelationData.getExchange(), gmallCorrelationData.getRoutingKey(),
                        gmallCorrelationData.getMessage(),message->{
                            message.getMessageProperties().setDelay(gmallCorrelationData.getDelayTime()*1000);
                            return message;
                        },gmallCorrelationData);
            }else {
                rabbitTemplate.convertAndSend(gmallCorrelationData.getExchange(), gmallCorrelationData.getRoutingKey(),
                        gmallCorrelationData.getMessage(),gmallCorrelationData);
            }
        }

    }
}
