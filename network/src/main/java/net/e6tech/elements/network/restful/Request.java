package net.e6tech.elements.network.restful;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class Request {

    private static final Presentation singleton = new Presentation() {};

    static final String GET = "GET";
    static final String PUT = "PUT";
    static final String POST = "POST";
    static final String DELETE = "DELETE";

    private RestfulClient client;
    private Map<String, String> requestProperties = new LinkedHashMap<>();
    private Presentation presentation = singleton; // similar to OSI Presentation.  Used to format data

    Request(RestfulClient client) {
        this.client = client;
    }

    public Request setRequestProperty(String key, String value) {
        requestProperties.put(key, value);
        return this;
    }

    public Request setRequestProperties(Map<String, String> map) {
        requestProperties.putAll(map);
        return this;
    }

    public Map<String, String> getRequestProperties() {
        return Collections.unmodifiableMap(requestProperties);
    }

    public void clearRequestProperty(String key) {
        requestProperties.remove(key);
    }

    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    public Response get(String context, Param ... params) throws Exception {
        return request(context, GET, new PostData(), params);
    }

    public Response delete(String context, Object data, Param ... params) throws Exception {
        return request(context, DELETE, new PostData(data), params);
    }

    public Response delete(String context, Param ... params) throws Exception {
        return request(context, DELETE, new PostData(), params);
    }

    public Response put(String context, Object data,  Param ... params) throws Exception {
        return request(context, PUT, new PostData(data), params);
    }

    public Response post(String context, Object data,  Param ... params) throws Exception {
        return request(context, POST, new PostData(data), params);
    }

    public Presentation getPresentation() {
        return presentation;
    }

    public void setPresentation(Presentation presentation) {
        this.presentation = presentation;
    }

    /**
     *
     * @param context full path
     * @param method POST, GET etc
     * @param postData post data if any
     * @param params query params
     * @return Response
     */
    @SuppressWarnings("squid:S00112")
    public Response request(String context, String method, PostData postData, Param ... params) throws Exception {
        getPresentation().formatRequest(this);
        if (postData == null)
            postData = new PostData();

        if (postData.isSpecified())
            postData.setData(getPresentation().formatPostData(postData.getData()));
        Response response = client.submit(context, method, requestProperties,
                postData,
                getPresentation().formatQuery(params));
        return getPresentation().formatResponse(response);
    }
}
