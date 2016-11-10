package de.otto.flummi.aggregations;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import de.otto.flummi.response.AggregationResult;

public abstract class AggregationBuilder<T extends AggregationBuilder<T>> {
    private final String name;
    protected List<AggregationBuilder<?>> subAggregations;

    protected AggregationBuilder(String name) {
        this.name = name;
    }

    public T subAggregation(AggregationBuilder subAggregation) {
        if (subAggregations == null) {
            subAggregations = new ArrayList<>();
        }
        subAggregations.add(subAggregation);

        return (T) this;
    }

    public String getName() {
        return name;
    }

    public abstract JsonObject build();
    public abstract AggregationResult parseResponse(JsonObject jsonObject);
}
