package com.atguigu.gmall.cart.service.impl;


import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String, CartInfo> hashOps = redisTemplate.boundHashOps(cartKey);
        CartInfo cartInfo = new CartInfo();
        //判断购物车是否含有此条sku数据
        if (hashOps.hasKey(skuId.toString())){
            cartInfo = hashOps.get(skuId.toString());
            cartInfo.setSkuNum(cartInfo.getSkuNum()+skuNum);
            cartInfo.setUpdateTime(new Date());
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            cartInfo.setIsChecked(1);
        }else {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo.setSkuId(skuId);
            cartInfo.setSkuNum(skuNum);
            cartInfo.setCreateTime(new Date());
            cartInfo.setUpdateTime(new Date());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
        }
        hashOps.put(skuId.toString(), cartInfo);
    }

    @Override
    public List<CartInfo> cartList(String userId, String userTempId) {
        List<CartInfo> tempList = null;
        if (!StringUtils.isEmpty(userTempId)){
            String tempKey = getCartKey(userTempId);
            BoundHashOperations<String,String,CartInfo> hashOps = redisTemplate.boundHashOps(tempKey);
            tempList = hashOps.values();
        }

        if (StringUtils.isEmpty(userId) && !CollectionUtils.isEmpty(tempList)){
            tempList.sort((o1,o2)->{
                return DateUtil.truncatedCompareTo(o2.getUpdateTime(), o1.getUpdateTime(), Calendar.SECOND);
            });
            return tempList;
        }

        String loginKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> loginOps =  redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(tempList) && !StringUtils.isEmpty(userId)){
            tempList.stream().forEach(cartInfo -> {
                if (loginOps.hasKey(cartInfo.getSkuId().toString())){
                    CartInfo loginInfo = loginOps.get(cartInfo.getSkuId().toString());
                    loginInfo.setSkuNum(loginInfo.getSkuNum()+cartInfo.getSkuNum());
                    loginInfo.setUpdateTime(new Date());
                    if (cartInfo.getIsChecked().intValue()==1){
                        loginInfo.setIsChecked(1);
                    }
                    loginOps.put(cartInfo.getSkuId().toString(),loginInfo);
                }else {
                    cartInfo.setUserId(userId);
                    cartInfo.setUpdateTime(new Date());
                    loginOps.put(cartInfo.getSkuId().toString(),cartInfo);
                }
            });
            redisTemplate.delete(getCartKey(userTempId));
        }

        List<CartInfo> loginList = null;
        loginList = loginOps.values();
        if (CollectionUtils.isEmpty(loginList)){
            loginList = new ArrayList<>();
        }
        if (!CollectionUtils.isEmpty(loginList)){
            loginList.sort((o1,o2)->{
                return DateUtil.truncatedCompareTo(o2.getUpdateTime(), o1.getUpdateTime(), Calendar.SECOND);
            });
        }
        return loginList;
    }

    @Override
    public void checkCart(String userId, Long skuId, Integer isChecked) {
        BoundHashOperations<String,String,CartInfo> boundHashOps = redisTemplate.boundHashOps(getCartKey(userId));
        if (boundHashOps.hasKey(skuId.toString())){
            CartInfo cartInfo = boundHashOps.get(skuId.toString());
            cartInfo.setIsChecked(isChecked);
            cartInfo.setUpdateTime(new Date());
            boundHashOps.put(skuId.toString(),cartInfo);
        }
    }

    @Override
    public void deleteCart(String userId, Long skuId) {
        redisTemplate.boundHashOps(getCartKey(userId)).delete(skuId.toString());
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        BoundHashOperations<String,String,CartInfo> boundHashOps = redisTemplate.boundHashOps(getCartKey(userId));
        List<CartInfo> cartInfoList = null;
        if (boundHashOps!=null){
            cartInfoList = boundHashOps.values().stream().filter(cartInfo -> {
                cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
                boundHashOps.put(cartInfo.getSkuId().toString(), cartInfo);
                return cartInfo.getIsChecked().intValue() == 1;
            }).collect(Collectors.toList());
        }
        return cartInfoList;
    }


    private String getCartKey(String userId) {
        return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }


}
