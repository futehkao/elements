/*
Copyright 2015-2019 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.network.restful;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.ErrorResponse;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.JavaKeyStore;
import net.e6tech.elements.security.SSLSocketConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.*;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;

/**
 * Created by futeh.
 */
@SuppressWarnings({"unchecked", "squid:S3776", "squid:S00117", "squid:S00116", "squid:S00100"})
public class RestfulClient {

    private static Logger logger = Logger.getLogger();
    private static Field urlMethod;

    private ExceptionMapper exceptionMapper;
    private String staticAddress;
    private String encoding = StandardCharsets.UTF_8.name();
    private String trustStore;
    private String trustStoreFormat = JavaKeyStore.DEFAULT_FORMAT;
    private char[] trustStorePassword;
    private char[] privateKeyPassword;
    private String TLSProtocol = "TLS";
    private boolean skipHostnameCheck = false;
    private boolean skipCertCheck = false;
    private SSLSocketFactory sslSocketFactory;
    private int connectionTimeout = -1;
    private int readTimeout = -1;
    private PrintWriter printer;
    private boolean printRawResponse = false;
    private String proxyHost;
    private int proxyPort = -1;
    private Marshaller marshaller = new JsonMarshaller<>(ErrorResponse.class);


    public RestfulClient() {
    }

    public RestfulClient(String address) {
        setAddress(address);
    }

    public ExceptionMapper getExceptionMapper() {
        return exceptionMapper;
    }

    public <R> void setExceptionMapper(ExceptionMapper<R> exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
        if (exceptionMapper != null && marshaller != null) {
            Class<R> errorResponseClass = exceptionMapper.errorResponseClass();
            marshaller.errorResponseClass(errorResponseClass);
        }
    }

    public RestfulClient exceptionMapper(ExceptionMapper exceptionMapper) {
        setExceptionMapper(exceptionMapper);
        return this;
    }

    public synchronized String getAddress() {
        return staticAddress;
    }

    public synchronized void setAddress(String path) {
        this.staticAddress = path;
    }

