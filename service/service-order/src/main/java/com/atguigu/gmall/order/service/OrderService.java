package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {
    Map<String, Object> trade(String userId);

    Long  submitOrder(OrderInfo orderInfo);

    String getTradeNo(String userId);

    boolean checkTradeCode(String userId, String tradeCodeNo);

    void deleteTradeNo(String userId);

    boolean checkStock(String skuId, String skuNum);

    IPage<OrderInfo> getOrderPage(Page<OrderInfo> orderInfos, String userId);

    void cancelOrder(Long orderInfo,String flag);

    void updateOrderStatus(Long orderId, ProcessStatus closed);

    OrderInfo getOrderInfo(Long orderId);

    void sendWareStock(Long orderId);

    Map initWareOrder(OrderInfo orderInfo);

    List<OrderInfo> orderSplit(String parseLong, String wareSkuMap);
}
