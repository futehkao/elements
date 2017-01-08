/*
Copyright 2015 Futeh Kao

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

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorAssist;
import net.e6tech.elements.common.interceptor.InterceptorHandler;

import javax.ws.rs.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by futeh.
 */
public class RestfulProxy {

    private String hostAddress;
    private RestfulClient client;
    private Interceptor interceptor;
    private Map<String, String> requestProperties = new LinkedHashMap<>();
    private PrintWriter printer;

    public RestfulProxy(String hostAddress) {
        this.hostAddress = hostAddress;
        client = new RestfulClient(hostAddress);
        interceptor = Interceptor.getInstance();
    }

    public PrintWriter getPrinter() {
        return printer;
    }

    public void setPrinter(PrintWriter printer) {
        this.printer = printer;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public boolean isSkipHostnameCheck() {
        return client.isSkipHostnameCheck();
    }

    public void setSkipHostnameCheck(boolean skipHostnameCheck) {
       client.setSkipHostnameCheck(skipHostnameCheck);
    }

    public boolean isSkipCertCheck() {
        return client.isSkipCertCheck();
    }

    public void setSkipCertCheck(boolean skipCertCheck) {
        client.setSkipCertCheck(skipCertCheck);
    }

    public  <T> T newProxy(Class<T> serviceClass) {
        client.setPrinter(printer);
        return interceptor.newInstance(serviceClass, new InvocationHandler(this, serviceClass, printer));
    }

    public void setRequestProperty(String key, String value) {
        requestProperties.put(key, value);
    }

    public void clearRequestProperty(String key) {
        requestProperties.remove(key);
    }

    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    private static class InvocationHandler implements InterceptorHandler {
        private RestfulProxy proxy;
        private String context;
        private Map<Method, MethodForwarder> methodForwarders = new Hashtable<>();
        private PrintWriter printer;

        InvocationHandler(RestfulProxy proxy, Class serviceClass, PrintWriter printer) {
            this.proxy = proxy;
            this.printer = printer;
            Path path = (Path) serviceClass.getAnnotation(Path.class);
            if (path != null) {
                this.context = path.value();
                if (!context.endsWith("/")) this.context = path.value() + "/";
            } else {
                context = "/";
            }
        }

        @Override
        public Object invoke(Object target, Method thisMethod, Object[] args) throws Throwable {
            if (printer != null) {
                printer.println(thisMethod);
            }
            Request request = proxy.client.create();
            for (Map.Entry<String, String> entry : proxy.requestProperties.entrySet()) {
                request.setRequestProperty(entry.getKey(), entry.getValue());
            }

            String fullContext = context;
            Path path = thisMethod.getAnnotation(Path.class);
            if (path != null) {
                String subctx = path.value();
                while (subctx.startsWith("/")) subctx = subctx.substring(1);
                fullContext = context + subctx;
            }

            final String ctx = fullContext;
            MethodForwarder forwarder = methodForwarders.computeIfAbsent(thisMethod, (key) ->  new MethodForwarder(ctx, key) );
            return forwarder.forward(request, args);
        }
    }

    private static class MethodForwarder {
        boolean get;
        boolean post;
        boolean put;
        boolean delete;
        Class returnType;
        ParameterizedType parameterizedReturnType;
        Class[] paramTypes;
        Parameter[] params;
        String context;
        QueryParam[] queryParams;
        PathParam[] pathParams;

        MethodForwarder(String context, Method method) {
            returnType = method.getReturnType();
            if (method.getGenericReturnType() instanceof ParameterizedType) parameterizedReturnType = (ParameterizedType) method.getGenericReturnType();
            paramTypes = method.getParameterTypes();
            this.context = context;
            queryParams = new QueryParam[paramTypes.length];
            pathParams = new PathParam[paramTypes.length];

            int idx = 0;
            params = method.getParameters();
            for (Parameter param : params) {
                QueryParam queryParam = (QueryParam) param.getAnnotation(QueryParam.class);
                PathParam pathParam = (PathParam) param.getAnnotation(PathParam.class);
                if (queryParam != null) {
                    queryParams[idx] = queryParam;
                }

                if(pathParam != null) {
                    pathParams[idx] = pathParam;
                }
                idx++;
            }

            if (method.getAnnotation(POST.class) != null) {
                post = true;
            } else if (method.getAnnotation(PUT.class) != null) {
                put = true;
            } else if (method.getAnnotation(GET.class) != null) {
                get = true;
            } else if (method.getAnnotation(DELETE.class) != null) {
                delete = true;
            } else {
                throw new IllegalArgumentException("Method " + method + " is not annotated with GET, PUT, POST or DELETE.");
            }
        }

        Object forward(Request request, Object[] args) throws Throwable {

            List<Param> paramList = new ArrayList<>();
            Object postData = null;

            String fullContext = context;
            for (int i = 0; i < paramTypes.length; i++) {
                if (queryParams[i] != null && args[i] != null) {
                    Param p = new Param(queryParams[i].value(), args[i].toString());
                    paramList.add(p);
                }

                if(pathParams[i] != null) {
                    if (args[i] == null) throw new IllegalArgumentException("PathParam {" + pathParams[i].value() + "} cannot be null");
                    String value = args[i].toString();
                    String valueEscaped = URLEncoder.encode(value,"UTF-8").replaceAll("\\+", "%20");
                    fullContext = fullContext.replace("{" + pathParams[i].value() + "}", valueEscaped);
                }

                if (pathParams[i] == null && queryParams[i] == null) postData = args[i];
            }

            Response response = null;
            if (post) {
                response = request.post(fullContext, postData, paramList.toArray(new Param[paramList.size()]));
            } else if (put) {
                response = request.put(fullContext, postData, paramList.toArray(new Param[paramList.size()]));
            } else if (get) {
                response = request.get(fullContext, paramList.toArray(new Param[paramList.size()]));
            } else if (delete) {
                response = request.delete(fullContext, paramList.toArray(new Param[paramList.size()]));
            }

            if (javax.ws.rs.core.Response.class.isAssignableFrom(returnType)) {
                WSResponseImpl impl = new WSResponseImpl(response);
                return impl;
            } else if (returnType.equals(Void.TYPE)) {
                return null;
            } else {
                if (parameterizedReturnType != null) {
                    Type type = parameterizedReturnType.getRawType();
                    if (type instanceof Class) {
                        Class encloseType = (Class) type;
                        if (Collection.class.isAssignableFrom(encloseType)) {
                            Class elementType = (Class) parameterizedReturnType.getActualTypeArguments()[0];
                            CollectionType ctype = TypeFactory.defaultInstance().constructCollectionType(encloseType, elementType);
                            return response.mapper.readValue(response.getResult(), ctype);
                        }
                    }
                }
                return response.read(returnType);
            }
        }

        private Collection convertCollection(Collection value, Class<? extends Collection> collectionType, Class elementType) throws IOException {

            //CollectionType ctype = TypeFactory.defaultInstance().constructCollectionType(collectionType, elementType);
            //String str = Response.mapper.writeValueAsString(value);
            //Collection converted = mapper.readValue(str, ctype);


            return null;
        }

    }

}
