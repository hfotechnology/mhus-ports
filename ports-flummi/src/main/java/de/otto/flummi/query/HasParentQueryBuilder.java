package de.otto.flummi.query;

import static de.otto.flummi.request.GsonHelper.object;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class HasParentQueryBuilder implements QueryBuilder {
    private final String type;
    private final QueryBuilder query;

    public HasParentQueryBuilder(String type, QueryBuilder query) {
        this.type = type;
        this.query = query;
    }

    @Override
    public JsonObject build() {
        JsonObject hasParent = new JsonObject();
        hasParent.add("type", new JsonPrimitive(type));
        hasParent.add("query", query.build());
        return object(
                "has_parent",
                hasParent);
    }
}
