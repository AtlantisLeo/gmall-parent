package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
public class OrderController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 确认订单
     * @param model
     * @return
     */
    @GetMapping("trade.html")
    public String trade(Model model) {
        Result<Map<String, Object>> result = orderFeignClient.trade();
        model.addAllAttributes(result.getData());
        return "order/trade";
    }

    @GetMapping("myOrder.html")
    public String myOrder() {
        return "order/myOrder";
    }

    @RequestMapping("pay.html")
    public String pay(Long orderId, Model model) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        model.addAttribute(orderInfo);
        return "payment/pay";
    }

}
