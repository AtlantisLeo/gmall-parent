package com.atguigu.gmall.cart.client.impl;

import com.atguigu.gmall.cart.client.CartFeignController;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CartDegradeFeignClient implements CartFeignController {
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        return null;
    }
}
