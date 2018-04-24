package de.otto.flummi;

import static de.otto.flummi.request.GsonHelper.object;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

import de.otto.flummi.request.BulkRequestBuilder;
import de.otto.flummi.request.CountRequestBuilder;
import de.otto.flummi.request.DeleteRequestBuilder;
import de.otto.flummi.request.GetRequestBuilder;
import de.otto.flummi.request.IndexRequestBuilder;
import de.otto.flummi.request.MultiGetRequestBuilder;
import de.otto.flummi.request.PingBuilder;
import de.otto.flummi.request.RefreshRequestBuilder;
import de.otto.flummi.request.SearchRequestBuilder;
import de.otto.flummi.request.SearchScrollRequestBuilder;
import de.otto.flummi.util.HttpClientWrapper;


public class Flummi {
    private final HttpClientWrapper httpClient;
    private final Gson gson;

    public Flummi(String baseUrl) {
    	this(new AsyncHttpClient(), baseUrl);
    }
    
    public Flummi(AsyncHttpClient asyncHttpClient, String baseUrl) {
        this.httpClient = new HttpClientWrapper(asyncHttpClient, baseUrl);
        this.gson = new Gson();
    }

    public Optional<String> getIndexNameForAlias(String aliasName) {
        try {
            Response response = httpClient.prepareGet("/_aliases").execute().get();
            if (response.getStatusCode() != 200) {
                throw RequestBuilderUtil.toHttpServerErrorException(response);
            }
            String jsonString = response.getResponseBody();

            return gson.fromJson(jsonString, JsonObject.class).entrySet().stream()
                    .filter(e -> (e.getValue() != null
                            && e.getValue().isJsonObject()
                            && e.getValue().getAsJsonObject().get("aliases") != null
                            && e.getValue().getAsJsonObject().get("aliases").isJsonObject()
                            && e.getValue().getAsJsonObject().get("aliases").getAsJsonObject().has(aliasName)))
                    .map(e -> e.getKey())
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void pointProductAliasToCurrentIndex(String aliasName, String indexName) throws InvalidElasticsearchResponseException {
        try {
            JsonArray actions = new JsonArray();
            actions.add(object("remove", object("index", "*", "alias", aliasName)));
            actions.add(object("add", object("index", indexName, "alias", aliasName)));
            JsonObject jsonObject = object("actions", actions);

            Response response = httpClient
                    .preparePost("/_aliases")
                    .setBody(gson.toJson(jsonObject))
                    .execute().get();
            if (response.getStatusCode() >= 300) {
                throw RequestBuilderUtil.toHttpServerErrorException(response);
            }
            JsonObject responseObject = gson.fromJson(response.getResponseBody(), JsonObject.class);
            if (!responseObject.has("acknowledged")) {
                throw new InvalidElasticsearchResponseException("Response does not contain field 'acknowledged': " + responseObject);
            } else {
                if (!responseObject.get("acknowledged").getAsBoolean()) {
                    throw new RuntimeException("Pointing product alias to current index not acknowledged");
                }
            }
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean aliasExists(String aliasName) {
        try {
            Response response = httpClient.prepareGet("/_aliases").execute().get();
            if (response.getStatusCode() != 200) {
                throw RequestBuilderUtil.toHttpServerErrorException(response);
            }
            String jsonString = response.getResponseBody();
            JsonObject responseObject = gson.fromJson(jsonString, JsonObject.class);

            return responseObject.entrySet().stream().filter(e ->
                    (e.getValue() != null
                            && e.getValue().isJsonObject()
                            && e.getValue().getAsJsonObject().get("aliases") != null
                            && e.getValue().getAsJsonObject().get("aliases").isJsonObject()
                            && e.getValue().getAsJsonObject().get("aliases").getAsJsonObject().has(aliasName)))
                    .count() > 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getAllIndexNames() {
        try {
            Response response = httpClient.prepareGet("/_all").execute().get();
            if (response.getStatusCode() != 200) {
                throw RequestBuilderUtil.toHttpServerErrorException(response);
            }
            String jsonString = response.getResponseBody();
            JsonObject responseObject = gson.fromJson(jsonString, JsonObject.class);

            return responseObject.entrySet().stream()
                    .map(Map.Entry::getKey)
                    .collect(toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonObject getIndexSettings() {
        try {
            Response response = httpClient.prepareGet("/_all/_settings").execute().get();
            if (response.getStatusCode() != 200) {
                throw RequestBuilderUtil.toHttpServerErrorException(response);
            }
            String jsonString = null;
            try {
                jsonString = response.getResponseBody();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return gson.fromJson(jsonString, JsonObject.class);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void refreshIndex(final String indexName) {
        new RefreshRequestBuilder(httpClient, indexName).execute();
    }

    public SearchRequestBuilder prepareSearch(String... indices) {
        return new SearchRequestBuilder(httpClient, indices);
    }

    public CountRequestBuilder prepareCount(String... indices) {
        return new CountRequestBuilder(httpClient, indices);
    }

    public BulkRequestBuilder prepareBulk() {
        return new BulkRequestBuilder(httpClient);
    }

    public GetRequestBuilder prepareGet(String indexName, String documentType, String id) {
        return new GetRequestBuilder(httpClient, indexName, documentType, id);
    }

    public DeleteRequestBuilder prepareDelete() {
        return new DeleteRequestBuilder(httpClient);
    }

    public MultiGetRequestBuilder prepareMultiGet(String[] indices) {
        return new MultiGetRequestBuilder(httpClient, indices);
    }

    public SearchScrollRequestBuilder prepareScroll() {
        return new SearchScrollRequestBuilder(httpClient);
    }

    public IndexRequestBuilder prepareIndex() {
        return new IndexRequestBuilder(httpClient);
    }

    public AdminClient admin() {
        return new AdminClient(httpClient);
    }
    
    public PingBuilder preparePing() {
    	return new PingBuilder(httpClient);
    }
}
