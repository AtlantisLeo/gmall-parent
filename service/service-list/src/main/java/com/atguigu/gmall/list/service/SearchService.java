package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;

public interface SearchService {
    void upperGoods(Long skuId);

    void lowerGoods(Long skuId);

    void incrHotScore(Long skuId);

    SearchResponseVo search(SearchParam searchParam);

}
