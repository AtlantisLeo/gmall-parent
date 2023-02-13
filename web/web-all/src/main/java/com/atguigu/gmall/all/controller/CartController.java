package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CartController {

    @Autowired
    private ProductFeignClient productFeignClient;


    @GetMapping("cart.html")
    public String index(){
        return "cart/index";
    }

    @GetMapping("addCart.html")
    public String addCart(@RequestParam(name = "skuId") Long skuId,
                          @RequestParam(name = "skuNum") Integer skuNum,
                          Model model){
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        model.addAttribute(skuInfo);
        model.addAttribute(skuNum);
        return "cart/addCart";
    }

}
