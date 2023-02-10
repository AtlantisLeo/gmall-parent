package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemApiService {
    Map<String, Object> getBySkuId(Long skuId);
}
