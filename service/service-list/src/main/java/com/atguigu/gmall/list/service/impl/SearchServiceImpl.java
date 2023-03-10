package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import lombok.SneakyThrows;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if (skuInfo != null){
            goods.setId(skuInfo.getId());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setTitle(skuInfo.getSkuName());
            goods.setTmId(skuInfo.getTmId());
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setCreateTime(new Date());
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            if (categoryView!=null){
                goods.setCategory1Id(categoryView.getCategory1Id());
                goods.setCategory2Id(categoryView.getCategory2Id());
                goods.setCategory3Id(categoryView.getCategory3Id());
                goods.setCategory1Name(categoryView.getCategory1Name());
                goods.setCategory2Name(categoryView.getCategory2Name());
                goods.setCategory3Name(categoryView.getCategory3Name());
            }
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (trademark!=null){
                goods.setTmLogoUrl(trademark.getLogoUrl());
                goods.setTmName(trademark.getTmName());
            }

        }
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
        if (!CollectionUtils.isEmpty(attrList)){
            List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                //??????sku????????????????????????
                List<BaseAttrValue> baseAttrValueList = baseAttrInfo.getAttrValueList();
                searchAttr.setAttrValue(baseAttrValueList.get(0).getValueName());
                return searchAttr;
            }).collect(Collectors.toList());
            goods.setAttrs(searchAttrList);
        }

        goodsRepository.save(goods);
    }

    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        String hotKey = "hotScore";
        Double aDouble = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        if (aDouble %10 == 0){
            Optional<Goods> optional  = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(Math.round(aDouble));
            goodsRepository.save(goods);
        }
    }

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @SneakyThrows
    @Override
    public SearchResponseVo search(SearchParam searchParam){

        SearchRequest searchRequest = this.buildQuery(searchParam);
        //????????????????????????
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        //???????????????????????????????????????????????????
        SearchResponseVo searchResponseVo=this.parseSearchResponseVo(searchResponse);
        //??????????????????
        searchResponseVo.setPageSize(searchParam.getPageSize());
        //???????????????
        searchResponseVo.setPageNo(searchParam.getPageNo());
        //?????????
        Long totalPage= (searchResponseVo.getTotal()+searchResponseVo.getPageSize()-1)/searchResponseVo.getPageSize();
        searchResponseVo.setTotalPages(totalPage);
        return searchResponseVo;
    }

    /**
     * ?????????????????????
     * @param searchResponse
     * @return
     */
    private SearchResponseVo parseSearchResponseVo(SearchResponse searchResponse) {

        //????????????
        SearchResponseVo searchResponseVo=new SearchResponseVo();
        //?????????????????????????????????
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        //???????????????????????????
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        List<? extends Terms.Bucket> buckets = tmIdAgg.getBuckets();
        if(!CollectionUtils.isEmpty(buckets)){
            //????????????????????????
            List<SearchResponseTmVo> responseTmVoList = buckets.stream().map(bucket -> {
                //??????????????????
                SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
                //??????id
//                String tmId = ((Terms.Bucket) bucket).getKeyAsString();
                long tmId = ((Terms.Bucket) bucket).getKeyAsNumber().longValue();
                searchResponseTmVo.setTmId(tmId);

                //??????name
                Map<String, Aggregation> tmSubAggregation = ((Terms.Bucket) bucket).getAggregations().asMap();

                //??????????????????????????????
                ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmSubAggregation.get("tmNameAgg");
                String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
                searchResponseTmVo.setTmName(tmName);

                //??????logoUrl
                ParsedStringTerms  tmLogoUrlAgg = (ParsedStringTerms) tmSubAggregation.get("tmLogoUrlAgg");
                String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
                searchResponseTmVo.setTmLogoUrl(tmLogoUrl);

                return searchResponseTmVo;


            }).collect(Collectors.toList());

            //?????????????????????????????????
            searchResponseVo.setTrademarkList(responseTmVoList);
        }



        //??????????????????????????????
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //??????id?????????
        Map<String, Aggregation> attrSubAggregation = attrAgg.getAggregations().asMap();
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrSubAggregation.get("attrIdAgg");
        //??????????????????
        List<? extends Terms.Bucket> subBuckets = attrIdAgg.getBuckets();
        if(!CollectionUtils.isEmpty(subBuckets)){
            //???????????????????????????
            List<SearchResponseAttrVo> responseAttrVoList = subBuckets.stream().map(subBucket -> {

                //????????????????????????
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();

                //??????????????????id
                long attrId = ((Terms.Bucket) subBucket).getKeyAsNumber().longValue();
                searchResponseAttrVo.setAttrId(attrId);
                //?????????????????????
                Map<String, Aggregation> subsbuAggregation = ((Terms.Bucket) subBucket).getAggregations().asMap();

                //?????????????????????
                ParsedStringTerms attrNameAgg = (ParsedStringTerms) subsbuAggregation.get("attrNameAgg");
                String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
                searchResponseAttrVo.setAttrName(attrName);
                //?????????????????????
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) subsbuAggregation.get("attrValueAgg");
                //???????????????????????????
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if(!CollectionUtils.isEmpty(attrValueAggBuckets)){
                    //?????????????????????
                    List<String> attrValueList = attrValueAggBuckets.stream().map(attrValueBucket -> {

                        return ((Terms.Bucket) attrValueBucket).getKeyAsString();
                    }).collect(Collectors.toList());

                    //?????????????????????
                    searchResponseAttrVo.setAttrValueList(attrValueList);
                }


                return searchResponseAttrVo;

            }).collect(Collectors.toList());

            //????????????????????????
            searchResponseVo.setAttrsList(responseAttrVoList);

        }

        //????????????goods??????
        SearchHit[] hits = searchResponse.getHits().getHits();
        //??????????????????goods??????
        List<Goods> goodsList=new ArrayList<>();
        //??????
        if(hits!=null &&hits.length>0){

            for (SearchHit hit : hits) {
                Goods goods = JSONObject.parseObject(hit.getSourceAsString(), Goods.class);
                //??????????????????
                if(hit.getHighlightFields().get("title")!=null){
                    //????????????
                    HighlightField title = hit.getHighlightFields().get("title");
                    goods.setTitle(title.getFragments()[0].toString());


                }


                goodsList.add(goods);

            }

        }

        //????????????????????????
        searchResponseVo.setGoodsList(goodsList);

        //?????????????????????
        long total = searchResponse.getHits().getTotalHits().value;
        searchResponseVo.setTotal(total);

        return searchResponseVo;
    }

    /**
     * ??????????????????
     *
     * @param searchParam
     * @return
     */
    private SearchRequest buildQuery(SearchParam searchParam) {
        //???????????????????????? ??????????????????
        SearchRequest searchRequest = new SearchRequest("goods");
        //????????????????????????
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //?????????????????????
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //??????????????????????????????
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {

            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);

            //???????????????????????????????????????
            boolQueryBuilder.must(title);
        }
        //???????????? trademark=2:??????
        String trademark = searchParam.getTrademark();
        //??????
        if (!StringUtils.isEmpty(trademark)) {
            //split
            String[] split = trademark.split(":");
            //??????
            if (split != null && split.length == 2) {

                //??????????????????
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                //?????? ??????????????????
                boolQueryBuilder.filter(tmId);

            }

        }

        //??????
        if (searchParam.getCategory1Id() != null) {

            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        //??????
        if (searchParam.getCategory2Id() != null) {

            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id()));
        }
        //??????
        if (searchParam.getCategory3Id() != null) {

            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id()));
        }


        //????????????  23:4G:????????????
        String[] props = searchParam.getProps();
        //????????????????????????
        if (props != null && props.length > 0) {
            for (String prop : props) {
                //prop 23:4G:????????????
                //????????????Id ????????????????????? ???????????????
                //split StringUtils.split
                String[] split = prop.split(":");
                //??????
                if(split!=null && split.length==3){

                    //?????????????????????
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    //??????????????????
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));

                    //nested
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));


                    //?????????????????????????????????
                    boolQueryBuilder.filter(boolQuery);
                }


            }


        }


        //???????????????????????????
        searchSourceBuilder.query(boolQueryBuilder);

        //??????
        //????????????
        int index=(searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(index);
        searchSourceBuilder.size(searchParam.getPageSize());
        //?????? 1:desc    1:hotScore 2:price
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            //????????????
            String field=null;
            //switch
            switch (split[0]){
                case "1":
                    field="hotScore";
                    break;
                case "2":
                    field="price";
                    break;
            }

            searchSourceBuilder.sort(field,split[1].equals("asc")?SortOrder.ASC:SortOrder.DESC);



        }else{
            searchSourceBuilder.sort("hotScore", SortOrder.DESC);
        }




        //??????--??????
        TermsAggregationBuilder tmIdAgg = AggregationBuilders.terms("tmIdAgg").field("tmId");
        tmIdAgg.subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"));
        tmIdAgg.subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        searchSourceBuilder.aggregation(tmIdAgg);


        //??????--????????????

        NestedAggregationBuilder nestedAgg = AggregationBuilders.nested("attrAgg", "attrs");
        //???????????????
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("attrs.attrId");

        //??????????????????
        nestedAgg.subAggregation(attrIdAgg);
        //???????????????
        attrIdAgg.subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"));
        attrIdAgg.subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"));

        searchSourceBuilder.aggregation(nestedAgg);

        //??????
        HighlightBuilder highlightBuilder=new HighlightBuilder();
        //????????????
        highlightBuilder.field("title");

        //????????????
        highlightBuilder.preTags("<span style=color:red>");
        //????????????
        highlightBuilder.postTags("</span>");

        searchSourceBuilder.highlighter(highlightBuilder);
        //????????????
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);


        //?????????????????????????????????
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl:=="+searchSourceBuilder.toString());

        return searchRequest;
    }

    // ?????????????????????
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchHits hits = response.getHits();
        //????????????
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        //?????????????????????
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //ParsedLongTerms ?
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo trademark = new SearchResponseTmVo();
            //????????????Id
            trademark.setTmId((Long.parseLong(((Terms.Bucket) bucket).getKeyAsString())));
            //trademark.setTmId(Long.parseLong(bucket.getKeyAsString()));
            //??????????????????
            Map<String, Aggregation> tmIdSubMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();

            trademark.setTmName(tmName);
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdSubMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            trademark.setTmLogoUrl(tmLogoUrl);

            return trademark;
        }).collect(Collectors.toList());
        searchResponseVo.setTrademarkList(trademarkList);

        //??????????????????
        SearchHit[] subHits = hits.getHits();
        List<Goods> goodsList = new ArrayList<>();
        if (subHits!=null && subHits.length>0){
            //????????????
            for (SearchHit subHit : subHits) {
                // ???subHit ???????????????
                Goods goods = JSONObject.parseObject(subHit.getSourceAsString(), Goods.class);

                //????????????
                if (subHit.getHighlightFields().get("title")!=null){
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);

        //????????????????????????
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<SearchResponseAttrVo> searchResponseAttrVOS = buckets.stream().map(bucket -> {
                //????????????????????????
                SearchResponseAttrVo responseAttrVO = new SearchResponseAttrVo();
                //?????????????????????Id
                responseAttrVO.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
                List<? extends Terms.Bucket> nameBuckets = attrNameAgg.getBuckets();
                responseAttrVO.setAttrName(nameBuckets.get(0).getKeyAsString());
                //????????????????????????
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();

                List<String> values = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                responseAttrVO.setAttrValueList(values);

                return responseAttrVO;

            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(searchResponseAttrVOS);
        }
        // ??????????????????
        searchResponseVo.setTotal(hits.getTotalHits().value);

        return searchResponseVo;
    }


}
