package com.atguigu.gmall.order.listerner;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.StringUtils;
import com.atguigu.gmall.common.model.MqConst;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class orderListener {


    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void cancelOrder(Long orderId, Message message, Channel channel){
        try {
            if (orderId!=null){
                OrderInfo orderInfo = orderService.getById(orderId);
                if (orderInfo!=null && "UNPAID".equals(orderInfo.getOrderStatus()) && "UNPAID".equals(orderInfo.getProcessStatus())){
                    PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());

                    if(paymentInfo!=null && "UNPAID".equals(paymentInfo.getPaymentStatus())){

                        //  查看是否有交易记录！
                        Boolean flag = paymentFeignClient.checkPayment(orderId);
                        //  判断
                        if (flag){
                            //  flag = true , 有交易记录
                            //  调用关闭接口！ 扫码未支付这样才能关闭成功！
                            Boolean result = paymentFeignClient.closePay(orderId);
                            //  判断
                            if (result){
                                //  result = true; 关闭成功！未付款！需要关闭orderInfo， paymentInfo，Alipay
                                orderService.cancelOrder(orderId,"2");
                            }else {
                                //  result = false; 表示付款！
                                //  说明已经付款了！ 正常付款成功都会走异步通知！
                            }
                        }else {
                            //  没有交易记录，不需要关闭支付！  需要关闭orderInfo， paymentInfo
                            orderService.cancelOrder(orderId,"2");
                        }

                    }else {
                        //  只关闭订单orderInfo！
                        orderService.cancelOrder(orderId,"1");
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void updateOrderStatus(Long orderId, Message message, Channel channel){
        try {
            if (orderId!=null){
                OrderInfo orderInfo = orderService.getById(orderId);
                if (orderInfo!=null && "UNPAID".equals(orderInfo.getProcessStatus())){
                    orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
                    orderService.sendWareStock(orderId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson, Message message, Channel channel) throws IOException {
        if (!StringUtils.isEmpty(msgJson)){
            Map<String,Object> map = JSON.parseObject(msgJson, Map.class);
            String orderId = (String)map.get("orderId");
            String status = (String)map.get("status");
            if ("DEDUCTED".equals(status)){
                // 减库存成功！ 修改订单状态为已支付
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.WAITING_DELEVER);
            }else {
                /**
                 * TODO 扣减库存失败，通知平台人员
                 */
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.STOCK_EXCEPTION);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}
