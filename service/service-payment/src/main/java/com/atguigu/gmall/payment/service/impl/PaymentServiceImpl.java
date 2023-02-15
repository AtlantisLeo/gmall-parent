package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.model.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;


@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitService rabbitService;

    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        LambdaQueryWrapper<PaymentInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentInfo::getOrderId, orderInfo.getId());
        queryWrapper.eq(PaymentInfo::getPaymentType, paymentType);
        Integer count = paymentInfoMapper.selectCount(queryWrapper);
        if (count>0){
            return;
        }
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setUserId(orderInfo.getUserId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfoMapper.insert(paymentInfo);
    }

    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String name) {
        LambdaQueryWrapper<PaymentInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentInfo::getOutTradeNo, outTradeNo);
        queryWrapper.eq(PaymentInfo::getPaymentType, name);
        return paymentInfoMapper.selectOne(queryWrapper);
    }

    @Override
    public void  paySuccess(String outTradeNo, String paymentType, Map<String, String> paramsMap) {
        //  根据outTradeNo，paymentType 查询
        PaymentInfo paymentInfoQuery = this.getPaymentInfo(outTradeNo, paymentType);
        if (paymentInfoQuery==null){
            return;
        }
        try {
            //  改造一下更新的方法！
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setCallbackTime(new Date());
            paymentInfo.setPaymentStatus(PaymentStatus.PAID.name());
            paymentInfo.setCallbackContent(paramsMap.toString());
            paymentInfo.setTradeNo(paramsMap.get("trade_no"));
            //  查询条件也可以作为更新条件！
            this.updatePaymentInfo(outTradeNo, paymentType, paymentInfo);
        } catch (Exception e) {
            //  删除key
            this.redisTemplate.delete(paramsMap.get("notify_id"));
            e.printStackTrace();
        }
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY, paymentInfoQuery.getOrderId());
    }

    //  更新交易状态记录！
    @Override
    public void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo);
        paymentInfoQueryWrapper.eq("payment_type",name);
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);

    }

    @Override
    public void closePayment(Long orderId) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);
        // 如果当前的交易记录不存在，则不更新交易记录
        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        if (null == count || count.intValue()==0) {
            return;
        }
        // 在关闭支付宝交易之前。还需要关闭paymentInfo
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.CLOSED.name());
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);

    }
}