    public RestfulClient address(String path) {
        setAddress(path);
        return this;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public RestfulClient encoding(String encoding) {
        setEncoding(encoding);
        return this;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        sslSocketFactory = null;
        this.trustStore = trustStore;
    }

    public RestfulClient trustStore(String trustStore) {
        setTrustStore(trustStore);
        return this;
    }

    public String getTrustStoreFormat() {
        return trustStoreFormat;
    }

    public void setTrustStoreFormat(String trustStoreFormat) {
        this.trustStoreFormat = trustStoreFormat;
    }

    public RestfulClient trustStoreFormat(String trustStoreFormat) {
        setTrustStoreFormat(trustStoreFormat);
        return this;
    }

    public char[] getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(char[] trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public RestfulClient trustStorePassword(char[] trustStorePassword) {
        setTrustStorePassword(trustStorePassword);
        return this;
    }

    public boolean isSkipHostnameCheck() {
        return skipHostnameCheck;
    }

    public void setSkipHostnameCheck(boolean skipHostnameCheck) {
        sslSocketFactory = null;
        this.skipHostnameCheck = skipHostnameCheck;
    }

    public RestfulClient skipHostnameCheck(boolean skipHostnameCheck) {
        setSkipHostnameCheck(skipHostnameCheck);
        return this;
    }

    public boolean isSkipCertCheck() {
        return skipCertCheck;
    }

    public void setSkipCertCheck(boolean skipCertCheck) {
        sslSocketFactory = null;
        this.skipCertCheck = skipCertCheck;
    }

    public RestfulClient skipCertCheck(boolean skipCertCheck) {
        setSkipCertCheck(skipCertCheck);
        return this;
    }

    public char[] getPrivateKeyPassword() {
        return privateKeyPassword;
    }

    public void setPrivateKeyPassword(char[] privateKeyPassword) {
        this.privateKeyPassword = privateKeyPassword;
    }

    public RestfulClient privateKeyPassword(char[] password) {
        setPrivateKeyPassword(password);
        return this;
    }

    public String getTLSProtocol() {
        return TLSProtocol;
    }

    public void setTLSProtocol(String TLSProtocol) {
        this.TLSProtocol = TLSProtocol;
    }

    public RestfulClient TLSProtocol(String TLSProtocol) {
        setTLSProtocol(TLSProtocol);
        return this;
    }

    public PrintWriter getPrinter() {
        return printer;
    }

    @Inject(optional = true)
    public void setPrinter(PrintWriter printer) {
        this.printer = printer;
    }

    public boolean isPrintRawResponse() {
        return printRawResponse;
    }

    public void setPrintRawResponse(boolean printRawResponse) {
        this.printRawResponse = printRawResponse;
    }

    public RestfulClient printRawResponse(boolean printRawResponse) {
        setPrintRawResponse(printRawResponse);
        return this;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public RestfulClient connectionTimeout(int connectionTimeout) {
        setConnectionTimeout(connectionTimeout);
        return this;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public RestfulClient readTimeout(int readTimeout) {
        setReadTimeout(readTimeout);
        return this;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public RestfulClient proxyHost(String proxyHost) {
        setProxyHost(proxyHost);
        return this;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public RestfulClient proxyPort(int proxyPort) {
        setProxyPort(proxyPort);
        return this;
    }

    public Marshaller getMarshaller() {
        return marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public <R> RestfulClient marshaller(Marshaller<R> marshaller) {
        setMarshaller(marshaller);
        if (exceptionMapper != null && marshaller != null) {
            Class<R> errorResponseClass = exceptionMapper.errorResponseClass();
            marshaller.errorResponseClass(errorResponseClass);
        }
        return this;
    }

    @SuppressWarnings("squid:S134")
    private Param[] toParams(Object object) {

        if (object instanceof Param) {
            return new Param[]{(Param) object};
        }

        List<Param> params = new ArrayList<>();
        if (object != null) {
            Class cls = object.getClass();
            BeanInfo beanInfo = null;
            try {
                beanInfo = Introspector.getBeanInfo(cls);
            } catch (IntrospectionException e) {
                throw new SystemException(e);
            }
            for (PropertyDescriptor desc : beanInfo.getPropertyDescriptors()) {
                if (desc.getReadMethod() != null) {
                    try {
                        Object value = desc.getReadMethod().invoke(object);
                        if (value != null)
                            params.add(new Param(desc.getName(), value.toString()));
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }
            }
        }
        return params.toArray(new Param[params.size()]);
    }

    public Request create() {
        return new Request(this);
    }

    public Response get(String context, Object object) throws Exception {
        return get(context, toParams(object)); // toParams make sure it calls the right vararg method
    }

    public Response get(String context, Param... params) throws Exception {
        return new Request(this).get(context, params);
    }

    public Response delete(String context, Object data, Param... params) throws Exception {
        return new Request(this).delete(context, data, params);
    }

    public Response delete(String context, Param... params) throws Exception {
        return new Request(this).delete(context, params);
    }

    public Response put(String context, Object data, Object object) throws Exception {
        return put(context, data, toParams(object)); // toParams make sure it calls the right vararg method
    }

    public Response put(String context, Object data, Param... params) throws Exception {
        return new Request(this).put(context, data, params);
    }

    public Response post(String context, Object data, Object object) throws Exception {
        return post(context, data, toParams(object)); // toParams make sure it calls the right vararg method
    }

    public Response post(String context, Object data, Param... params) throws Exception {
        return new Request(this).post(context, data, params);
    }

    private String constructPath(String destination, String ctx, Param... params) {
        String dest = destination;
        String context = ctx;
        String fullPath = null;

        if (!dest.endsWith("/"))
            dest = dest + "/";

        if (context != null) {
            while (context.startsWith("/"))
                context = context.substring(1);
        }

        fullPath = dest + context;

        while (fullPath.endsWith("/"))
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        if (params != null) {
            StringBuilder builder = new StringBuilder();
            List<Param> list = new ArrayList<>();
            for (Param param : params)
                if (param.getValue() != null)
                    list.add(param);
            for (int i = 0; i < list.size(); i++) {
                if (i == 0)
                    builder.append("?");
                builder.append(list.get(i).encode());
                if (i != list.size() - 1)
                    builder.append("&");
            }
            fullPath = fullPath + builder.toString();
        }
        return fullPath;
    }

    @SuppressWarnings("squid:S3510")
    HttpURLConnection open(String dest, String context, Param... params) throws IOException {
        String fullPath = constructPath(dest, context, params);
        URL url = null;
        try {
            logger.debug(fullPath);
            url = new URL(fullPath);

            HttpURLConnection conn = null;
            if (proxyHost != null && proxyPort > 0) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                conn = (HttpURLConnection) url.openConnection(proxy);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }

            if (connectionTimeout >= 0)
                conn.setConnectTimeout(connectionTimeout);
            if (readTimeout >= 0)
                conn.setReadTimeout(readTimeout);
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(getSSLSocketFactory());
                if (skipHostnameCheck || skipCertCheck)
                    https.setHostnameVerifier((hostname, session) -> true);
            }
            return conn;
        } catch (MalformedURLException e) {
            throw logger.systemException(e);
        }
    }

    protected Response submit(String context, String method, Map<String, String> requestProperties, PostData postData, Param... params) throws Exception {
        return _submit(staticAddress, context, method, requestProperties, postData, params);
    }

    protected Response _submit(String dest, String context, String method, Map<String, String> requestProperties, PostData postData, Param... params) throws Exception {
        if (postData == null)
            postData = new PostData();
        Response response = null;
        HttpURLConnection conn = null;
        try {
            conn = open(dest, context, params);
            if (postData.isSpecified()) {
                conn.setDoOutput(true);
                if (postData.getEncoder() != null)
                    conn.setRequestProperty("Content-Type", postData.getEncoder().getContentType());
                else conn.setRequestProperty("Content-Type", marshaller.getContentType());
            }

            try {
                conn.setRequestMethod(method);
            } catch (ProtocolException ex) {
                Field field = urlMethod;
                if (field == null) {
                    field = HttpURLConnection.class.getDeclaredField("method");
                    field.setAccessible(true);
                    urlMethod = field;
                }
                field.set(conn, method);
            }
            setConnectionProperties(conn);
            loadRequestProperties(conn, requestProperties);

            printRequest(conn, dest, context, method, requestProperties, postData, params);

            if (postData.isSpecified()) {
                // for POST, and PUT we MUST call conn.getOutputStream even if data is null
                OutputStream out = conn.getOutputStream();
                if (postData.getData() != null) {
                    try (Writer writer = new OutputStreamWriter(new BufferedOutputStream(out), StandardCharsets.UTF_8)) {
                        String posted = postData.encode(marshaller);
                        writer.write(posted);
                        logger.debug(posted);
                        writer.flush();
                    }
                }
                out.close();
            }

            response = readResponse(conn);
            printResponse(response);

        } catch (MalformedURLException e) {
            logger.systemException(e);
        } finally {
            if (conn != null)
                conn.disconnect();
        }

        return response;
    }

    private void printRequest(HttpURLConnection conn, String dest, String context, String method, Map<String, String> requestProperties, PostData postData, Param... params)
            throws Exception {
        if (printer != null) {
            printer.println("REQUEST ----------------------------");
            printer.println(method + " " + constructPath(dest, context, params));
            printHeaders((Map) requestProperties);
            printHeaders(conn.getRequestProperties());
            if (postData.getData() != null) {
                printer.println(marshaller.prettyPrintRequest(postData.getData()));
            }
            printer.println();
        }
    }

    private void printResponse(Response response)
            throws Exception {
        if (printer != null) {
            printer.println("RESPONSE ----------------------------");
            List<String> statusList = response.getHeaderFields().get(null);
            if (statusList != null && !statusList.isEmpty())
                printer.println(statusList.get(0));
            printer.println("Response Code=" + response.getResponseCode());
            printHeaders(response.getHeaderFields());
            String result = response.getResult();
            if (result != null && result.length() > 0) {
                if (isPrintRawResponse()) {
                    printer.println("===== RAW RESPONSE: START =====");
                    printer.println(result);
                    printer.println("===== RAW RESPONSE: END =======");
                }
                printer.println(marshaller.prettyPrintResponse(result));
            }
            printer.println();
        }
    }

    private Response readResponse(HttpURLConnection conn) throws Exception {
        Response response = new Response();

        response.setHeaderFields(conn.getHeaderFields());
        response.setResponseCode(conn.getResponseCode());

        if (conn.getResponseCode() == HTTP_NO_CONTENT)
            return response;

        InputStream in = null;
        try {
            in = conn.getInputStream();
        } catch (IOException ex) {
            Logger.suppress(ex);
            in = conn.getErrorStream();
            if (in == null)
                checkResponseCode(conn.getResponseCode(), conn.getResponseMessage());
        }

        try {
            BufferedInputStream bis = new BufferedInputStream(in);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            int read = 0;
            int bufSize = 4096;
            byte[] buffer = new byte[bufSize];
            while (true) {
                read = bis.read(buffer);
                if (read == -1) {
                    break;
                }
                byteArray.write(buffer, 0, read);
            }
            String result = new String(byteArray.toByteArray(), encoding);
            response.setResult(result);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }

        try {
            checkResponseCode(response.getResponseCode(), response.getResult());
        } catch (ClientErrorException ex) {
            Throwable mappedThrowable = null;
            String result = ex.getMessage();
            if (result != null && exceptionMapper != null) {
                try {
                    Object error = marshaller.readErrorResponse(result);
                    if (error != null) {
                        mappedThrowable = exceptionMapper.fromResponse(error);
                    }
                } catch (Exception e) {
                    Logger.suppress(e);
                }
            }
            if (printer != null)
                printer.println("FAILURE -----------------------------");
            printResponse(response);
            if (mappedThrowable != null) {
                if (mappedThrowable instanceof Exception)
                    throw (Exception) mappedThrowable;
                else
                    throw new SystemException(mappedThrowable);
            } else
                throw ex;
        }

        return response;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:SwitchLastCaseIsDefaultCheck"})
    protected void checkResponseCode(int code, String message) {
        javax.ws.rs.core.Response.Status status = javax.ws.rs.core.Response.Status.fromStatusCode(code);

        if (code == 500)
            throw new InternalServerErrorException();
        if (code > 500)
            throw new ServiceUnavailableException();

        if (status == null) {
            if (code == 422) {
                throw new BadRequestException(message);
            } else {
                throw new ServerErrorException(message, code);
            }
        }

        switch (status) {
            case OK:
            case CREATED:
            case ACCEPTED:
            case NO_CONTENT:
            case RESET_CONTENT:
            case PARTIAL_CONTENT:
                return;
        }

        if (printer != null) {
            String msg = (message != null && message.length() > 0) ? message : "NA";
            printer.println("ERROR RESPONSE ----------------------");
            printer.println("Response Code=" + code + " Message=" + msg);
            printer.println();
        }

        switch (status) {
            case BAD_REQUEST:
                throw new BadRequestException(message);
            case UNAUTHORIZED:
                throw new NotAuthorizedException(javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.UNAUTHORIZED).build());
            case PAYMENT_REQUIRED:
            case FORBIDDEN:
                throw new ForbiddenException(message);
            case NOT_FOUND:
                throw new NotFoundException(message);
            case METHOD_NOT_ALLOWED:
                throw new NotAllowedException(message,
                        javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED).build());
            case NOT_ACCEPTABLE:
                throw new NotAcceptableException(message);
            case UNSUPPORTED_MEDIA_TYPE:
                throw new NotSupportedException(message);
            default:
                throw new ServerErrorException(message, status);
        }
    }

    @SuppressWarnings({"squid:S135"})
    private void printHeaders(Map<String, ?> headers) {
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() == null)
                continue;
            printer.print(entry.getKey() + ": ");
            if ("Authorization".equals(entry.getKey()) && entry.getValue() instanceof String) {
                String auth = (String) entry.getValue();
                if (auth.startsWith("Bearer ")) {
                    printer.println("Bearer ...");
                    continue;
                }
            }
            boolean first = true;
            if (entry.getValue() instanceof List) {
                for (String item : (List<String>) entry.getValue()) {
                    if (first) {
                        first = false;
                    } else {
                        printer.print(", ");
                    }
                    printer.print(item);
                }
            } else {
                printer.print(entry.getValue());
            }
            printer.println();
        }
        printer.flush();
    }

    private void setConnectionProperties(HttpURLConnection conn) {
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Accept", marshaller.getAccept());
    }

    private void loadRequestProperties(HttpURLConnection conn, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet())
            conn.setRequestProperty(entry.getKey(), entry.getValue());
    }

    private SSLSocketFactory getSSLSocketFactory() {
        if (sslSocketFactory != null)
            return sslSocketFactory;
        SSLSocketConfig config = new SSLSocketConfig();
        config.setKeyStore(trustStore);
        config.setKeyStorePassword(trustStorePassword);
        config.setKeyStoreFormat(trustStoreFormat);
        config.setSkipCertCheck(skipCertCheck);
        config.setKeyManagerPassword(privateKeyPassword);
        config.setErasePasswords(true);
        try {
            sslSocketFactory = config.getSSLSocketFactory();
            privateKeyPassword = null;
        } catch (Exception e) {
            throw logger.systemException(e);
        }
        return sslSocketFactory;
    }


    // you can always explicitly set the SSLSocketFactory if you don't like how RestfulClient creates a SSLSocketFactory
    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

}
