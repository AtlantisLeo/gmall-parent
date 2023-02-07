package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.BaseManagerService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/product")
public class SpuManageController {

    @Autowired
    private BaseManagerService baseManagerService;


    @GetMapping("{page}/{size}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long size,
                                 SpuInfo spuInfo){

        Page<SpuInfo> spuInfoPage = new Page<>(page, size);
        IPage<SpuInfo> spuInfoIPage = baseManagerService.getSpuInfoPage(spuInfo,spuInfoPage);
        return Result.ok(spuInfoIPage);
    }

    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        baseManagerService.saveSpuInfo(spuInfo);
        return Result.ok();
    }
}
