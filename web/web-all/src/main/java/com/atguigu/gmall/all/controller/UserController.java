package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    @GetMapping("/login.html")
    public String toLogin(Model model,String originUrl){
        model.addAttribute("originUrl", originUrl);
        return "login";
    }
}
