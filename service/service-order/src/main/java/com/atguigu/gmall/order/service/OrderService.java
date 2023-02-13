package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

public interface OrderService {
    Map<String, Object> trade(String userId);

    Long  submitOrder(OrderInfo orderInfo);

    String getTradeNo(String userId);

    boolean checkTradeCode(String userId, String tradeCodeNo);

    void deleteTradeNo(String userId);

    boolean checkStock(String skuId, String skuNum);

    IPage<OrderInfo> getOrderPage(Page<OrderInfo> orderInfos, String userId);
}
