package com.atguigu.gmall.payment.service;

public interface AlipayService {
    String createaliPay(Long orderId);

    boolean refund(Long orderId);

    Boolean closePay(Long orderId);

    boolean checkPayment(Long orderId);
}
