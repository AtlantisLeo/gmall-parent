package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.model.user.UserInfo;

import java.util.List;

public interface UserService {
    UserInfo login(UserInfo userInfo);

    List<UserAddress> findUserAddressListByUserId(String userId);
}
