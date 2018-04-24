package de.otto.flummi.aggregations;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.otto.flummi.response.AggregationResult;
import de.otto.flummi.response.Bucket;
import org.testng.annotations.Test;

import java.util.List;

import static de.otto.flummi.request.GsonHelper.array;
import static de.otto.flummi.request.GsonHelper.object;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class AggregationResultParserTest {

    @Test
    public void shouldParseResponse() {
        // given
        JsonObject response = object("buckets", array(object("key", "someKey", "doc_count", "1")));

        // when
        AggregationResult aggregation = AggregationResultParser.parseBuckets(response);

        // then
        assertThat(aggregation.getNestedAggregations(), is(emptyMap()));
        assertThat(aggregation.getBuckets(), hasSize(1));
        assertThat(aggregation.getBuckets().get(0).getKey(), is("someKey"));
        assertThat(aggregation.getBuckets().get(0).getDocCount(), is(1L));
    }

    @Test
    public void shouldParseResponseWithMultipleBuckets() {
        // given
        JsonObject response = object("buckets", array(object("key", "someKey", "doc_count", "1"), object("key", "someKey2", "doc_count", "3")));

        // when
        AggregationResult aggregation = AggregationResultParser.parseBuckets(response);

        // then
        assertThat(aggregation.getNestedAggregations(), is(emptyMap()));
        assertThat(aggregation.getBuckets(), hasSize(2));
        assertThat(aggregation.getBuckets().get(0).getKey(), is("someKey"));
        assertThat(aggregation.getBuckets().get(0).getDocCount(), is(1L));
        assertThat(aggregation.getBuckets().get(1).getKey(), is("someKey2"));
        assertThat(aggregation.getBuckets().get(1).getDocCount(), is(3L));
    }

    @Test
    public void shouldParseResponseWithEmptyBuckets() {
        // given
        JsonObject response = object("buckets", array());

        // when
        AggregationResult aggregation = AggregationResultParser.parseBuckets(response);

        // then
        assertThat(aggregation.getNestedAggregations(), is(emptyMap()));
        assertThat(aggregation.getBuckets(), hasSize(0));
    }


    @Test
    public void shouldParseResponseWithNestedTermsQuery() {
        // given
        JsonObject response = object("someName", object("buckets", array(object("key", "someKey", "doc_count", "1"))));

        // when
        AggregationResult aggregation = AggregationResultParser.parseSubAggregations(response, asList(new TermsBuilder("someName").field("someField")));

        // then
        assertThat(aggregation.getBuckets(), is(emptyList()));
        List<Bucket> buckets = aggregation.getNestedAggregations().get("someName").getBuckets();
        assertThat(buckets, hasSize(1));
        assertThat(buckets.get(0).getKey(), is("someKey"));
        assertThat(buckets.get(0).getDocCount(), is(1L));
    }
}