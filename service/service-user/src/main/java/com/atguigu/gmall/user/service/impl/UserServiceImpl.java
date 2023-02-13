package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserAddressMapper;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;


@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {
        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        String pwd = DigestUtils.md5DigestAsHex(userInfo.getPasswd().getBytes());
        queryWrapper.eq(UserInfo::getPasswd,pwd);
        queryWrapper.eq(UserInfo::getLoginName,userInfo.getLoginName());
        UserInfo one = userInfoMapper.selectOne(queryWrapper);
        return one;
    }

    @Override
    public List<UserAddress> findUserAddressListByUserId(String userId) {
        LambdaQueryWrapper<UserAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAddress::getUserId, userId);
        return userAddressMapper.selectList(wrapper);
    }

}
