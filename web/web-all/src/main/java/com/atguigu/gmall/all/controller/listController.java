package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class listController {

    @Autowired
    private ListFeignClient listFeignClient;

    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model) {
        Result<Map> result = listFeignClient.list(searchParam);
        model.addAllAttributes(result.getData());
        String urlParam = makeUrlParam(searchParam);
        //处理品牌条件回显
        String trademarkParam = makeTrademark(searchParam.getTrademark());
        //处理平台属性条件回显
        List<Map<String, String>> propsParamList = makeProps(searchParam.getProps());
        Map<String,Object> orderMap =  makeOrder(searchParam.getOrder());
        model.addAttribute("searchParam", searchParam);
        model.addAttribute("urlParam",urlParam);
        model.addAttribute("trademarkParam",trademarkParam);
        model.addAttribute("propsParamList",propsParamList);
        model.addAttribute("orderMap",orderMap);
        return "list/index";
    }

    private Map<String, Object> makeOrder(String order) {
        Map<String, Object> map = new HashMap<>();
        if (!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            if (split.length == 2 && split != null){
                map.put("type",split[0]);
                map.put("sort",split[1]);
            }
        }else {
            map.put("type","1");
            map.put("sort","desc");
        }
        return map;
    }

    private List<Map<String, String>> makeProps(String[] props) {
        List<Map<String,String>> mapList = new ArrayList<>();
        if (props!=null && props.length>0){
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split != null && split.length==3){
                    HashMap<String, String> map = new HashMap<String,String>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    mapList.add(map);
                }
            }

        }
        return mapList;
    }

    private String makeTrademark(String trademark) {
        if (!StringUtils.isEmpty(trademark)){
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                return "品牌：" + split[1];
            }
        }
        return "";
    }

    private String makeUrlParam(SearchParam searchParam) {
        StringBuilder builder = new StringBuilder();
        String keyword = searchParam.getKeyword();
        if (!StringUtils.isEmpty(keyword)){
            builder.append("keyword="+keyword);
        }
        if (searchParam.getCategory1Id()!=null){
            builder.append("category1Id="+searchParam.getCategory1Id());
        }
        if (searchParam.getCategory2Id()!=null){
            builder.append("category2Id="+searchParam.getCategory2Id());
        }
        if (searchParam.getCategory3Id()!=null){
            builder.append("category3Id="+searchParam.getCategory3Id());
        }

        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            if (builder.length()>0){
                builder.append("&trademark="+trademark);
            }
        }

        String[] props = searchParam.getProps();
        if (props!=null){
            for (String prop : props) {
                if (builder.length()>0){
                    builder.append("&props="+prop);
                }
            }
        }

        return "list.html?"+builder.toString();
    }


}
