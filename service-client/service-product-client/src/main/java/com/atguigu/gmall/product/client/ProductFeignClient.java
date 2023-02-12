package com.atguigu.gmall.product.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.Impl.ProductDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(value = "service-product",fallback = ProductDegradeFeignClient.class)
public interface ProductFeignClient {


    @GetMapping("/api/product/getBaseCategoryList")
    public Result getBaseCategoryList();

    @GetMapping("/api/product/inner/getSkuInfo/{skuId}")
    public SkuInfo getSkuInfo(@PathVariable Long skuId);

    @GetMapping("/api/product/inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable("category3Id") Long category3Id);
    @GetMapping("/api/product/inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable Long skuId);

    @GetMapping("/api/product/inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(@PathVariable("skuId") Long skuId,
                                                          @PathVariable("spuId") Long spuId);

    @GetMapping("/api/product/inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable("spuId") Long spuId);

    @GetMapping("/api/product/inner/findSpuPosterBySpuId/{spuId}")
    public List<SpuPoster> findSpuPosterBySpuId(@PathVariable Long spuId);

    @GetMapping("/api/product/inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable("skuId") Long skuId);

    @GetMapping("/api/product/inner/getTrademark/{tmId}")
    public  BaseTrademark getTrademark(@PathVariable("tmId")Long tmId);
}
