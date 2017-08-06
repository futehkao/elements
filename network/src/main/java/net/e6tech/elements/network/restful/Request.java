package net.e6tech.elements.network.restful;

import java.util.Properties;

/**
 * Created by futeh.
 */
public class Request {

    static final String GET = "GET";
    static final String PUT = "PUT";
    static final String POST = "POST";
    static final String DELETE = "DELETE";
    static final int RETRY = 4;

    private RestfulClient client;
    private Properties requestProperties = new Properties();

    Request(RestfulClient client) {
        this.client = client;
    }

    public Request setRequestProperty(String key, String value) {
        requestProperties.setProperty(key, value);
        return this;
    }

    public Response get(String context, Param ... params) throws Throwable {
        return request(context, GET, null, params);
    }

    public Response delete(String context, Param ... params) throws Throwable {
        return request(context, DELETE, null, params);
    }

    public Response put(String context, Object data,  Param ... params) throws Throwable {
        return request(context, PUT, data, params);
    }

    public Response post(String context, Object data,  Param ... params) throws Throwable {
        return request(context, POST, data, params);
    }

    public Response request(String context, String method, Object data,  Param ... params) throws Throwable {
        return client.submit(context, method, requestProperties, data, params);
    }
}
