package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Autowired
    private RedisTemplate redisTemplate;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Value("${authUrls.url}")
    private String authUrls;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取请求报文
        ServerHttpRequest request = exchange.getRequest();
        //获取请求的url
        String path = request.getURI().getPath();

        //判断url是否为不可访问的url
        if (antPathMatcher.match("/**/inner/**", path)) {
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }
        //获取用户Id
        String userId = getUserId(request);

        //token可能被盗用
        if ("-1".equals(userId)) {
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        //api接口，异步请求，校验用户必须登录
        if (antPathMatcher.match("/api/**/auth/**", path)) {
            if (StringUtils.isEmpty(userId)) {
                ServerHttpResponse response = exchange.getResponse();
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }
        // 验证url是否为需要登录的url
        for (String authUrl : authUrls.split(",")) {
            // 当前的url包含登录的控制器域名，但是用户Id 为空！
            if (path.indexOf(authUrl) != -1 && StringUtils.isEmpty(userId)) {
                ServerHttpResponse response = exchange.getResponse();
                //303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://www.gmall.com/login.html?originUrl=" + request.getURI());
                // 重定向到登录
                return response.setComplete();
            }
        }

        String userTempId=getUserTempId(request);
        // 将userId 传递给后端
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)) {
            if (!StringUtils.isEmpty(userId)){
                request.mutate().header("userId", userId).build();
            }
            if (!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId", userTempId).build();
            }
            return chain.filter(exchange.mutate().request(request).build());
        }

        return chain.filter(exchange);

    }

    private String getUserTempId(ServerHttpRequest request) {
        List<String> list = request.getHeaders().get("userTempId");
        if (!CollectionUtils.isEmpty(list)){
            return list.get(0);
        }else {
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if (cookie != null){
                return URLDecoder.decode(cookie.getValue());
            }
        }
        return null;
    }

    //获取用户id
    private String getUserId(ServerHttpRequest request) {
        String token = "";
        //通过请求头获取
        List<String> list = request.getHeaders().get("token");
        if (!CollectionUtils.isEmpty(list)){
            token = list.get(0);
        }else {
            //通过cookie获取
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            HttpCookie cookie = cookies.getFirst("token");
            if (cookie!=null){
                token = URLDecoder.decode(cookie.getValue());
            }
        }
        if (!StringUtils.isEmpty(token)){
            String userStr = (String) redisTemplate.opsForValue().get("user:login:" + token);
            JSONObject userJson = JSONObject.parseObject(userStr);
            String ip = userJson.getString("ip");
            String curIp = IpUtil.getGatwayIpAddress(request);
            if (curIp.equals(ip)){
                //通过，返回用户Id
                return userJson.getString("userId");
            }else {
                //ip与redis中存储的ip不一致，返回-1
                return "-1";
            }
        }
        //不存在返回空
        return "";
    }
   //返回方法
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        Result<Object> result = Result.build(null, resultCodeEnum);
        byte[] bytes = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(wrap));
    }

}
