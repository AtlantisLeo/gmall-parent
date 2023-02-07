package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.BaseManagerService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product/")
public class SkuManagerController {

    @Autowired
    private  BaseManagerService baseManagerService;

    @GetMapping("spuImageList/{spuId}")
    public Result getSpuImageList(@PathVariable Long spuId){
        List<SpuImage> list = baseManagerService.getSpuImageList(spuId);
        return Result.ok(list);
    }
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result getSpuSaleAttrList(@PathVariable Long spuId){
        List<SpuSaleAttr> list = baseManagerService.getSpuSaleAttrList(spuId);
        return Result.ok(list);
    }

    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo){
        baseManagerService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    @GetMapping("list/{page}/{limit}")
    public Result getSkuPage(@PathVariable Long page, @PathVariable Long limit){
        IPage<SkuInfo> skuInfoIPage =  baseManagerService.getSkuPage(page,limit);
        return Result.ok(skuInfoIPage);
    }

    @GetMapping("onSale/{skuId}")
    public Result onSale(@PathVariable Long skuId){
        baseManagerService.onSale(skuId);
        return Result.ok();
    }

    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId){
        baseManagerService.cancelSale(skuId);
        return Result.ok();
    }
}
