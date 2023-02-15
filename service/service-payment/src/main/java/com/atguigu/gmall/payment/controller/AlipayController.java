package com.atguigu.gmall.payment.controller;


import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/api/payment/alipay/")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable Long orderId){
        String from = alipayService.createaliPay(orderId);
        return from;
    }

    @RequestMapping("callback/return")
    public String callBack() {
        // 同步回调给用户展示信息
        return "redirect:" + AlipayConfig.return_order_url;
    }


    @PostMapping("callback/notify")
    @ResponseBody
    public String callbackNotify(@RequestParam Map<String, String> paramsMap){
        System.out.println(paramsMap);
        boolean signVerified = false;
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //  获取异步通知的参数中的订单号！
        String outTradeNo = paramsMap.get("out_trade_no");
        //  获取异步通知的参数中的订单总金额！
        String totalAmount = paramsMap.get("total_amount");
        //  获取异步通知的参数中的appId！
        String appId = paramsMap.get("app_id");
        //  获取异步通知的参数中的交易状态！
        String tradeStatus = paramsMap.get("trade_status");
        //  根据outTradeNo 查询数据！

        //  保证异步通知的幂等性！notify_id
        String notifyId = paramsMap.get("notify_id");

        //  true:
        if(signVerified){
            PaymentInfo paymentinfo = this.paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
            if (paymentinfo!=null && new BigDecimal("0.01").compareTo(new BigDecimal(totalAmount))==0
                    && AlipayConfig.app_id.equals(appId)){

                //  说明已经处理过了！
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)){
                        //  修改交易记录状态！再订单状态！
                        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent(notifyId, notifyId, 1462, TimeUnit.MINUTES);
                    if (flag){
                        this.paymentService.paySuccess(outTradeNo, PaymentType.ALIPAY.name(),paramsMap);
                        return "success";
                    }
                    return "success";
                }
            }
            //  放入redis！ setnx：当 key 不存在的时候生效！
        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable(value = "orderId")Long orderId) {
        // 调用退款接口
        boolean flag = alipayService.refund(orderId);
        return Result.ok(flag);
    }


    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.closePay(orderId);
        return aBoolean;
    }

    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用退款接口
        boolean flag = alipayService.checkPayment(orderId);
        return flag;
    }

    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }

}
