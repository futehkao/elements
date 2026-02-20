package net.e6tech.elements.network.restful;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.ServiceUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestfulClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<CapturedRequest> lastRequest = new AtomicReference<>();

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void roundTripRequestsAndPrinting() throws Exception {
        startServer();
        String baseUrl = "http://127.0.0.1:" + port + "/api";

        RestfulClient client = new RestfulClient(baseUrl);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        client.setPrinter(new PrintWriter(output, true));
        client.setPrintRawResponse(true);
        client.setConnectionTimeout(1_000);
        client.setReadTimeout(1_000);
        client.setSkipHostnameCheck(false);
        client.setSkipCertCheck(false);
        client.setEncoding("UTF-8");
        client.TLSProtocol("TLS");
        client.proxyPort(-1);
        client.proxyHost(null);
        client.privateKeyPassword("secret".toCharArray());

        assertEquals(baseUrl, client.getAddress());
        assertEquals("UTF-8", client.getEncoding());
        assertEquals("TLS", client.getTLSProtocol());
        assertTrue(client.getPrivateKeyPassword() == null || client.getPrivateKeyPassword().length > 0);

        Response getResponse = client.get("/echo/", new QueryBean("alpha value", 7, null));
        assertEquals(200, getResponse.getResponseCode());
        assertTrue(getResponse.getResult().contains("\"method\":\"GET\""));
        assertTrue(lastRequest.get().query.contains("value=alpha+value"));
        assertTrue(lastRequest.get().query.contains("number=7"));

        Response getWithNullObject = client.get("/echo", (Object) null);
        assertEquals(200, getWithNullObject.getResponseCode());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer hidden-token");
        headers.put("Accept", "text/plain");
        headers.put("Content-Type", "text/plain");

        Request request = client.create().setRequestProperties(headers);
        Response postResponse = request.post("/echo", Map.of("k", "v"), new Param("x", "y"));
        assertEquals(200, postResponse.getResponseCode());
        assertEquals("text/plain", lastRequest.get().headers.get("accept"));
        assertEquals("text/plain", lastRequest.get().headers.get("content-type"));

        PostData customEncoded = new PostData(Map.of("from", "encoder"));
        customEncoded.setEncoder(new RequestEncoder() {
            @Override
            public String getContentType() {
                return "application/custom";
            }

            @Override
            public String encodeRequest(Object data) {
                return "ENCODED";
            }
        });
        Response customResponse = client.create().request("/echo", "POST", customEncoded, new Param("qp", "ok"));
        assertEquals(200, customResponse.getResponseCode());
        assertEquals("application/custom", lastRequest.get().headers.get("content-type"));
        assertEquals("ENCODED", lastRequest.get().body);

        Response putResponse = client.put("/echo", Map.of("a", 1), new Param("p", "q"));
        assertEquals(200, putResponse.getResponseCode());

        Response deleteResponse = client.delete("/echo", Map.of("delete", true), new Param("d", "1"));
        assertEquals(200, deleteResponse.getResponseCode());

        Response noContent = client.get("/status/204");
        assertEquals(204, noContent.getResponseCode());
        assertNull(noContent.getResult());

        assertThrows(SystemException.class, () -> client.get("/echo", new ThrowingBean()));

        String printed = output.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("REQUEST ----------------------------"));
        assertTrue(printed.contains("RESPONSE ----------------------------"));
        assertTrue(printed.contains("Bearer ..."));
        assertTrue(printed.contains("===== RAW RESPONSE: START ====="));
    }

    @Test
    void exceptionMapperAndMarshallerBehaviors() throws Exception {
        startServer();
        RestfulClient client = new RestfulClient("http://127.0.0.1:" + port + "/api");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        client.setPrinter(new PrintWriter(output, true));

        assertThrows(BadRequestException.class, () -> client.get("/status/400"));

        RecordingMarshaller marshaller = new RecordingMarshaller();
        client.setMarshaller(marshaller);
        client.setExceptionMapper(new ApiErrorMapper(false));
        assertEquals(ApiError.class, marshaller.errorClass);

        IllegalStateException mapped = assertThrows(IllegalStateException.class,
                () -> client.get("/status/400"));
        assertEquals("mapped:broken", mapped.getMessage());

        client.setExceptionMapper(new ApiErrorMapper(true));
        SystemException wrapped = assertThrows(SystemException.class, () -> client.get("/status/400"));
        assertTrue(wrapped.getCause() instanceof AssertionError);

        client.setExceptionMapper(new ApiErrorMapper(false));
        client.marshaller(new JsonMarshaller<>());
        BadRequestException fallback = assertThrows(BadRequestException.class,
                () -> client.get("/status/400-text"));
        assertTrue(fallback.getMessage() == null || !fallback.getMessage().isEmpty());

        String printed = output.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("FAILURE -----------------------------"));
        assertTrue(printed.contains("ERROR RESPONSE ----------------------"));
    }

    @Test
    void statusCodeMappingsAndValidationPaths() throws Exception {
        startServer();
        RestfulClient client = new RestfulClient("http://127.0.0.1:" + port + "/api");
        client.setPrinter(new PrintWriter(new ByteArrayOutputStream(), true));

        assertStatus(client, 401, NotAuthorizedException.class);
        assertStatus(client, 402, ForbiddenException.class);
        assertStatus(client, 403, ForbiddenException.class);
        assertStatus(client, 404, NotFoundException.class);
        assertStatus(client, 302, NotFoundException.class);
        assertStatus(client, 405, NotAllowedException.class);
        assertStatus(client, 406, NotAcceptableException.class);
        assertStatus(client, 415, NotSupportedException.class);
        assertStatus(client, 409, IllegalArgumentException.class);
        assertStatus(client, 422, BadRequestException.class);
        assertStatus(client, 499, IllegalArgumentException.class);
        assertStatus(client, 500, InternalServerErrorException.class);
        assertStatus(client, 503, ServiceUnavailableException.class);

        Request nullHeaderKey = client.create();
        nullHeaderKey.setRequestProperty(null, "v");
        assertThrows(BadRequestException.class, () -> nullHeaderKey.get("/echo"));

        Request nullHeaderValue = client.create();
        nullHeaderValue.setRequestProperty("k", null);
        assertThrows(BadRequestException.class, () -> nullHeaderValue.get("/echo"));
    }

    @Test
    void proxyRefreshAndSslErrorBranches() throws Exception {
        startServer();
        String baseUrl = "http://127.0.0.1:" + port + "/api";

        RestfulClient client = new RestfulClient(baseUrl);
        client.setProxyHost("127.0.0.1");
        client.setProxyPort(port);
        client.setConnectionTimeout(1_000);
        client.setReadTimeout(1_000);
        client.skipHostnameCheck(true);
        client.skipCertCheck(true);

        Response proxied = client.get("/echo", new Param("via", "proxy"));
        assertEquals(200, proxied.getResponseCode());
        assertTrue(lastRequest.get().path.contains("/api/echo"));

        client.refreshClient();
        Response afterRefresh = client.get("/echo", new Param("again", "yes"));
        assertEquals(200, afterRefresh.getResponseCode());

        client.refreshClient();
        client.setTrustStore("does-not-exist.jks");
        client.setTrustStorePassword("bad".toCharArray());
        assertThrows(SystemException.class, () -> client.get("/echo"));

        RestfulClient encodingFallback = new RestfulClient(baseUrl);
        encodingFallback.setEncoding("INVALID-ENCODING");
        Response encodingResponse = encodingFallback.post("/status/204", Map.of("x", "y"));
        assertEquals(204, encodingResponse.getResponseCode());
    }

    private void assertStatus(RestfulClient client, int code, Class<? extends Throwable> expected) {
        Throwable ex = assertThrows(Throwable.class, () -> client.get("/status/" + code));
        assertEquals(expected, ex.getClass());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        port = server.getAddress().getPort();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        lastRequest.set(new CapturedRequest(exchange.getRequestMethod(), path, query, body, headers));

        if (path.endsWith("/echo")) {
            String response = "{\"method\":\"" + exchange.getRequestMethod() + "\",\"path\":\"" + path + "\"}";
            send(exchange, 200, "application/json", response);
            return;
        }

        if (path.contains("/status/")) {
            String codeText = path.substring(path.lastIndexOf('/') + 1);
            int statusCode;
            try {
                statusCode = Integer.parseInt(codeText);
            } catch (NumberFormatException ex) {
                statusCode = 500;
            }

            if (path.endsWith("/400")) {
                send(exchange, 400, "application/json", "{\"message\":\"broken\"}");
                return;
            }
            if (path.endsWith("/400-text")) {
                send(exchange, 400, "text/plain", "broken-text");
                return;
            }
            if (statusCode == 204 || statusCode == 205 || statusCode == 304) {
                exchange.sendResponseHeaders(statusCode, -1);
                exchange.close();
                return;
            }
            send(exchange, statusCode, "application/json", "{\"status\":" + statusCode + "}");
            return;
        }

        send(exchange, 404, "text/plain", "not-found");
    }

    private void send(HttpExchange exchange, int status, String contentType, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Test", "yes");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static class CapturedRequest {
        private final String method;
        private final String path;
        private final String query;
        private final String body;
        private final Map<String, String> headers;

        CapturedRequest(String method, String path, String query, String body, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.body = body;
            this.headers = headers;
        }
    }

    static class QueryBean {
        private final String value;
        private final int number;
        private final String nullable;

        QueryBean(String value, int number, String nullable) {
            this.value = value;
            this.number = number;
            this.nullable = nullable;
        }

        public String getValue() {
            return value;
        }

        public int getNumber() {
            return number;
        }

        public String getNullable() {
            return nullable;
        }
    }

    static class ThrowingBean {
        public String getValue() {
            throw new IllegalStateException("bad getter");
        }
    }

    static class ApiError {
        public String message;
    }

    static class ApiErrorMapper implements ExceptionMapper<ApiError> {
        private final boolean returnNonException;

        ApiErrorMapper(boolean returnNonException) {
            this.returnNonException = returnNonException;
        }

        @Override
        public Class<ApiError> errorResponseClass() {
            return ApiError.class;
        }

        @Override
        public ApiError toResponse(Throwable exception) {
            ApiError error = new ApiError();
            error.message = exception.getMessage();
            return error;
        }

        @Override
        public Throwable fromResponse(ApiError response) {
            if (returnNonException) {
                return new AssertionError("mapped-assertion");
            }
            return new IllegalStateException("mapped:" + response.message);
        }
    }

    static class RecordingMarshaller implements Marshaller<ApiError> {
        private Class<ApiError> errorClass;

        @Override
        public String getAccept() {
            return "application/json";
        }

        @Override
        public String prettyPrintRequest(Object data) {
            return String.valueOf(data);
        }

        @Override
        public Object prettyPrintResponse(String response) {
            return response;
        }

        @Override
        public void errorResponseClass(Class<ApiError> errorResponseClass) {
            this.errorClass = errorResponseClass;
        }

        @Override
        public ApiError readErrorResponse(String errorResponse) {
            ApiError error = new ApiError();
            error.message = "broken";
            return error;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String encodeRequest(Object data) {
            return "{\"ok\":true}";
        }
    }
}
