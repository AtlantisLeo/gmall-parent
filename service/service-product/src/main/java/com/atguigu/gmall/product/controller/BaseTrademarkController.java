package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/product/baseTrademark/")
public class BaseTrademarkController {

    @Autowired
   private BaseTrademarkService baseTrademarkService;

    @GetMapping("{page}/{limit}")
    public Result getTrademarkPage(@PathVariable Long page,
                        @PathVariable Long limit){
       IPage<BaseTrademark> iPage =  baseTrademarkService.getTrademarkPage(page,limit);
        return Result.ok(iPage);
    }

    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){
        BaseTrademark byId = baseTrademarkService.getById(id);
        return  Result.ok(byId);
    }

    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }

    @PutMapping("update")
    public Result updateById(@RequestBody BaseTrademark banner){
        baseTrademarkService.updateById(banner);
        return Result.ok();
    }

    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }
}
