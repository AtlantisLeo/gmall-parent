package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.model.product.BaseCategoryTrademark;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.CategoryTrademarkVo;
import com.atguigu.gmall.product.mapper.BaseCategoryTrademarkMapper;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseCategoryTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BaseCategoryTrademarkServiceImpl extends ServiceImpl<BaseCategoryTrademarkMapper,BaseCategoryTrademark>  implements BaseCategoryTrademarkService {

    @Autowired
    private BaseCategoryTrademarkMapper baseCategoryTrademarkMapper;
    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Override
    public List<BaseTrademark> findTrademarkList(Long category3Id) {
        List<BaseTrademark> baseTrademarks = new ArrayList<>();
        LambdaQueryWrapper<BaseCategoryTrademark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseCategoryTrademark::getCategory3Id,category3Id);
        List<BaseCategoryTrademark> categoryTrademarkList = baseCategoryTrademarkMapper.selectList(wrapper);
        List<Long> collect = categoryTrademarkList.stream().map(BaseCategoryTrademark::getTrademarkId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(collect)){
            LambdaQueryWrapper<BaseTrademark> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(BaseTrademark::getId,collect);
            baseTrademarks = baseTrademarkMapper.selectList(queryWrapper);
        }
        return baseTrademarks;
    }

    @Override
    public void save(CategoryTrademarkVo categoryTrademarkVo) {
        List<Long> trademarkIdList = categoryTrademarkVo.getTrademarkIdList();
        if (!CollectionUtils.isEmpty(trademarkIdList)){
            for (Long trademarkId : trademarkIdList) {
                BaseCategoryTrademark baseCategoryTrademark = new BaseCategoryTrademark();
                baseCategoryTrademark.setCategory3Id(categoryTrademarkVo.getCategory3Id());
                baseCategoryTrademark.setTrademarkId(trademarkId);
                baseCategoryTrademarkMapper.insert(baseCategoryTrademark);
            }
        }
    }

    @Override
    public void removeBy2Id(Long category3Id, Long trademarkId) {
        LambdaQueryWrapper<BaseCategoryTrademark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseCategoryTrademark::getCategory3Id,category3Id);
        wrapper.eq(BaseCategoryTrademark::getTrademarkId, trademarkId);
        baseCategoryTrademarkMapper.delete(wrapper);
    }

    @Override
    public List<BaseTrademark> findCurrentTrademarkList(Long category3Id) {

        LambdaQueryWrapper<BaseCategoryTrademark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BaseCategoryTrademark::getCategory3Id,category3Id);
        List<BaseCategoryTrademark> categoryTrademarkList = baseCategoryTrademarkMapper.selectList(wrapper);
        List<Long> collect = categoryTrademarkList.stream().map(BaseCategoryTrademark::getTrademarkId).collect(Collectors.toList());
        LambdaQueryWrapper<BaseTrademark> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.notIn(BaseTrademark::getId, collect);
        return baseTrademarkMapper.selectList(queryWrapper);
    }
}
