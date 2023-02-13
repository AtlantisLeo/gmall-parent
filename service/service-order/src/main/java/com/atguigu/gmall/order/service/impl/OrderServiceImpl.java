package com.atguigu.gmall.order.service.impl;


import com.atguigu.gmall.cart.client.CartFeignController;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.user.client.UserFeignController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private UserFeignController userFeignController;
    @Autowired
    private CartFeignController cartFeignController;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Value("${ware.url}")
    private  String wareUrl;

    @Override
    public Map<String, Object> trade(String userId) {
        List<UserAddress> userAddressList = userFeignController.findUserAddressListByUserId(userId);
        List<CartInfo> cartCheckedList = cartFeignController.getCartCheckedList(userId);
        List<OrderDetail> detailArrayList =  cartCheckedList.stream().map(cartInfo -> {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            return orderDetail;
        }).collect(Collectors.toList());

        int size =0;
        for (OrderDetail orderDetail : detailArrayList) {
            size=size+orderDetail.getSkuNum();
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();
        String tradeNo = this.getTradeNo(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailArrayList);
        // 保存总金额
        result.put("totalNum", size);
        result.put("totalAmount", orderInfo.getTotalAmount());
        result.put("tradeNo", tradeNo);
        
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long  submitOrder(OrderInfo orderInfo) {
        orderInfo.sumTotalAmount();
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setCreateTime(new Date());
        // 定义为1天
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        // 获取订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer tradeBody = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            tradeBody.append(orderDetail.getSkuName()+" ");
        }
        if (tradeBody.toString().length()>100){
            orderInfo.setTradeBody(tradeBody.toString().substring(0,100));
        }else {
            orderInfo.setTradeBody(tradeBody.toString());
        }
        orderInfoMapper.insert(orderInfo);
        BoundHashOperations boundHashOps = redisTemplate.boundHashOps(RedisConst.USER_KEY_PREFIX + orderInfo.getUserId() + RedisConst.USER_CART_KEY_SUFFIX);
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
            boundHashOps.delete(orderDetail.getSkuId().toString());
        }
        return orderInfo.getId();
    }

    /**
     *
     * @param userId
     * @return 生成订单流水号，防止重复提交订单
     */
    @Override
    public String getTradeNo(String userId) {
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String tradeNo = "GMALL" + UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);
        return tradeNo;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String o = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeCodeNo.equals(o);
    }

    @Override
    public void deleteTradeNo(String userId) {
        String tradeNoKey = "user:" + userId + ":tradeCode";
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(String skuId, String skuNum) {
        String s = HttpClientUtil.doGet(wareUrl + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(s);
    }

    @Override
    public IPage<OrderInfo> getOrderPage(Page<OrderInfo> orderInfos, String userId) {
        return orderInfoMapper.getOrderPage(orderInfos,userId);
    }
}
