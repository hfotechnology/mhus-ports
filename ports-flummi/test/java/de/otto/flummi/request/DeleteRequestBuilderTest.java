package de.otto.flummi.request;

import com.ning.http.client.AsyncHttpClient;
import de.otto.flummi.CompletedFuture;
import de.otto.flummi.MockResponse;
import de.otto.flummi.response.HttpServerErrorException;
import de.otto.flummi.util.HttpClientWrapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class DeleteRequestBuilderTest {

    private HttpClientWrapper httpClient;
    private DeleteRequestBuilder testee;

    @BeforeMethod
    private void setup() {
        httpClient = mock(HttpClientWrapper.class);
        testee = new DeleteRequestBuilder(httpClient);
    }

    @Test
    public void shouldDeleteDocument() {
        HttpRequest boundRequestBuilderMock = mock(HttpRequest.class);

        when(httpClient.prepareDelete("/someIndexName/someType/someId")).thenReturn(boundRequestBuilderMock);
        when(boundRequestBuilderMock.execute()).thenReturn(new CompletedFuture(new MockResponse(200, "ok", "")));
        testee.setDocumentType("someType")
                .setIndexName("someIndexName")
                .setId("someId")
                .execute();
        verify(httpClient).prepareDelete("/someIndexName/someType/someId");
    }

    @Test
    public void shouldThrowExceptionIfIndexNameIsMissing() {
        try {
            testee.setDocumentType("someType")
                    .setId("someId")
                    .execute();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("missing property 'indexName'"));
        }
    }

    @Test
    public void shouldThrowExceptionIfTypeIsMissing() {
        try {
            testee.setIndexName("someIndexName")
                    .setId("someId")
                    .execute();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("missing property 'type'"));
        }
    }

    @Test
    public void shouldThrowExceptionIfIdIsMissing() {
        try {
            testee.setDocumentType("someType")
                    .setIndexName("someIndexName")
                    .execute();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("missing property 'id'"));
        }
    }

    @Test(expectedExceptions = HttpServerErrorException.class)
    public void shouldThrowExceptionIfStatusIsNot200() {
        HttpRequest boundRequestBuilderMock = mock(HttpRequest.class);

        when(httpClient.prepareDelete("/someIndexName/someType/someId")).thenReturn(boundRequestBuilderMock);
        when(boundRequestBuilderMock.execute()).thenReturn(new CompletedFuture(new MockResponse(400, "not ok", "errorResponse")));
        try {
            testee.setDocumentType("someType")
                    .setId("someId")
                    .setIndexName("someIndexName")
                    .execute();
        } catch (HttpServerErrorException e) {
            assertThat(e.getStatusCode(), is(400));
            assertThat(e.getResponseBody(), is("errorResponse"));
            throw e;
        }
    }
}