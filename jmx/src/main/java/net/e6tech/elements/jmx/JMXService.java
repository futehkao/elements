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

package net.e6tech.elements.jmx;

import com.j256.simplejmx.common.JmxResource;
import com.j256.simplejmx.server.JmxServer;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.ObjectConverter;
import net.e6tech.elements.common.util.SystemException;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S1191")
public class JMXService {
    private static final Logger logger = Logger.getLogger();

    private JMXService() {
    }

    public static void start(int port, int jmxrmiPort,  String user, char[] password) throws Exception {
        start(InetAddress.getLoopbackAddress(), port, jmxrmiPort, user, password);
    }

    @SuppressWarnings({"unchecked", "squid:S00112", "squid:S1191"})
    public static void start(InetAddress bindAddress, int port, int jmxrmiPort, String user, char[] password) throws Exception {
        if (port >= 0) {
            JMXHtmlServer adapter = new JMXHtmlServer(port);
            adapter.setBindAddress(bindAddress);
            if (user != null && user.length() > 0) {
                com.sun.jdmk.comm.AuthInfo authInfo = new com.sun.jdmk.comm.AuthInfo(user, new String(password));
                adapter.addUserAuthenticationInfo(authInfo);
            }
            adapter.start();
            try {
                ManagementFactory.getPlatformMBeanServer().registerMBean(adapter, new ObjectName("JMX:name=htmlAdaptorServer"));
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }

        if (jmxrmiPort >= 0) {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            Registry registry = LocateRegistry.createRegistry(jmxrmiPort);
            registry.list();
            Map env = new HashMap<>();
            if (user != null && user.length() > 0) {
                String[] creds = {user, new String(password)};
                env.put(JMXConnector.CREDENTIALS, creds);
            }
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jmxrmiPort + "/server");
            JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
            cs.start();

            mbs.registerMBean(cs, new ObjectName("connector:type=standard_rmi"));
        }
    }

    public static void registerMBean(Object mbean, String name) {
        try {
            register(mbean, new ObjectName(name));
        } catch (Exception ex) {
            logger.info("Cannot register {} as MBean", name, ex);
        }
    }

    public static int registerMBean(Object mbean, Function<Integer, String> function) {
        int count = 1;
        while (find(function.apply(count)).isPresent()) {
            count ++ ;
        }
        registerMBean(mbean, function.apply(count));
        return count;
    }

    public static ObjectInstance registerIfAbsent(String name, Supplier supplier) throws JMException {
        ObjectName objectName = null;
        try {
            objectName = new ObjectName(name);
        } catch(MalformedObjectNameException ex) {
            throw new IllegalArgumentException(ex);
        }

        Optional<ObjectInstance> optional = find(objectName);
        if (!optional.isPresent()) {
            try {
                register(supplier.get(), objectName);
                return find(objectName).orElseThrow(() -> new InstanceNotFoundException("ObjectInstance with name=" + name + " not found."));
            } catch (InstanceAlreadyExistsException ex) {
                return find(objectName).orElseThrow(() -> ex);
            }
        } else {
            return optional.get();
        }
    }

    @SuppressWarnings({"squid:S135", "squid:S2095"})
    private static void register(Object mbean, ObjectName objectName) throws JMException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxServer jmxServer = new JmxServer(server);
        if (mbean.getClass().getAnnotation(JmxResource.class) != null) {
            jmxServer.register(mbean, objectName, null, null, null);
        } else {
            boolean conformToMBean = false;
            Class<?>[] interfaces = mbean.getClass().getInterfaces();
            for (Class<?> intf : interfaces) {
                MXBean annotation = intf.getAnnotation(MXBean.class);
                if (annotation != null) {
                    conformToMBean = annotation.value();
                    break;
                }

                if (intf.getSimpleName().endsWith("MXBean") || intf.getSimpleName().endsWith("MBean")) {
                    conformToMBean = true;
                    break;
                }
            }
            if (conformToMBean)
                server.registerMBean(mbean, objectName);
            else jmxServer.register(mbean, objectName, null, null, null);
        }
    }

    @SuppressWarnings("squid:S2095")
    public static void unregisterMBean(String name) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            JmxServer jmxServer = new JmxServer(server);
            jmxServer.unregister(new ObjectName(name));
        } catch (Exception e) {
            logger.warn("Cannot register " + name + " as MBean", e);
        }
    }

    public static Optional<ObjectInstance> find(String objectName) {
        try {
            return find(new ObjectName(objectName));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Optional<ObjectInstance> find(ObjectName objectName) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectInstance instance;
        try {
            instance = mBeanServer.getObjectInstance(objectName);
        } catch (InstanceNotFoundException e) {
            Logger.suppress(e);
            return Optional.empty();
        }
        return Optional.of(instance);
    }

    public static Set<ObjectInstance> query(ObjectName objectName) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        return  mBeanServer.queryMBeans(objectName, null);
    }

    public static Object invoke(ObjectName objectName, String method, Object[] arguments, String[] signature) throws MBeanException {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            return mBeanServer.invoke(objectName, method, arguments, signature);
        } catch (InstanceNotFoundException | ReflectionException e) {
            throw new MBeanException(e);
        }
    }

    /* invokes an operation on the mbean */
    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S00112", "squid:S135", "squid:S134", "squid:S3776"})
    public static Object invoke(ObjectName objectName,
                       String methodName,
                       Object ... arguments) throws Exception {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectInstance> instances = mBeanServer.queryMBeans(objectName, null);

        if (instances.isEmpty())
            return null;

        if (instances.size() > 1)
            throw new IllegalStateException("More than one instances found with the objectName");

        ObjectInstance instance = instances.iterator().next();
        MBeanInfo info = mBeanServer.getMBeanInfo(instance.getObjectName());

        Object ret = null;
        boolean found = false;
        int arglen = 0;
        if (arguments != null)
            arglen = arguments.length;
        for (MBeanOperationInfo op : info.getOperations()) {
            if (op.getName().equals(methodName)) {
                String[] signature = new String[0];
                Object[] args = new Object[0];
                MBeanParameterInfo[] params = op.getSignature();
                if (arglen != params.length)
                    continue;
                if (arguments != null) {
                    signature = new String[params.length];
                    args = new Object[params.length];
                    for (int i = 0 ; i < params.length; i++) {
                        MBeanParameterInfo entry = params[i];
                        signature[i] = entry.getType();
                        Class toType = ObjectConverter.loadClass(mBeanServer.getClassLoaderFor(instance.getObjectName()),
                                entry.getType());
                        args[i] = (new ObjectConverter()).convert(arguments[i], (Type) toType, null);
                    }
                }
                ret = mBeanServer.invoke(instance.getObjectName(), methodName, args, signature);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("No method named \"" + methodName + "\" with " + arglen + " parameters.");
        }
        return ret;
    }
}
