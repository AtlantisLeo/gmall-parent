package com.atguigu.gmall.order.controller;

import com.alibaba.nacos.common.utils.StringUtils;
import com.atguigu.gmall.cart.client.CartFeignController;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/order/")
public class OrderApiController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private CartFeignController cartFeignController;
    @Autowired
    private ThreadPoolExecutor executor;

    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        Map<String, Object> map = orderService.trade(userId);
        return Result.ok(map);
    }

    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        String tradeNo = request.getParameter("tradeNo");
        boolean b = orderService.checkTradeCode(userId, tradeNo);
        if (!b){
            return Result.fail().message("不能重复提交订单");
        }
        List<CompletableFuture> futureList  = new ArrayList<>();
        List<String> errorsList = new ArrayList<>();
        for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
            CompletableFuture<Void> checkStockFuture = CompletableFuture.runAsync(() -> {
                boolean flag = orderService.checkStock(String.valueOf(orderDetail.getSkuId()), String.valueOf(orderDetail.getSkuNum()));
                if (!flag) {
                    errorsList.add(orderDetail.getSkuName() + "-库存不足");
                }
            }, executor);
            futureList.add(checkStockFuture);
            CompletableFuture<Void> checkSkuPriceFuture = CompletableFuture.runAsync(() -> {
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                    cartFeignController.getCartCheckedList(userId);
                    errorsList.add(orderDetail.getSkuName() + "-价格有变动");
                }
            }, executor);
            futureList.add(checkSkuPriceFuture);
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        if (errorsList.size()>0){
            return Result.fail().message(StringUtils.join(errorsList, ","));
        }
        orderInfo.setUserId(Long.parseLong(userId));
        Long orderId = orderService.submitOrder(orderInfo);
        orderService.deleteTradeNo(userId);
        return Result.ok(orderId);
    }


    @GetMapping("auth/{page}/{limit}")
    public Result<IPage<OrderInfo>> getOrderPage( @PathVariable Long page,
                                          @PathVariable Long limit,
                                          HttpServletRequest request){

        String userId = AuthContextHolder.getUserId(request);
        Page<OrderInfo> orderInfos = new Page<>(page, limit);
        IPage<OrderInfo> infoIPage = orderService.getOrderPage(orderInfos,userId);
        infoIPage.getRecords().stream().forEach(item->{
            item.setOrderStatusName(OrderStatus.getStatusNameByStatus(item.getOrderStatus()));
        });
        return Result.ok(infoIPage);
    }


}
