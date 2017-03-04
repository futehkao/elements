package net.e6tech.elements.network.restful;

import net.e6tech.elements.common.logging.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Properties;

/**
 * Created by futeh.
 */
public class Request {

    private static final String GET = "GET";
    private static final String PUT = "PUT";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";
    private static final int RETRY = 4;

    private static Logger logger = Logger.getLogger();

    private RestfulClient client;
    private String encoding = "UTF-8";
    private Properties requestProperties = new Properties();

    Request(RestfulClient client) {
        this.client = client;
    }

    public Request setRequestProperty(String key, String value) {
        requestProperties.setProperty(key, value);
        return this;
    }

    private void setConnectionProperties(HttpURLConnection conn) throws ProtocolException {
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Accept", "application/json");
    }

    private void loadRequestProperties(HttpURLConnection conn) {
        for (String key : requestProperties.stringPropertyNames()) {
            conn.setRequestProperty(key, requestProperties.getProperty(key));
        }
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
