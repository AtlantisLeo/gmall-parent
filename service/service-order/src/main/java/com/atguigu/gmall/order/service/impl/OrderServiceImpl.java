package com.atguigu.gmall.order.service.impl;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignController;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.model.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper,OrderInfo> implements OrderService {

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
    @Autowired
    private RabbitService rabbitService;


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
        // ???????????????
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
        // ?????????1???
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        // ??????????????????
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

        rabbitService.sendDelayMsg(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);
        return orderInfo.getId();
    }

    /**
     *
     * @param userId
     * @return ????????????????????????????????????????????????
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

    @Override
    public void cancelOrder(Long orderId,String flag) {
        this.updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
        }
    }

    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(wrapper);
        orderInfo.setOrderDetailList(orderDetails);
        return orderInfo;
    }

    @Override
    public void sendWareStock(Long orderId) {
        this.updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);
        String wareJson = initWareOrder(orderId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);
    }


    private String initWareOrder(Long orderId) {
        // ??????orderId ??????orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);
        // ???orderInfo????????????????????????Map
        Map map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    //  ???orderInfo????????????????????????Map
    @Override
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// ??????Id ????????????????????????????????????
        ArrayList<Map> mapArrayList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderInfo> orderSplit(String orderId, String wareSkuMap) {
        ArrayList<OrderInfo> orderInfoArrayList = new ArrayList<>();
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        OrderInfo orderInfo = getOrderInfo(Long.parseLong(orderId));
        if (maps != null) {
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");

                List<String> skuIds = (List<String>) map.get("skuIds");

                OrderInfo subOrderInfo = new OrderInfo();
                // ????????????
                BeanUtils.copyProperties(orderInfo, subOrderInfo);
                // ??????????????????
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(orderInfo.getId());
                // ????????????Id
                subOrderInfo.setWareId(wareId);

                // ????????????????????????: ?????????????????????
                // ????????????????????????
                // ??????????????????????????????????????????
                ArrayList<OrderDetail> orderDetails = new ArrayList<>();

                List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
                StringBuilder stringBuilder = new StringBuilder();
                // ??????????????????????????????????????????????????????
                if (orderDetailList != null && orderDetailList.size() > 0) {
                    for (OrderDetail orderDetail : orderDetailList) {
                        // ??????????????????????????????Id
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId().longValue()) {
                                // ??????????????????????????????
                                orderDetails.add(orderDetail);
                                stringBuilder.append(orderDetail.getSkuName()+"");
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(orderDetails);
                // ???????????????
                subOrderInfo.sumTotalAmount();
                subOrderInfo.setTradeBody(stringBuilder.toString());
                // ???????????????
                submitOrder(subOrderInfo);
                // ?????????????????????????????????
                orderInfoArrayList.add(subOrderInfo);
                // ???????????????????????????
                updateOrderStatus(orderInfo.getId(), ProcessStatus.SPLIT);

            }
        }
        return orderInfoArrayList;
    }


}
