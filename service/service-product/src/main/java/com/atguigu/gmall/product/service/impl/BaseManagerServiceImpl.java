package com.atguigu.gmall.product.service.impl;


import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.model.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.BaseManagerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BaseManagerServiceImpl implements BaseManagerService {

    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;
    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;
    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;
    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;
    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;
    @Autowired
    private SpuInfoMapper spuInfoMapper;
    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;
    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    private SpuImageMapper spuImageMapper;
    @Autowired
    private SpuPosterMapper spuPosterMapper;
    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;
    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    private SkuImageMapper skuImageMapper;
    @Autowired
    private SkuInfoMapper skuInfoMapper;
    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }
    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        LambdaQueryWrapper<BaseCategory2> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseCategory2::getCategory1Id, category1Id);
        return baseCategory2Mapper.selectList(wrapper);
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        LambdaQueryWrapper<BaseCategory3> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseCategory3::getCategory2Id, category2Id);
        return baseCategory3Mapper.selectList(wrapper);
    }

    @Override
    public List<BaseAttrInfo> attrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        return baseAttrInfoMapper.attrInfoList(category1Id,category2Id,category3Id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        if (baseAttrInfo.getId() != null){
            baseAttrInfoMapper.updateById(baseAttrInfo);
            LambdaQueryWrapper<BaseAttrValue> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BaseAttrValue::getAttrId, baseAttrInfo.getId());
            baseAttrValueMapper.delete(wrapper);
        }else {
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        if (!CollectionUtils.isEmpty(baseAttrInfo.getAttrValueList())){
            for (BaseAttrValue baseAttrValue : baseAttrInfo.getAttrValueList()) {
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }
    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        LambdaQueryWrapper<BaseAttrValue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseAttrValue::getAttrId, attrId);
        List<BaseAttrValue> attrValueList = baseAttrValueMapper.selectList(wrapper);
        baseAttrInfo.setAttrValueList(attrValueList);
        return baseAttrInfo;
    }

    @Override
    public IPage<SpuInfo> getSpuInfoPage(SpuInfo spuInfo, Page<SpuInfo> spuInfoPage) {
        LambdaQueryWrapper<SpuInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpuInfo::getCategory3Id, spuInfo.getCategory3Id());
        wrapper.orderByAsc(SpuInfo::getId);
        return spuInfoMapper.selectPage(spuInfoPage,wrapper);
    }

    @Override
    public List<BaseSaleAttr> baseSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpuInfo(SpuInfo spuInfo) {
        if (spuInfo != null){
            spuInfoMapper.insert(spuInfo);
        }
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (!CollectionUtils.isEmpty(spuImageList)){
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (!CollectionUtils.isEmpty(spuSaleAttrList)){
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (!CollectionUtils.isEmpty(spuSaleAttrValueList)){
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }
        List<SpuPoster> spuPosterList = spuInfo.getSpuPosterList();
        if (!CollectionUtils.isEmpty(spuPosterList)){
            for (SpuPoster spuPoster : spuPosterList) {
                spuPoster.setSpuId(spuInfo.getId());
                spuPosterMapper.insert(spuPoster);
            }
        }
    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        LambdaQueryWrapper<SpuImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpuImage::getSpuId, spuId);
        List<SpuImage> list = spuImageMapper.selectList(wrapper);
        return list;
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        return spuSaleAttrMapper.getSpuSaleAttrList(spuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSkuInfo(SkuInfo skuInfo) {
        if (skuInfo != null){
            skuInfoMapper.insert(skuInfo);
        }
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (!CollectionUtils.isEmpty(skuImageList)){
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)){
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                    skuAttrValue.setSkuId(skuInfo.getId());
                    skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)){
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                        skuSaleAttrValue.setSkuId(skuInfo.getId());
                        skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                        skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }
    }

    @Override
    public IPage<SkuInfo> getSkuPage(Long page, Long limit) {
        Page<SkuInfo> infoPage = new Page<>(page, limit);
        LambdaQueryWrapper<SkuInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(SkuInfo::getId);
        return skuInfoMapper.selectPage(infoPage, queryWrapper);
    }

    @Override
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(1);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConst.SKU_BLOOM_FILTER);
        bloomFilter.add(skuId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);
    }

    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(0);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);
    }

    @Override
    @GmallCache(prefix = "sku:")
    public SkuInfo getSkuInfo(Long skuId) {
        return getSkuInfoDB(skuId);

    }

    private SkuInfo getSkuInfoDB(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        LambdaQueryWrapper<SkuImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SkuImage::getSkuId, skuId);
        List<SkuImage> skuImages = skuImageMapper.selectList(queryWrapper);
        if (skuInfo!=null){
            skuInfo.setSkuImageList(skuImages);
        }
        return skuInfo;
    }

    @Override
    @GmallCache(prefix = "categoryView:")
    public BaseCategoryView getCategoryView(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    public BigDecimal getSkuPrice(Long skuId) {
        RLock lock = redissonClient.getLock(skuId + ":lock");
        BigDecimal price  = new BigDecimal(0);
        try {
            lock.lock();
            SkuInfo skuInfo= skuInfoMapper.selectById(skuId);
            if (skuInfo!=null){
                price = skuInfo.getPrice();
            }
        }finally {
            lock.unlock();
        }
        return price;
    }

    @Override
    @GmallCache(prefix = "spuSaleAttrListCheck:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return  spuSaleAttrMapper.getSpuSaleAttrListCheckBySku(skuId,spuId);
    }

    @Override
    @GmallCache(prefix = "skuValueIdsMap:")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object, Object> hashMap = new HashMap<>();
        List<Map> mapList =  skuSaleAttrValueMapper.getSkuValueIdsMap(spuId);
        for (Map map : mapList) {
            hashMap.put(map.get("value_ids"), map.get("sku_id"));
        }
        return hashMap;
    }

    @Override
    @GmallCache(prefix = "spuPoster:")
    public List<SpuPoster> findSpuPosterBySpuId(Long spuId) {
        LambdaQueryWrapper<SpuPoster> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpuPoster::getSpuId, spuId);
        return  spuPosterMapper.selectList(wrapper);
    }

    @Override
    @GmallCache(prefix = "attrList:")
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        return baseAttrInfoMapper.getAttrList(skuId);
    }

    @Override
    @GmallCache(prefix = "CategoryList:")
    public List<JSONObject> getBaseCategoryList() {
        List<JSONObject> jsonObjects = new ArrayList<>();
        List<BaseCategoryView> categoryViewList = baseCategoryViewMapper.selectList(null);
        Map<Long, List<BaseCategoryView>> collect = categoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        int index=1;
        for (Map.Entry<Long, List<BaseCategoryView>> category1Entry : collect.entrySet()) {
            Long category1Id = category1Entry.getKey();
            List<BaseCategoryView> categoryViewList2 = category1Entry.getValue();
            JSONObject category1  = new JSONObject();
            category1.put("index",index++);
            category1.put("categoryId", category1Id);
            category1.put("categoryName", categoryViewList2.get(0).getCategory1Name());
            Map<Long, List<BaseCategoryView>> collect1 = categoryViewList2.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            List<JSONObject> category2Child = new ArrayList<>();
            for (Map.Entry<Long, List<BaseCategoryView>> category2Entry : collect1.entrySet()) {
                Long category2Id = category2Entry.getKey();
                List<BaseCategoryView> categoryViewList3 = category2Entry.getValue();
                JSONObject category2  = new JSONObject();
                category2.put("categoryId", category2Id);
                category2.put("categoryName", categoryViewList3.get(0).getCategory2Name());
                category2Child.add(category2);
                List<JSONObject> category3Child = new ArrayList<>();
                for (BaseCategoryView category3View : categoryViewList3) {
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",category3View.getCategory3Id());
                    category3.put("categoryName",category3View.getCategory3Name());
                    category3Child.add(category3);

                }
                category2.put("categoryChild",category3Child);
            }

            category1.put("categoryChild",category2Child);
            jsonObjects.add(category1);
        }
        return jsonObjects;
    }

}
