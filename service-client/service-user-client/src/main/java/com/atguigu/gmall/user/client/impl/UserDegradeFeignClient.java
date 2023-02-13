package com.atguigu.gmall.user.client.impl;

import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.user.client.UserFeignController;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserDegradeFeignClient implements UserFeignController {
    @Override
    public List<UserAddress> findUserAddressListByUserId(String userId) {
        return null;
    }
}
