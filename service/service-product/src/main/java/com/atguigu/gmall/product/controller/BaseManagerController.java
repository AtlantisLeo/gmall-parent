package com.atguigu.gmall.product.controller;


import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.BaseManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product/")
public class BaseManagerController {

    @Autowired
    private  BaseManagerService baseManagerService;

    @GetMapping("getCategory1")
    public Result getCategory1(){
       List<BaseCategory1> list = baseManagerService.getCategory1();
       return Result.ok(list);
    }
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id){
        List<BaseCategory2> list = baseManagerService.getCategory2(category1Id);
        return Result.ok(list);
    }
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id){
        List<BaseCategory3> list = baseManagerService.getCategory3(category2Id);
        return Result.ok(list);
    }

    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable Long category1Id,
                               @PathVariable Long category2Id,
                               @PathVariable Long category3Id){
        List<BaseAttrInfo> list = baseManagerService.attrInfoList(category1Id,category2Id,category3Id);
        return Result.ok(list);
    }

    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
        baseManagerService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable Long attrId){
        BaseAttrInfo baseAttrInfo =  baseManagerService.getAttrInfo(attrId);
        return Result.ok(baseAttrInfo.getAttrValueList());
    }

    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){
       List<BaseSaleAttr> list =  baseManagerService.baseSaleAttrList();
       return Result.ok(list);
    }
}
