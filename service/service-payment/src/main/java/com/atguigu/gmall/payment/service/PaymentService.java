package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

public interface PaymentService {
    void savePaymentInfo(OrderInfo orderInfo, String paymentType);

    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    void paySuccess(String outTradeNo, String name, Map<String, String> paramsMap);
    void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo);

    void closePayment(Long orderId);
}
