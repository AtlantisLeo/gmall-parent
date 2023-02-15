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

        rabbitService.sendDelayMsg(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);
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
        // 通过orderId 获取orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);
        // 将orderInfo中部分数据转换为Map
        Map map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    //  将orderInfo中部分数据转换为Map
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
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
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
                // 属性拷贝
                BeanUtils.copyProperties(orderInfo, subOrderInfo);
                // 防止主键冲突
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(orderInfo.getId());
                // 赋值仓库Id
                subOrderInfo.setWareId(wareId);

                // 计算子订单的金额: 必须有订单明细
                // 获取到子订单明细
                // 声明一个集合来存储子订单明细
                ArrayList<OrderDetail> orderDetails = new ArrayList<>();

                List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
                StringBuilder stringBuilder = new StringBuilder();
                // 表示主主订单明细中获取到子订单的明细
                if (orderDetailList != null && orderDetailList.size() > 0) {
                    for (OrderDetail orderDetail : orderDetailList) {
                        // 获取子订单明细的商品Id
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId().longValue()) {
                                // 将订单明细添加到集合
                                orderDetails.add(orderDetail);
                                stringBuilder.append(orderDetail.getSkuName()+"");
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(orderDetails);
                // 计算总金额
                subOrderInfo.sumTotalAmount();
                subOrderInfo.setTradeBody(stringBuilder.toString());
                // 保存子订单
                submitOrder(subOrderInfo);
                // 将子订单添加到集合中！
                orderInfoArrayList.add(subOrderInfo);
                // 修改原始订单的状态
                updateOrderStatus(orderInfo.getId(), ProcessStatus.SPLIT);

            }
        }
        return orderInfoArrayList;
    }


}
