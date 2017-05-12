/*
The MIT License

Copyright (c) 2013 Mashape (http://mashape.com)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.joshworks.restclient.test.http;

import io.joshworks.restclient.http.ClientContainer;
import io.joshworks.restclient.http.Headers;
import io.joshworks.restclient.http.HttpResponse;
import io.joshworks.restclient.http.JsonNode;
import io.joshworks.restclient.http.RestClient;
import io.joshworks.restclient.http.async.Callback;
import io.joshworks.restclient.http.exceptions.RestClientException;
import io.joshworks.restclient.request.GetRequest;
import io.joshworks.restclient.request.HttpRequest;
import io.joshworks.restclient.test.helper.GetResponse;
import io.joshworks.restclient.test.helper.JsonMapper;
import net.jodah.failsafe.CircuitBreaker;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RestClientTest {

    private CountDownLatch lock;
    private boolean status;

    private RestClient client;

    @Before
    public void setUp() {
        client = RestClient.newClient().build();
        lock = new CountDownLatch(1);
        status = false;
    }

    @After
    public void shutdown() throws IOException {
        if (client != null) {
            client.shutdown();
        }
    }

    @AfterClass
    public static void shutdownContainer() throws IOException {
        ClientContainer.shutdown();
    }

    private String findAvailableIpAddress() throws IOException {
        for (int i = 100; i <= 255; i++) {
            String ip = "192.168.1." + i;
            if (!InetAddress.getByName(ip).isReachable(1000)) {
                return ip;
            }
        }

        throw new RuntimeException("Couldn't find an available IP address in the range of 192.168.0.100-255");
    }

    @Test
    public void testRequests() throws JSONException, RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/post").header("accept", "application/json").field("param1", "value1").field("param2", "bye").asJson();

        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertNotNull(json.getObject());
        assertNotNull(json.getArray());
        assertEquals(1, json.getArray().length());
        assertNotNull(json.getArray().get(0));
    }

    @Test
    public void testGet() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get?name=mark").asJson();
        assertEquals(response.getBody().getObject().getJSONObject("args").getString("name"), "mark");

        response = client.get("http://httpbin.org/get").queryString("name", "mark2").asJson();
        assertEquals(response.getBody().getObject().getJSONObject("args").getString("name"), "mark2");
    }

    @Test
    public void testGetUTF8() throws RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString("param3", "こんにちは").asJson();

        assertEquals(response.getBody().getObject().getJSONObject("args").getString("param3"), "こんにちは");
    }

    @Test
    public void testPostUTF8() throws RestClientException {
        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").field("param3", "こんにちは").asJson();

        assertEquals(response.getBody().getObject().getJSONObject("form").getString("param3"), "こんにちは");
    }

    @Test
    public void testPostBinaryUTF8() throws RestClientException, URISyntaxException {
        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").field("param3", "こんにちは").field("file", new File(getClass().getResource("/test").toURI())).asJson();

        assertEquals("This is a test file", response.getBody().getObject().getJSONObject("files").getString("file"));
        assertEquals("こんにちは", response.getBody().getObject().getJSONObject("form").getString("param3"));
    }

    @Test
    public void testPostRawBody() throws RestClientException, URISyntaxException, IOException {
        String sourceString = "'\"@こんにちは-test-123-" + Math.random();
        byte[] sentBytes = sourceString.getBytes();

        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").body(sentBytes).asJson();

        assertEquals(sourceString, response.getBody().getObject().getString("data"));
    }

    @Test
    public void testCustomUserAgent() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get?name=mark").header("user-agent", "hello-world").asJson();
        assertEquals("hello-world", response.getBody().getObject().getJSONObject("headers").getString("User-Agent"));

        GetRequest getRequest = client.get("http");
        for (Object current : Arrays.asList(0, 1, 2)) {
            getRequest.queryString("name", current);
        }

    }

    @Test
    public void testGetMultiple() throws JSONException, RestClientException {
        for (int i = 1; i <= 20; i++) {
            HttpResponse<JsonNode> response = client.get("http://httpbin.org/get?try=" + i).asJson();
            assertEquals(response.getBody().getObject().getJSONObject("args").getString("try"), ((Integer) i).toString());
        }
    }

    @Test
    public void testGetFields() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString("name", "mark").queryString("nick", "thefosk").asJson();
        assertEquals(response.getBody().getObject().getJSONObject("args").getString("name"), "mark");
        assertEquals(response.getBody().getObject().getJSONObject("args").getString("nick"), "thefosk");
    }

    @Test
    public void testGetFields2() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString("email", "hello@hello.com").asJson();
        assertEquals("hello@hello.com", response.getBody().getObject().getJSONObject("args").getString("email"));
    }

    @Test
    public void testQueryStringEncoding() throws JSONException, RestClientException {
        String testKey = "email2=someKey&email";
        String testValue = "hello@hello.com";
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString(testKey, testValue).asJson();
        assertEquals(testValue, response.getBody().getObject().getJSONObject("args").getString(testKey));
    }

    @Test
    public void testDelete() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.delete("http://httpbin.org/delete").asJson();
        assertEquals(200, response.getStatus());

        response = client.delete("http://httpbin.org/delete").field("name", "mark").asJson();
        assertEquals("mark", response.getBody().getObject().getJSONObject("form").getString("name"));
    }

    @Test
    public void testDeleteBody() throws JSONException, RestClientException {
        String body = "{\"jsonString\":{\"members\":\"members1\"}}";
        HttpResponse<JsonNode> response = client.delete("http://httpbin.org/delete").body(body).asJson();
        assertEquals(200, response.getStatus());
        assertEquals(body, response.getBody().getObject().getString("data"));
    }

    @Test
    public void testBasicAuth() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/headers").basicAuth("user", "test").asJson();
        assertEquals("Basic dXNlcjp0ZXN0", response.getBody().getObject().getJSONObject("headers").getString("Authorization"));
    }

    @Test
    public void testAsync() throws JSONException, InterruptedException, ExecutionException {
        Future<HttpResponse<JsonNode>> future = client.post("http://httpbin.org/post").header("accept", "application/json").field("param1", "value1").field("param2", "bye").asJsonAsync();

        assertNotNull(future);
        HttpResponse<JsonNode> jsonResponse = future.get();

        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertNotNull(json.getObject());
        assertNotNull(json.getArray());
        assertEquals(1, json.getArray().length());
        assertNotNull(json.getArray().get(0));
    }

    @Test
    public void testAsyncCallback() throws JSONException, InterruptedException, ExecutionException {
        client.post("http://httpbin.org/post").header("accept", "application/json").field("param1", "value1").field("param2", "bye").asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> jsonResponse) {
                assertTrue(jsonResponse.getHeaders().size() > 0);
                assertTrue(jsonResponse.getBody().toString().length() > 0);
                assertFalse(jsonResponse.getRawBody() == null);
                assertEquals(200, jsonResponse.getStatus());

                JsonNode json = jsonResponse.getBody();
                assertFalse(json.isArray());
                assertNotNull(json.getObject());
                assertNotNull(json.getArray());
                assertEquals(1, json.getArray().length());
                assertNotNull(json.getArray().get(0));

                assertEquals("value1", json.getObject().getJSONObject("form").getString("param1"));
                assertEquals("bye", json.getObject().getJSONObject("form").getString("param2"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }
        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testMultipart() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/post").field("name", "Mark").field("file", new File(getClass().getResource("/test").toURI())).asJson();
        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertNotNull(json.getObject());
        assertNotNull(json.getArray());
        assertEquals(1, json.getArray().length());
        assertNotNull(json.getArray().get(0));
        assertNotNull(json.getObject().getJSONObject("files"));

        assertEquals("This is a test file", json.getObject().getJSONObject("files").getString("file"));
        assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));
    }

    @Test
    public void testMultipartContentType() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/post").field("name", "Mark").field("file", new File(getClass().getResource("/image.jpg").toURI()), "image/jpeg").asJson();
        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertNotNull(json.getObject());
        assertNotNull(json.getArray());
        assertEquals(1, json.getArray().length());
        assertNotNull(json.getArray().get(0));
        assertNotNull(json.getObject().getJSONObject("files"));

        assertTrue(json.getObject().getJSONObject("files").getString("file").contains("data:image/jpeg"));
        assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));
    }

//    @Test
//    public void testMultipartInputStreamContentType() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException, FileNotFoundException {
//        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/post")
//                .field("name", "Mark")
//                .field("file", new FileInputStream(new File(getClass().getResource("/image.jpg").toURI())), ContentType.APPLICATION_OCTET_STREAM, "image.jpg")
//                .asJson();
//        assertTrue(jsonResponse.getHeaders().size() > 0);
//        assertTrue(jsonResponse.getBody().toString().length() > 0);
//        assertFalse(jsonResponse.getRawBody() == null);
//        assertEquals(200, jsonResponse.getStatus());
//
//        JsonNode json = jsonResponse.getBody();
//        assertFalse(json.isArray());
//        assertNotNull(json.getObject());
//        assertNotNull(json.getArray());
//        assertEquals(1, json.getArray().length());
//        assertNotNull(json.getArray().get(0));
//        assertNotNull(json.getObject().getJSONObject("files"));
//
//        assertTrue(json.getObject().getJSONObject("files").getString("file").contains("data:application/octet-stream"));
//        assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));
//    }

    @Test
    public void testMultipartInputStreamContentTypeAsync() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException, FileNotFoundException {
        client.post("http://httpbin.org/post").field("name", "Mark").field("file", new FileInputStream(new File(getClass().getResource("/test").toURI())), ContentType.APPLICATION_OCTET_STREAM, "test").asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> response) {
                assertTrue(response.getHeaders().size() > 0);
                assertTrue(response.getBody().toString().length() > 0);
                assertFalse(response.getRawBody() == null);
                assertEquals(200, response.getStatus());

                JsonNode json = response.getBody();
                assertFalse(json.isArray());
                assertNotNull(json.getObject());
                assertNotNull(json.getArray());
                assertEquals(1, json.getArray().length());
                assertNotNull(json.getArray().get(0));

                assertEquals("This is a test file", json.getObject().getJSONObject("files").getString("file"));
                assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }

        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testMultipartByteContentType() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException, IOException {
        final InputStream stream = new FileInputStream(new File(getClass().getResource("/image.jpg").toURI()));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();
        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/post").field("name", "Mark").field("file", bytes, "image.jpg").asJson();
        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertNotNull(json.getObject());
        assertNotNull(json.getArray());
        assertEquals(1, json.getArray().length());
        assertNotNull(json.getArray().get(0));
        assertNotNull(json.getObject().getJSONObject("files"));

        assertTrue(json.getObject().getJSONObject("files").getString("file").contains("data:application/octet-stream"));
        assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));
    }

    @Test
    public void testMultipartByteContentTypeAsync() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException, IOException {
        final InputStream stream = new FileInputStream(new File(getClass().getResource("/test").toURI()));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();
        client.post("http://httpbin.org/post").field("name", "Mark").field("file", bytes, "test").asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> response) {
                assertTrue(response.getHeaders().size() > 0);
                assertTrue(response.getBody().toString().length() > 0);
                assertFalse(response.getRawBody() == null);
                assertEquals(200, response.getStatus());

                JsonNode json = response.getBody();
                assertFalse(json.isArray());
                assertNotNull(json.getObject());
                assertNotNull(json.getArray());
                assertEquals(1, json.getArray().length());
                assertNotNull(json.getArray().get(0));

                assertEquals("This is a test file", json.getObject().getJSONObject("files").getString("file"));
                assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }

        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testMultipartAsync() throws JSONException, InterruptedException, ExecutionException, URISyntaxException, RestClientException {
        client.post("http://httpbin.org/post").field("name", "Mark").field("file", new File(getClass().getResource("/test").toURI())).asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> response) {
                assertTrue(response.getHeaders().size() > 0);
                assertTrue(response.getBody().toString().length() > 0);
                assertFalse(response.getRawBody() == null);
                assertEquals(200, response.getStatus());

                JsonNode json = response.getBody();
                assertFalse(json.isArray());
                assertNotNull(json.getObject());
                assertNotNull(json.getArray());
                assertEquals(1, json.getArray().length());
                assertNotNull(json.getArray().get(0));

                assertEquals("This is a test file", json.getObject().getJSONObject("files").getString("file"));
                assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }

        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testGzip() throws RestClientException, JSONException {
        HttpResponse<JsonNode> jsonResponse = client.get("http://httpbin.org/gzip").asJson();
        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertTrue(json.getObject().getBoolean("gzipped"));
    }

    @Test
    public void testGzipAsync() throws RestClientException, JSONException, InterruptedException, ExecutionException {
        HttpResponse<JsonNode> jsonResponse = client.get("http://httpbin.org/gzip").asJsonAsync().get();
        assertTrue(jsonResponse.getHeaders().size() > 0);
        assertTrue(jsonResponse.getBody().toString().length() > 0);
        assertFalse(jsonResponse.getRawBody() == null);
        assertEquals(200, jsonResponse.getStatus());

        JsonNode json = jsonResponse.getBody();
        assertFalse(json.isArray());
        assertTrue(json.getObject().getBoolean("gzipped"));
    }

    @Test
    public void testDefaultHeaders() throws RestClientException, JSONException, IOException {
        RestClient customClient = null;
        try {
            customClient = RestClient.newClient()
                    .defaultHeader("X-Custom-Header", "hello")
                    .defaultHeader("user-agent", "foobar")
                    .build();

            HttpResponse<JsonNode> jsonResponse = customClient.get("http://httpbin.org/headers").asJson();
            assertTrue(jsonResponse.getHeaders().size() > 0);
            assertTrue(jsonResponse.getBody().toString().length() > 0);
            assertFalse(jsonResponse.getRawBody() == null);
            assertEquals(200, jsonResponse.getStatus());

            JsonNode json = jsonResponse.getBody();
            assertFalse(json.isArray());
            assertTrue(jsonResponse.getBody().getObject().getJSONObject("headers").has("X-Custom-Header"));
            assertEquals("hello", json.getObject().getJSONObject("headers").getString("X-Custom-Header"));
            assertTrue(jsonResponse.getBody().getObject().getJSONObject("headers").has("User-Agent"));
            assertEquals("foobar", json.getObject().getJSONObject("headers").getString("User-Agent"));

            jsonResponse = customClient.get("http://httpbin.org/headers").asJson();
            assertTrue(jsonResponse.getBody().getObject().getJSONObject("headers").has("X-Custom-Header"));
            assertEquals("hello", jsonResponse.getBody().getObject().getJSONObject("headers").getString("X-Custom-Header"));
        } finally {
            customClient.shutdown();
        }

    }

    @Test
    public void testSetTimeouts() throws IOException {
        RestClient customClient = null;
        try {
            int timeout = 2000;
            customClient = RestClient.newClient().timeouts(timeout, 10000).build();
            String address = "http://" + findAvailableIpAddress() + "/";
            long start = System.currentTimeMillis();
            try {
                client.get("http://" + address + "/").asString();
            } catch (Exception e) {
                if (System.currentTimeMillis() - start >= timeout) { // Add 100ms for code execution
                    fail();
                }
            }

        } finally {
            customClient.shutdown();
        }

    }

    @Test
    public void testPathParameters() throws RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.get("http://httpbin.org/{method}").routeParam("method", "get").queryString("name", "Mark").asJson();

        assertEquals(200, jsonResponse.getStatus());
        assertEquals(jsonResponse.getBody().getObject().getJSONObject("args").getString("name"), "Mark");
    }

    @Test
    public void testQueryAndBodyParameters() throws RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.post("http://httpbin.org/{method}").routeParam("method", "post").queryString("name", "Mark").field("wot", "wat").asJson();

        assertEquals(200, jsonResponse.getStatus());
        assertEquals(jsonResponse.getBody().getObject().getJSONObject("args").getString("name"), "Mark");
        assertEquals(jsonResponse.getBody().getObject().getJSONObject("form").getString("wot"), "wat");
    }

    @Test
    public void testPathParameters2() throws RestClientException {
        HttpResponse<JsonNode> jsonResponse = client.patch("http://httpbin.org/{method}").routeParam("method", "patch").field("name", "Mark").asJson();

        assertEquals(200, jsonResponse.getStatus());
        assertEquals("OK", jsonResponse.getStatusText());
        assertEquals(jsonResponse.getBody().getObject().getJSONObject("form").getString("name"), "Mark");
    }

    @Test
    public void testMissingPathParameter() throws RestClientException {
        try {
            client.get("http://httpbin.org/{method}").routeParam("method222", "get").queryString("name", "Mark").asJson();
            fail();
        } catch (RuntimeException e) {
            // OK
        }
    }

//    @Test
//    public void parallelTest() throws Exception {
//        RestClient firstClient = null;
//        RestClient secondClient = null;
//        try {
//            firstClient = RestClient.newClient().concurrency(10).build();
//
//            long start = System.currentTimeMillis();
//            makeParallelRequests(firstClient);
//            long smallerConcurrencyTime = (System.currentTimeMillis() - start);
//
//            secondClient = RestClient.newClient().concurrency(20).build();
//            start = System.currentTimeMillis();
//            makeParallelRequests(secondClient);
//            long higherConcurrencyTime = (System.currentTimeMillis() - start);
//
//            assertTrue(higherConcurrencyTime < smallerConcurrencyTime);
//        } finally {
//            firstClient.shutdown();
//            secondClient.shutdown();
//        }
//
//    }

    private void makeParallelRequests(final RestClient restClient) throws InterruptedException {
        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(10);
        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 200; i++) {
            newFixedThreadPool.execute(() -> {
                try {
                    restClient.get("http://httpbin.org/get").queryString("index", counter.incrementAndGet()).asJson();
                } catch (RestClientException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        newFixedThreadPool.shutdown();
        newFixedThreadPool.awaitTermination(10, TimeUnit.MINUTES);
    }

    @Test
    public void testAsyncCustomContentType() throws InterruptedException {
        client.post("http://httpbin.org/post").header("accept", "application/json").header("Content-Type", "application/json").body("{\"hello\":\"world\"}").asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> jsonResponse) {
                JsonNode json = jsonResponse.getBody();
                assertEquals("{\"hello\":\"world\"}", json.getObject().getString("data"));
                assertEquals("application/json", json.getObject().getJSONObject("headers").getString("Content-Type"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }
        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testAsyncCustomContentTypeAndFormParams() throws InterruptedException {
        client.post("http://httpbin.org/post").header("accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded").field("name", "Mark").field("hello", "world").asJsonAsync(new Callback<JsonNode>() {

            public void failed(RestClientException e) {
                fail();
            }

            public void completed(HttpResponse<JsonNode> jsonResponse) {
                JsonNode json = jsonResponse.getBody();
                assertEquals("Mark", json.getObject().getJSONObject("form").getString("name"));
                assertEquals("world", json.getObject().getJSONObject("form").getString("hello"));

                assertEquals("application/x-www-form-urlencoded", json.getObject().getJSONObject("headers").getString("Content-Type"));

                status = true;
                lock.countDown();
            }

            public void cancelled() {
                fail();
            }
        });

        lock.await(10, TimeUnit.SECONDS);
        assertTrue(status);
    }

    @Test
    public void testGetQuerystringArray() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString("name", "Mark").queryString("name", "Tom").asJson();

        JSONArray names = response.getBody().getObject().getJSONObject("args").getJSONArray("name");
        assertEquals(2, names.length());

        assertEquals("Mark", names.getString(0));
        assertEquals("Tom", names.getString(1));
    }

    @Test
    public void testPostMultipleFiles() throws JSONException, RestClientException, URISyntaxException {
        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").field("param3", "wot").field("file1", new File(getClass().getResource("/test").toURI())).field("file2", new File(getClass().getResource("/test").toURI())).asJson();

        JSONObject names = response.getBody().getObject().getJSONObject("files");
        assertEquals(2, names.length());

        assertEquals("This is a test file", names.getString("file1"));
        assertEquals("This is a test file", names.getString("file2"));

        assertEquals("wot", response.getBody().getObject().getJSONObject("form").getString("param3"));
    }

    @Test
    public void testGetArray() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.get("http://httpbin.org/get").queryString("name", Arrays.asList("Mark", "Tom")).asJson();

        JSONArray names = response.getBody().getObject().getJSONObject("args").getJSONArray("name");
        assertEquals(2, names.length());

        assertEquals("Mark", names.getString(0));
        assertEquals("Tom", names.getString(1));
    }

    @Test
    public void testPostArray() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").field("name", "Mark").field("name", "Tom").asJson();

        JSONArray names = response.getBody().getObject().getJSONObject("form").getJSONArray("name");
        assertEquals(2, names.length());

        assertEquals("Mark", names.getString(0));
        assertEquals("Tom", names.getString(1));
    }

    @Test
    public void testPostCollection() throws JSONException, RestClientException {
        HttpResponse<JsonNode> response = client.post("http://httpbin.org/post").field("name", Arrays.asList("Mark", "Tom")).asJson();

        JSONArray names = response.getBody().getObject().getJSONObject("form").getJSONArray("name");
        assertEquals(2, names.length());

        assertEquals("Mark", names.getString(0));
        assertEquals("Tom", names.getString(1));
    }

    @Test
    public void testCaseInsensitiveHeaders() throws RestClientException {
        GetRequest request = client.get("http://httpbin.org/headers").header("Name", "Marco");
        assertEquals(1, request.getHeaders().size());
        assertEquals("Marco", request.getHeaders().get("name").get(0));
        assertEquals("Marco", request.getHeaders().get("NAme").get(0));
        assertEquals("Marco", request.getHeaders().get("Name").get(0));
        JSONObject headers = request.asJson().getBody().getObject().getJSONObject("headers");
        assertEquals("Marco", headers.getString("Name"));

    }

//    @Test
//    public void multipleHeaders() {
//        GetRequest request = client.get("http://localhost:9000").header("Name", "Marco").header("Name", "John");
//        assertEquals(1, request.getHeaders().size());
//        assertEquals("Marco", request.getHeaders().get("name").get(0));
//        assertEquals("John", request.getHeaders().get("name").get(1));
//        assertEquals("Marco", request.getHeaders().get("NAme").get(0));
//        assertEquals("John", request.getHeaders().get("NAme").get(1));
//        assertEquals("Marco", request.getHeaders().get("Name").get(0));
//        assertEquals("John", request.getHeaders().get("Name").get(1));
//
//        JSONObject headers = request.asJson().getBody().getObject().getJSONObject("headers");
//        assertEquals("Marco,John", headers.get("Name"));
//    }


    @Test
    public void multipleClients() throws RestClientException, IOException {
        RestClient client1 = RestClient.newClient().build();
        int status = client1.get("http://httpbin.org/get").asString().getStatus();
        assertEquals(200, status);

        RestClient client2 = RestClient.newClient().build();
        status = client2.get("http://httpbin.org/get").asString().getStatus();
        assertEquals(200, status);

        RestClient client3 = RestClient.newClient().build();
        status = client3.get("http://httpbin.org/get").asString().getStatus();
        assertEquals(200, status);

        client1.shutdown();
        client2.shutdown();
        client3.shutdown();
    }

    //FIXME
//    @Test
//    public void retry() throws RestClientException, IOException {
//        RestClient retryClient = RestClient.newClient()
//                .retryPolicy(new RetryPolicy().withMaxRetries(2))
//                .build();
//        int status = retryClient.get("http://dummy-url.abc").asString().getStatus();
//        assertEquals(200, status);
//    }

    @Test
    public void fallbackResponse() throws RestClientException, IOException {
        String fallback = "FALLBACK-DATA";
        RestClient retryClient = RestClient.newClient().build();

        HttpResponse<String> fallbackResponse = retryClient.get("http://dummy-url.abc")
                .withFallback(fallback)
                .asString();

        assertEquals(fallback, fallbackResponse.getBody());
    }

//
//    @Test
//    public void setTimeoutsAndCustomClient() {
//        try {
//            client.setTimeouts(1000, 2000);
//        } catch (Exception e) {
//            fail();
//        }
//
//        try {
//            client.setAsyncHttpClient(HttpAsyncClientBuilder.create().build());
//        } catch (Exception e) {
//            fail();
//        }
//
//        try {
//            client.setAsyncHttpClient(HttpAsyncClientBuilder.create().build());
//            client.setTimeouts(1000, 2000);
//            fail();
//        } catch (Exception e) {
//            // Ok
//        }
//
//        try {
//            client.setHttpClient(HttpClientBuilder.create().build());
//            client.setTimeouts(1000, 2000);
//            fail();
//        } catch (Exception e) {
//            // Ok
//        }
//    }

    @Test
    public void testObjectMapperRead() throws RestClientException, IOException {
        RestClient customClient = null;
        try {
            customClient = RestClient.newClient()
                    .objectMapper(new JsonMapper())
                    .build();


            GetResponse getResponseMock = new GetResponse();
            getResponseMock.setUrl("http://httpbin.org/get");

            HttpResponse<GetResponse> getResponse = customClient.get(getResponseMock.getUrl()).asObject(GetResponse.class);

            assertEquals(200, getResponse.getStatus());
            assertEquals(getResponse.getBody().getUrl(), getResponseMock.getUrl());
        } finally {
            customClient.shutdown();
        }
    }

    @Test
    public void testObjectMapperWrite() throws RestClientException, IOException {
        RestClient customClient = null;
        try {
            customClient = RestClient.newClient()
                    .objectMapper(new JsonMapper())
                    .build();

            GetResponse postResponseMock = new GetResponse();
            postResponseMock.setUrl("http://httpbin.org/post");

            HttpResponse<JsonNode> postResponse = customClient.post(postResponseMock.getUrl())
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(postResponseMock)
                    .asJson();

            assertEquals(200, postResponse.getStatus());
            assertEquals(postResponse.getBody().getObject().getString("data"), "{\"url\":\"http://httpbin.org/post\"}");
        } finally {
            customClient.shutdown();
        }

    }

    @Test
    public void testPostProvidesSortedParams() throws IOException {
        // Verify that fields are encoded into the body in sorted order.
        HttpRequest httpRequest = client.post("test").field("z", "Z").field("y", "Y").field("x", "X").getHttpRequest();

        InputStream content = httpRequest.getBody().getEntity().getContent();
        String body = IOUtils.toString(content, "UTF-8");
        assertEquals("x=X&y=Y&z=Z", body);
    }

    @Test
    public void testHeaderNamesCaseSensitive() {
        // Verify that header names are the same as server (case sensitive)
        final Headers headers = new Headers();
        headers.put("Content-Type", Arrays.asList("application/json"));

        assertEquals("Only header \"Content-Type\" should exist", null, headers.getFirst("cOnTeNt-TyPe"));
        assertEquals("Only header \"Content-Type\" should exist", null, headers.getFirst("content-type"));
        assertEquals("Only header \"Content-Type\" should exist", "application/json", headers.getFirst("Content-Type"));
    }

    @Test
    public void circuitBreaker() throws Exception {
        RestClient customClient = null;
        try {
            CircuitBreaker circuitBreaker = new CircuitBreaker()
                    .withFailureThreshold(1);

            customClient = RestClient.newClient()
                    .baseUrl("http://invalid-url.abc")
                    .circuitBreaker(circuitBreaker)
                    .build();


            assertTrue(circuitBreaker.isClosed());
            try {
                customClient.get("/dummy").asJson();
                customClient.get("/dummy").asJson();
            } catch (Exception ignored) {

            }
            assertTrue(circuitBreaker.isOpen());


        } finally {
            customClient.shutdown();
        }
    }

    //TODO add to all other methods
    @Test
    public void baseUrl() throws Exception {
        RestClient customClient = null;
        try {
            customClient = RestClient.newClient()
                    .baseUrl("http://httpbin.org")
                    .build();


            HttpResponse<JsonNode> postResponse = customClient.get("/get")
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .asJson();

            assertEquals(200, postResponse.getStatus());
        } finally {
            customClient.shutdown();
        }
    }
}