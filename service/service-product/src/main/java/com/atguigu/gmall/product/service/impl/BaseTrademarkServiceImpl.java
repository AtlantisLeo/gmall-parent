package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BaseTrademarkServiceImpl extends ServiceImpl<BaseTrademarkMapper, BaseTrademark> implements BaseTrademarkService {

    @Override
    public IPage<BaseTrademark> getTrademarkPage(Long page, Long limit) {
        Page<BaseTrademark> baseTrademarkPage = new Page<>(page, limit);
        LambdaQueryWrapper<BaseTrademark> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BaseTrademark::getId);
        return baseMapper.selectPage(baseTrademarkPage,wrapper);
    }

}
