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

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import net.e6tech.elements.common.interceptor.CallFrame;
import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;
import net.e6tech.elements.common.interceptor.InterceptorListener;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.datastructure.Pair;
import org.ehcache.impl.internal.concurrent.ConcurrentHashMap;

import javax.ws.rs.*;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public class RestfulProxy {

    private RestfulClient client;
    private Interceptor interceptor;
    private Map<String, String> requestProperties = new LinkedHashMap<>();
    private PrintWriter printer;
    private Response lastResponse;

    public RestfulProxy(String hostAddress) {
        client = new RestfulClient(hostAddress);
        interceptor = Interceptor.getInstance();
    }

    public RestfulProxy(RestfulClient client) {
        this.client = client;
        interceptor = Interceptor.getInstance();
    }

    public ExceptionMapper getExceptionMapper() {
        return client.getExceptionMapper();
    }

    public void setExceptionMapper(ExceptionMapper exceptionMapper) {
        client.setExceptionMapper(exceptionMapper);
    }

    public PrintWriter getPrinter() {
        return printer;
    }

    public void setPrinter(PrintWriter printer) {
        this.printer = printer;
    }

    public String getHostAddress() {
        return client.getAddress();
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
        return interceptor.newInstance(serviceClass, new InvocationHandler(this, serviceClass, null));
    }

    public  <T> T newProxy(Class<T> serviceClass, Presentation presentation) {
        client.setPrinter(printer);
        return interceptor.newInstance(serviceClass, new InvocationHandler(this, serviceClass, presentation));
    }

    public  <T> T newProxy(Class<T> serviceClass, InterceptorListener listener) {
        client.setPrinter(printer);
        return interceptor.instanceBuilder(serviceClass, new InvocationHandler(this, serviceClass, null))
                .listener(listener).build();
    }

    public  <T> T newProxy(Class<T> serviceClass, Presentation presentation, InterceptorListener listener) {
        client.setPrinter(printer);
        return interceptor.instanceBuilder(serviceClass, new InvocationHandler(this, serviceClass, presentation))
                .listener(listener)
                .build();
    }

    public Map<String, String> getRequestProperties() {
        return Collections.unmodifiableMap(requestProperties);
    }

    public void setRequestProperties(Map<String, String> map) {
        requestProperties.putAll(map);
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

    public Response getLastResponse() {
        return lastResponse;
    }

    public RestfulClient getClient() {
        return client;
    }

    public static class InvocationHandler implements InterceptorHandler {
        private RestfulProxy proxy;
        private String context;
        private Map<Method, MethodForwarder> methodForwarders = new ConcurrentHashMap<>();
        private Map<Method, String> methodSignatures = new ConcurrentHashMap<>();
        private Presentation presentation;

        InvocationHandler(RestfulProxy proxy, Class<?> serviceClass, Presentation presentation) {
            this.proxy = proxy;
            this.presentation = presentation;
            Path path = serviceClass.getAnnotation(Path.class);
            if (path != null) {
                this.context = path.value();
                if (!context.endsWith("/"))
                    this.context = path.value() + "/";
            } else {
                context = "/";
            }
        }

        @Override
        public Object invoke(CallFrame frame) throws Throwable {
            if (proxy.printer != null) {
                String signature = methodSignatures.computeIfAbsent(frame.getMethod(), this::methodSignature);
                String caller = Reflection.<String, Boolean>mapCallingStackTrace(e -> {
                    if (e.state().isPresent()) return e.get().toString(); // previous element match.
                    if (e.get().getMethodName().equals(frame.getMethod().getName())) e.state(Boolean.TRUE); // match, but we are interested in the next one.
                    return null;
                }).orElse("Cannot detect caller");
                proxy.printer.println("Called by: " + caller);
                proxy.printer.println(signature);
            }
            Request request = proxy.client.create();
            if (presentation != null)
                request.setPresentation(presentation);
            for (Map.Entry<String, String> entry : proxy.requestProperties.entrySet()) {
                request.setRequestProperty(entry.getKey(), entry.getValue());
            }

            String fullContext = context;
            Path path = frame.getAnnotation(Path.class);
            if (path != null) {
                String subctx = path.value();
                while (subctx.startsWith("/"))
                    subctx = subctx.substring(1);
                fullContext = context + subctx;
            }

            final String ctx = fullContext;
            MethodForwarder forwarder;
            try {
                forwarder = methodForwarders.computeIfAbsent(frame.getMethod(),
                        key -> new MethodForwarder(ctx, key));
            } catch (IllegalArgumentException ex) {
                // this is clearly not a restful method
                if (frame.getTarget() != null)
                    return frame.invoke(frame.getTarget());
                return null;
            }
            Pair<Response, Object> pair = forwarder.forward(request, frame.getArguments());
            synchronized (proxy) {
                proxy.lastResponse = pair.key();
            }
            return pair.value();
        }

        public RestfulProxy getProxy() {
            return proxy;
        }

        public Presentation getPresentation() {
            return presentation;
        }

        public void setPresentation(Presentation presentation) {
            this.presentation = presentation;
        }

        String methodSignature(Method method) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(method.getReturnType().getSimpleName()).append(' ');
                sb.append(method.getDeclaringClass().getTypeName()).append('.');
                sb.append(method.getName());

                sb.append('(');
                separateWithCommas(method.getParameterTypes(), sb);
                sb.append(')');
                return sb.toString();
            } catch (Exception e) {
                return "<" + e + ">";
            }
        }

        void separateWithCommas(Class<?>[] types, StringBuilder sb) {
            for (int j = 0; j < types.length; j++) {
                sb.append(types[j].getSimpleName());
                if (j < (types.length - 1))
                    sb.append(",");
            }
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
        BeanParam[] beanParams;

        MethodForwarder(String context, Method method) {
            returnType = method.getReturnType();
            if (method.getGenericReturnType() instanceof ParameterizedType)
                parameterizedReturnType = (ParameterizedType) method.getGenericReturnType();
            paramTypes = method.getParameterTypes();
            this.context = context;
            queryParams = new QueryParam[paramTypes.length];
            pathParams = new PathParam[paramTypes.length];
            beanParams = new BeanParam[paramTypes.length];

            int idx = 0;
            params = method.getParameters();
            for (Parameter param : params) {
                QueryParam queryParam = param.getAnnotation(QueryParam.class);
                PathParam pathParam = param.getAnnotation(PathParam.class);
                BeanParam beanParam = param.getAnnotation(BeanParam.class);
                if (queryParam != null) {
                    queryParams[idx] = queryParam;
                }

                if(pathParam != null) {
                    pathParams[idx] = pathParam;
                }

                if(beanParam != null) {
                    beanParams[idx] = beanParam;
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

        private Optional<Object> getValue(Object o, AccessibleObject a) {
            try {
                if (a instanceof Method) {
                    return Optional.ofNullable(((Method) a).invoke(o));
                } else if (a instanceof Field) {
                    return Optional.ofNullable(Reflection.getProperty(o, ((Field) a).getName()));
                }
            } catch(IllegalAccessException | InvocationTargetException e) {
                // ignored
            }
            return Optional.empty();
        }

        @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S3776", "squid:S00112"})
        Pair<Response, Object> forward(Request request, Object[] args) throws Throwable {

            List<Param> paramList = new ArrayList<>();
            PostData postData = new PostData();

            String fullContext = context;
            for (int i = 0; i < paramTypes.length; i++) {
                if (queryParams[i] != null && args[i] != null) {
                    Param p = new Param(queryParams[i].value(), args[i].toString());
                    paramList.add(p);
                }

                if(pathParams[i] != null) {
                    if (args[i] == null)
                        throw new IllegalArgumentException("PathParam {" + pathParams[i].value() + "} cannot be null");
                    String value = args[i].toString();
                    String valueEscaped = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
                    fullContext = fullContext.replace("{" + pathParams[i].value() + "}", valueEscaped);
                }

                if(beanParams[i] != null && args[i] != null) {
                    Object beanParamObj = args[i];

                    Map<String, String> pathParamMap = new HashMap<>();
                    Reflection.forEachAnnotatedAccessor(beanParamObj.getClass(), PathParam.class, member ->
                            pathParamMap.put(member.getAnnotation(PathParam.class).value(),
                                    getValue(beanParamObj, member).map(Object::toString).orElse(null)));


                    for(Map.Entry<String, String> entry : pathParamMap.entrySet()) {
                        if (entry.getValue() == null)
                            throw new IllegalArgumentException("PathParam {" + entry.getKey() + "} cannot be null");
                        String valueEscaped = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
                        fullContext = fullContext.replace("{" + entry.getKey() + "}", valueEscaped);
                    }

                    Map<String, String> queryParamMap = new HashMap<>();
                    Reflection.forEachAnnotatedAccessor(beanParamObj.getClass(), QueryParam.class, member ->
                            queryParamMap.put(member.getAnnotation(QueryParam.class).value(),
                                    getValue(beanParamObj, member).map(Object::toString).orElse(null)));

                    for(Map.Entry<String, String> entry : queryParamMap.entrySet()) {
                        if (entry.getValue() == null) continue;
                        Param p = new Param(entry.getKey(), entry.getValue());
                        paramList.add(p);
                    }

                }

                if (pathParams[i] == null && queryParams[i] == null && beanParams[i] == null) {
                    postData.setData(args[i]);
                    postData.setSpecified(true);
                }
            }

            Response response = null;
            if (post) {
                response = request.post(fullContext, postData.getData(), paramList.toArray(new Param[paramList.size()]));
            } else if (put) {
                response = request.put(fullContext, postData.getData(), paramList.toArray(new Param[paramList.size()]));
            } else if (get) {
                response = request.get(fullContext, paramList.toArray(new Param[paramList.size()]));
            } else if (delete) {
                if (postData.isSpecified())
                    response = request.delete(fullContext, postData.getData(), paramList.toArray(new Param[paramList.size()]));
                else
                    response = request.delete(fullContext, paramList.toArray(new Param[paramList.size()]));
            } else {
                throw new IllegalArgumentException("Unknown HTTP method");
            }

            if (javax.ws.rs.core.Response.class.isAssignableFrom(returnType)) {
                WSResponseImpl impl = new WSResponseImpl(response);
                return new Pair<>(response, impl);
            } else if (returnType.equals(Void.TYPE)) {
                return new Pair<>(response, null);
            } else {
                if (parameterizedReturnType != null) {
                    Type type = parameterizedReturnType.getRawType();
                    if (type instanceof Class) {
                        Class encloseType = (Class) type;
                        if (Collection.class.isAssignableFrom(encloseType)) {
                            Class elementType = (Class) parameterizedReturnType.getActualTypeArguments()[0];
                            CollectionType ctype = TypeFactory.defaultInstance().constructCollectionType(encloseType, elementType);
                            return new Pair<>(response, Response.mapper.readValue(response.getResult(), ctype));
                        } else if (Map.class.isAssignableFrom(encloseType)) {
                            Class keyType = (Class) parameterizedReturnType.getActualTypeArguments()[0];
                            Class valueType = (Class) parameterizedReturnType.getActualTypeArguments()[1];
                            MapType mtype = TypeFactory.defaultInstance().constructMapType(encloseType, keyType, valueType);
                            return new Pair<>(response, Response.mapper.readValue(response.getResult(), mtype));
                        }
                    }
                }
                return new Pair<>(response, response.read(returnType));
            }
        }
    }

}
