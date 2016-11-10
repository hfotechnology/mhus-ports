package de.otto.flummi.query;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class TermQueryBuilder implements QueryBuilder {
    private final String name;
    private final JsonElement value;

    public TermQueryBuilder(String name, JsonElement value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public JsonObject build() {
        if (name==null || name.isEmpty()) {
            throw new RuntimeException("missing property 'name'");
        }
        if (value == null) {
            throw new RuntimeException("missing property 'value'");
        }
        JsonObject jsonObject = new JsonObject();
        JsonObject term = new JsonObject();
        jsonObject.add("term", term);
        term.add(name, value);
        return jsonObject;
    }
}
