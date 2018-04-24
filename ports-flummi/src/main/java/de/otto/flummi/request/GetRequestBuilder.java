package de.otto.flummi.request;

import static de.otto.flummi.RequestBuilderUtil.toHttpServerErrorException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ning.http.client.Response;

import de.mhus.lib.core.logging.Log;
import de.otto.flummi.RequestBuilderUtil;
import de.otto.flummi.response.GetResponse;
import de.otto.flummi.util.HttpClientWrapper;

public class GetRequestBuilder implements RequestBuilder<GetResponse> {
    private HttpClientWrapper httpClient;
    private final String indexName;
    private final String documentType;
    private final String id;
    private final Gson gson;

    public static final Log LOG = Log.getLog(GetRequestBuilder.class);

    public GetRequestBuilder(HttpClientWrapper httpClient, String indexName, String documentType, String id) {
        this.httpClient = httpClient;
        this.indexName = indexName;
        this.documentType = documentType;
        this.id = id;
        this.gson = new Gson();
    }

    @Override
    public GetResponse execute() {
        try {
            String url = RequestBuilderUtil.buildUrl(indexName, documentType, URLEncoder.encode(id, "UTF-8"));
            Response response = httpClient.prepareGet(url).execute().get();
            if (response.getStatusCode() >= 300 && 404 != response.getStatusCode()) {
                throw toHttpServerErrorException(response);
            }

            if (404 == response.getStatusCode()) {
                return new GetResponse(false, null, id);
            }
            String jsonString = response.getResponseBody();
            JsonObject responseObject = gson.fromJson(jsonString, JsonObject.class);
            return new GetResponse(true, responseObject != null && responseObject.get("_source") != null
                    ? responseObject.get("_source").getAsJsonObject()
                    : null, responseObject.get("_id").getAsString());

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
