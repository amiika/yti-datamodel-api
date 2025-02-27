package fi.vm.yti.datamodel.api.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.vm.yti.datamodel.api.index.model.DeepSearchHitListDTO;
import fi.vm.yti.datamodel.api.index.model.DeepSearchResourceHitListDTO;
import fi.vm.yti.datamodel.api.index.model.IndexResourceDTO;
import fi.vm.yti.datamodel.api.index.model.ModelSearchRequest;

@Singleton
@Service
public class DeepResourceQueryFactory {

    private static final Logger logger = LoggerFactory.getLogger(DeepResourceQueryFactory.class);
    private static final Pattern sortLangPattern = Pattern.compile("[a-zA-Z-]+");
    private static final FetchSourceContext sourceIncludes = new FetchSourceContext(true, new String[]{ "id", "status", "label", "comment", "isDefinedBy", "type" }, new String[]{});
    private static final Script topHitScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, "_score", Collections.emptyMap());
    private ObjectMapper objectMapper;
    private LuceneQueryFactory luceneQueryFactory;

    @Autowired
    public DeepResourceQueryFactory(ObjectMapper objectMapper,
                                    LuceneQueryFactory luceneQueryFactory) {
        this.luceneQueryFactory = luceneQueryFactory;
        this.objectMapper = objectMapper;
    }

    public SearchRequest createQuery(String query,
                                     String sortLang,
                                     Set<String> modelIds) {

        QueryStringQueryBuilder queryStringQuery = luceneQueryFactory.buildPrefixSuffixQuery(query).field("label.*");
        if (sortLang != null && sortLangPattern.matcher(sortLang).matches()) {
            queryStringQuery = queryStringQuery.field("label." + sortLang, 10);
        }

        QueryBuilder finalQuery = modelIds != null ? QueryBuilders.boolQuery()
            .must(ElasticUtils.createStatusAndModelQuery("isDefinedBy", modelIds))
            .must(queryStringQuery) :
            QueryBuilders.boolQuery()
                .must(queryStringQuery);

        return new SearchRequest("dm_resources")
            .source(new SearchSourceBuilder()
                .query(finalQuery)
                .size(0)
                .aggregation(AggregationBuilders.terms("group_by_model")
                    .field("isDefinedBy")
                    .size(1000)
                    .subAggregation(AggregationBuilders.terms("sub_group_by_type")
                        .field("type")
                        .size(1000)
                        .order(BucketOrder.aggregation("best_class_hit", false))
                        .subAggregation(AggregationBuilders.topHits("top_resource_hits")
                            .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                            .size(6)
                            .fetchSource(sourceIncludes))
                        .subAggregation(AggregationBuilders.max("best_class_hit")
                            .script(topHitScript)))));
    }

    public Map<String, List<DeepSearchHitListDTO<?>>> parseResponse(SearchResponse response,
                                                                    ModelSearchRequest request) {
        Map<String, List<DeepSearchHitListDTO<?>>> ret = new HashMap<>();
        try {
            Terms groupByModel = response.getAggregations().get("group_by_model");
            for (Terms.Bucket modelBucket : groupByModel.getBuckets()) {
                String modelId = modelBucket.getKeyAsString();
                List<DeepSearchHitListDTO<?>> hitLists = new ArrayList<>();
                ret.put(modelId, hitLists);
                Terms groupByType = modelBucket.getAggregations().get("sub_group_by_type");
                for (Terms.Bucket typeBucket : groupByType.getBuckets()) {
                    String type = typeBucket.getKeyAsString();
                    TopHits hitsAggr = typeBucket.getAggregations().get("top_resource_hits");
                    SearchHits hits = hitsAggr.getHits();
                    long total = hits.getTotalHits();
                    if (total > 0) {
                        List<IndexResourceDTO> topHits = new ArrayList<>();
                        DeepSearchResourceHitListDTO hitList = new DeepSearchResourceHitListDTO(type, total, topHits);
                        hitLists.add(hitList);

                        for (SearchHit hit : hits.getHits()) {
                            IndexResourceDTO indexResource = objectMapper.readValue(hit.getSourceAsString(), IndexResourceDTO.class);
                            indexResource.highlightLabels(request.getQuery());
                            topHits.add(indexResource);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Cannot parse deep class query response", e);
        }
        return ret;
    }
}
