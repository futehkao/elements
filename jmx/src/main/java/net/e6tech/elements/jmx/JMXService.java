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
package net.e6tech.elements.jmx;

import com.j256.simplejmx.common.JmxResource;
import com.j256.simplejmx.server.JmxServer;
import com.sun.jdmk.comm.AuthInfo;
import com.sun.jdmk.comm.HtmlAdaptorServer;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.ObjectConverter;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Created by futeh.
 */
public class JMXService {
    private static final Logger logger = Logger.getLogger();

    public static void start(int port, int jmxrmiPort,  String user, char[] password) throws Exception {
        HtmlAdaptorServer adapter = new HtmlAdaptorServer(port);
        if (user != null && user.length() > 0) {
            AuthInfo authInfo = new AuthInfo(user, new String(password));
            adapter.addUserAuthenticationInfo(authInfo);
        }
        adapter.start();
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(adapter, new ObjectName("JMX:name=htmlAdaptorServer"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Registry registry = LocateRegistry.createRegistry(jmxrmiPort);
        Map env = new HashMap<>();
        if (user != null && user.length() > 0) {
            String[] creds = {user, new String(password)};
            env.put(JMXConnector.CREDENTIALS, creds);
        }
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jmxrmiPort + "/server");
        JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
        cs.start();

        mbs.registerMBean(cs, new ObjectName("connector:type=standard_rmi") );
    }

    public static void registerMBean(Object mbean, String name) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            JmxServer jmxServer = new JmxServer(server);
            if (mbean.getClass().getAnnotation(JmxResource.class) != null) {
                jmxServer.register(mbean, new ObjectName(name), null, null, null);
            } else {
                boolean conformToMBean = false;
                Class[] interfaces = mbean.getClass().getInterfaces();
                for (Class intf : interfaces) {
                    MXBean annotation = (MXBean) intf.getAnnotation(MXBean.class);
                    if (annotation != null) {
                        conformToMBean = annotation.value();
                        break;
                    }

                    if (intf.getSimpleName().endsWith("MXBean") || intf.getSimpleName().endsWith("MBean")) {
                        conformToMBean = true;
                        break;
                    }
                }
                if (conformToMBean) server.registerMBean(mbean, new ObjectName(name));
                else jmxServer.register(mbean, new ObjectName(name), null, null, null);
            }
        } catch (javax.management.NotCompliantMBeanException | IllegalArgumentException ex) {
            logger.info("Cannot register " + name + " as MBean", ex);
        } catch (Exception e) {
            logger.warn("Cannot register " + name + " as MBean", e);
        }
    }

    public static void unregisterMBean(String name) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            JmxServer jmxServer = new JmxServer(server);
            jmxServer.unregister(new ObjectName(name));
        } catch (Exception e) {
            logger.warn("Cannot register " + name + " as MBean", e);
        }
    }

    public static Optional<ObjectInstance> find(ObjectName objectName) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectInstance> instances = mBeanServer.queryMBeans(objectName, null);
        if (instances.size() == 0) return Optional.empty();
        if (instances.size() > 1) throw new IllegalArgumentException("More one instances found with objectName=" + objectName);
        return Optional.of(instances.iterator().next());
    }

    public static Set<ObjectInstance> query(ObjectName objectName) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        return  mBeanServer.queryMBeans(objectName, null);
    }

    public static Object invoke(ObjectName objectName, String method, Object[] arguments, String[] signature) throws MBeanException, InstanceNotFoundException, ReflectionException {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        return mBeanServer.invoke(objectName, method, arguments, signature);
    }

    /* invokes an operation on the mbean */
    public static Object invoke(ObjectName objectName,
                       String methodName,
                       Object ... arguments) throws Exception {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectInstance> instances = mBeanServer.queryMBeans(objectName, null);

        if (instances.size() == 0) {
            return null;
        }
        if (instances.size() > 1) {
            throw new IllegalStateException("More than one instances found with the objectName");
        }

        ObjectInstance instance = instances.iterator().next();
        MBeanInfo info = mBeanServer.getMBeanInfo(instance.getObjectName());

        Object ret = null;
        boolean found = false;
        int arglen = 0;
        if (arguments != null) arglen = arguments.length;
        for (MBeanOperationInfo op : info.getOperations()) {
            if (op.getName().equals(methodName)) {
                String[] signature = new String[0];
                Object[] args = new Object[0];
                MBeanParameterInfo[] params = op.getSignature();
                if (arglen != params.length) continue;
                if (params != null) {
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
