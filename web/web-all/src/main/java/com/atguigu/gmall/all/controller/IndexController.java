package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
    @Autowired
    private ProductFeignClient productFeignClient;

    @GetMapping({"/", "index.html"})
    public String index(Model model){
        Result list = productFeignClient.getBaseCategoryList();

        model.addAttribute("list", list.getData());
        return "index/index";
    }
}
