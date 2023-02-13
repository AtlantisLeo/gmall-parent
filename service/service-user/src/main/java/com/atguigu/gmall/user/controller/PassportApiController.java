package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {

    @Autowired
    private UserService userService;
    @Autowired
    private RedisTemplate redisTemplate;


    @PostMapping("/login")
    public Result login(@RequestBody UserInfo userInfo, HttpServletRequest request) {
        UserInfo user = userService.login(userInfo);
        if (user!=null){
            String token = UUID.randomUUID().toString().replaceAll("-", "");
            JSONObject userJson = new JSONObject();
            userJson.put("userId", user.getId().toString());
            userJson.put("ip", IpUtil.getIpAddress(request));
            //将用户Id，token，ip存入redis中
            redisTemplate.opsForValue().set(RedisConst.USER_LOGIN_KEY_PREFIX+token,userJson.toJSONString(),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);
            HashMap<String, Object> map = new HashMap<>();
            map.put("nickName", user.getNickName());
            map.put("token", token);
            //返回前端需要的参数 用户名和token
            return Result.ok(map);
        }else{
            return Result.fail().message("用户名或密码错误");
        }

    }

    @GetMapping("/logout")
    public Result logout(@RequestHeader(value = "token") String token){
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX+token);
        return Result.ok();
    }

}
