package net.e6tech.elements.network.restful;

import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.ErrorResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by futeh.
 */
public class Request {
    public static final UUID uuid = UUID.randomUUID();
    public static final String HTTP_LOOPBACK_POSTFIX = "-" + Long.toString(Math.abs(uuid.getMostSignificantBits()), 36) +
            "-" + Long.toString(Math.abs(uuid.getLeastSignificantBits()), 36);

    private static final Presentation singleton = new Presentation() {};

    static final String GET = "GET";
    static final String PUT = "PUT";
    static final String PATCH = "PATCH";
    static final String POST = "POST";
    static final String DELETE = "DELETE";

    private RestfulClient client;
    private Map<String, String> requestProperties = new LinkedHashMap<>();
    private Presentation presentation = singleton; // similar to OSI Presentation.  Used to format data
    private RequestEncoder encoder;

    public static String getHeader(Class cls) {
        return cls.getSimpleName() + HTTP_LOOPBACK_POSTFIX;
    }

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

    public RequestEncoder getPayloadEncoder() {
        return encoder;
    }

    public void setPayloadEncoder(RequestEncoder encoder) {
        this.encoder = encoder;
    }

    public Response get(String context, Param ... params) throws Exception {
        return sendWithPayload(context, GET, new PostData(), params);
    }

    public Response delete(String context, Object data, Param ... params) throws Exception {
        return sendWithPayload(context, DELETE, toPostData(data), params);
    }

    public Response delete(String context, Param ... params) throws Exception {
        return sendWithPayload(context, DELETE, new PostData(), params);
    }

    public Response put(String context, Object data,  Param ... params) throws Exception {
        return sendWithPayload(context, PUT, toPostData(data), params);
    }

    public Response patch(String context, Object data,  Param ... params) throws Exception {
        return sendWithPayload(context, PATCH, toPostData(data), params);
    }

    public Response post(String context, Object data,  Param ... params) throws Exception {
        return sendWithPayload(context, POST, toPostData(data), params);
    }

    private PostData toPostData(Object data) {
        if (data instanceof PostData)
            return (PostData) data;
        return new PostData(data);
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
     * @param params query params
     * @return Response
     */
    @SuppressWarnings("squid:S00112")
    public Response sendWithoutPayload(String context, String method, Param ... params) throws Exception {
        return sendWithPayload(context, method, new PostData(), params);
    }

    /**
     *
     * @param context full path
     * @param method POST, GET etc
     * @param data post data if any
     * @param params query params
     * @return Response
     */
    @SuppressWarnings("squid:S00112")
    public Response sendWithPayload(String context, String method, Object data, Param ... params) throws Exception {
        PostData postData;
        if (method.equals(GET) ) {  // GET doesn't take data.
            postData = new PostData();
        } else {
            postData = toPostData(data);
        }

        if (postData.getEncoder() == null)
            postData.setEncoder(getPayloadEncoder());

        getPresentation().formatRequest(this);

        if (postData.isSpecified())
            postData.setData(getPresentation().formatPostData(postData.getData()));
        Response response = client.submit(context, method, requestProperties,
                postData,
                getPresentation().formatQuery(params));
        return getPresentation().formatResponse(response);
    }
}
