/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.exceptions.SyntaxError;
import com.datastax.driver.mapping.annotations.Table;
import com.datastax.driver.mapping.annotations.UDT;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.etl.ETLContext;
import net.e6tech.elements.cassandra.etl.Strategy;
import net.e6tech.elements.cassandra.generator.Codec;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.PackageScanner;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.common.util.SystemException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class Schema {
    private static Cache<String, List<String>> scriptCache = CacheBuilder.newBuilder()
            .concurrencyLevel(32)
            .initialCapacity(128)
            .maximumSize(1000)
            .build();

    public enum ScriptType {
        create,
        extract
    }

    private static Logger logger = Logger.getLogger();

    private Provision provision;
    private List<Map<String, String>> codecs = new ArrayList<>();

    public List<Map<String, String>> getCodecs() {
        return codecs;
    }

    public void setCodecs(List<Map<String, String>> codecs) {
        this.codecs = codecs;
    }

    public SessionProvider getProvider(Resources resources) {
        return resources.getInstance(SessionProvider.class);
    }


    public void createCodecs(String keyspace, String userType, Class<? extends Codec> codec) {
        provision.open().accept(Resources.class, resources -> {
            String cql = getProvider(resources).getGenerator().createCodecs(keyspace, userType, codec);
            try {
                getProvider(resources).getSession(keyspace).execute(cql);
                logger.info("Created UDT type " + userType + " for class " + codec);
            } catch (SyntaxError ex) {
                logger.info("Syntax error in creating table for " + codec);
                logger.info(cql);
                throw ex;
            }
        });
    }

    public void registerCodecs(String keyspace, String packageName, Class ... order) {
        try {
            provision.suppressLogging();
            provision.open().accept(Resources.class, resources -> {
                SessionProvider provider = getProvider(resources);
                Class[] found = (packageName == null) ? new Class[0] : scanClasses(UDT.class, new String[] {packageName});
                Class[] classes = found;
                if (order != null) {
                    List<Class> list = new ArrayList<>();
                    for (Class cls : found) {
                        for (Class cls2 : order) {
                            if (!cls.equals(cls2)) {
                                list.add(cls);
                            }
                        }
                    }
                    for (Class cls : order)
                        list.add(cls);

                    classes = list.toArray(new Class[0]);
                }

                for (Class codecClass : classes) {
                    try {
                        String keySpaceIn = null;
                        String userType = null;
                        UDT udt = (UDT) codecClass.getAnnotation(UDT.class);
                        if (!udt.keyspace().isEmpty())
                            keySpaceIn = udt.keyspace();
                        userType = udt.name();

                        if (keyspace != null) {
                            keySpaceIn = keyspace;
                        }

                        if (Codec.class.isAssignableFrom(codecClass))
                            createCodecs(keySpaceIn, userType, (Class) codecClass);
                        provider.registerCodec(keySpaceIn, userType, codecClass);
                    } catch (SyntaxError e) {
                        logger.warn("Cannot register codec for class " + codecClass.getName());
                    }
                }
            });
        } finally {
            provision.resumeLogging();
        }
    }

    public void registerCodecs() {
        provision.open().accept(Resources.class, resources -> {
            SessionProvider provider = getProvider(resources);
            for (Map<String, String> map : codecs) {
                String codecClassName = map.get("codec");

                if (codecClassName == null)
                    throw new IllegalArgumentException("No codec defined");

                try {
                    Class<? extends TypeCodec> codecClass = (Class) getClass().getClassLoader().loadClass(codecClassName);
                    String keySpaceIn = null;
                    String userType = null;

                    UDT udt = codecClass.getAnnotation(UDT.class);
                    if (udt != null) {
                        if (!udt.keyspace().isEmpty())
                            keySpaceIn = udt.keyspace();
                        userType = udt.name();
                    }

                    if (map.get("keyspace") != null)
                        keySpaceIn = map.get("keyspace");

                    if (map.get("userType") != null)
                        userType = map.get("userType");

                    if (userType == null)
                        throw new IllegalArgumentException("No userType defined");
                    if (Codec.class.isAssignableFrom(codecClass))
                        createCodecs(keySpaceIn, userType, (Class) codecClass);
                    provider.registerCodec(keySpaceIn, userType, codecClass);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            }
        });
    }

    public void createKeyspace(String keyspaceIn, int replication) {
        provision.open().accept(Resources.class, resources -> {
            Metadata meta = resources.getInstance(Cluster.class).getMetadata();
            if (meta.getKeyspace(keyspaceIn) == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE KEYSPACE IF NOT EXISTS ").append(keyspaceIn);
                sb.append(" WITH replication = {'class':'SimpleStrategy', 'replication_factor' : " + replication + "};");
                resources.getInstance(Session.class).execute(sb.toString());
            }
        });
    }

    private  <A extends Annotation> Class[] scanClasses(Class<A> annotationClass, String ... packageNames) {
        if (packageNames == null || packageNames.length == 0)
            return new Class[0];
        PackageScanner packageScanner = new PackageScanner();
        List<Class> list = new ArrayList<>();
        for (String packageName : packageNames) {
            Class[] classes = packageScanner.getTopLevelClassesRecursive(getClass().getClassLoader(), packageName);
            for (Class cls : classes) {
                if (cls.getAnnotation(annotationClass) != null) {
                    list.add(cls);
                }
            }
        }
        return list.toArray(new Class[0]);
    }

    public void createTables(String keyspace, String ... packageNames) {
        Class[] classes = scanClasses(Table.class, packageNames);
        createTables(keyspace, classes);
    }

    public void createTables(String keyspace, Class ... classes) {
        provision.open().accept(Resources.class, resources -> {
            for (Class cls : classes) {
                String cql = getProvider(resources).getGenerator().createTable(keyspace, cls);
                try {
                    getProvider(resources).getSession(keyspace).execute(cql);
                } catch (Exception ex) {
                    logger.info("Syntax error in creating table for " + cls);
                    logger.info(cql);
                    throw ex;
                }
            }

            for (Class cls : classes) {
                List<String> statements = getProvider(resources).getGenerator().createIndexes(keyspace, cls);
                for (String cql : statements) {
                    try {
                        getProvider(resources).getSession(keyspace).execute(cql);
                    } catch (Exception ex) {
                        logger.info("Syntax error in creating index for " + cls);
                        logger.info(cql);
                        throw ex;
                    }
                }
            }
        });
    }

    public void runScripts(ScriptType type, Class... classes) {
        provision.open().accept(Resources.class, resources -> {
            for (Class cls : classes) {
                List<String> statements = getScript(cls, type);
                for (String stmt : statements) {
                    resources.getInstance(Session.class).execute(stmt);
                }
            }
        });
    }

    public void runScripts(String keyspace, ScriptType type, Class... classes) {
        provision.open().accept(Resources.class, resources -> {
            for (Class cls : classes) {
                List<String> statements = getScript(cls, type);
                for (String stmt : statements) {
                    getProvider(resources).getSession(keyspace).execute(stmt);
                }
            }
        });
    }

    public static List<String> getScript(Class cls, ScriptType type) {
        String tmp = "";
        if (type != null) {
            tmp = "_" + type.name();
        }
        final String postfix = tmp;

        try {
            return scriptCache.get(cls.getName() + postfix, () -> {
                try (BufferedInputStream stream = new BufferedInputStream(cls.getResourceAsStream(cls.getSimpleName() + postfix + ".cql"))) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int size;
                    while ((size = stream.read(buffer)) != -1) {
                        output.write(buffer, 0, size);
                    }
                    String file = new String(output.toByteArray(), StandardCharsets.UTF_8);
                    String[] statments = file.split(";");
                    List<String> list = new ArrayList<>();
                    for (String stmt : statments) {
                        if (!StringUtil.isNullOrEmpty(stmt)) {
                            list.add(stmt);
                        }
                    }
                    return Collections.unmodifiableList(list);
                }
            });
        } catch (ExecutionException e) {
            logger.error("Cannot retrieve script " + cls.getSimpleName() + postfix + ".cql");
            throw new SystemException(e);
        }
    }

    public void extract(String packageName) {
        extract(packageName, false);
    }

    public void extractRecursive(String packageName) {
        extract(packageName, true);
    }

    public void extract(String packageName, boolean recursive) {
        PackageScanner scanner = new PackageScanner();
        List<Class> classList = new ArrayList<>();
        Class[] classes = recursive ? scanner.getTopLevelClassesRecursive(Strategy.class.getClassLoader(), packageName)
                : scanner.getTopLevelClasses(Strategy.class.getClassLoader(), packageName);
        for (Class cls : classes) {
            if (Strategy.class.isAssignableFrom(cls)) {
                classList.add(cls);
            }
        }

        Map<Class, Class> map = new LinkedHashMap<>();
        List<Class> list = new ArrayList<>();
        for (Class cls : classes) {
            if (Modifier.isAbstract(cls.getModifiers()) || cls.isInterface()) {
                continue;
            }
            Class clazz = cls;
            list.clear();
            while (clazz != null && clazz != Object.class) {
                analyze(clazz, list);
                clazz = clazz.getSuperclass();
            }
            if (!list.isEmpty()) {
                map.put(cls, list.get(0));
            }
        }

        for (Map.Entry<Class, Class> entry : map.entrySet()) {
            ETLContext context = null;
            try {
                context = (ETLContext) provision.newInstance(entry.getValue());
                Strategy strategy = (Strategy) entry.getKey().getDeclaredConstructor().newInstance();
                strategy.run(context);
            } catch (Exception e) {
                logger.error("Cannot extract {}", entry.getKey());
                throw new SystemException(e);
            }
        }
    }

    private void analyze(Class cls, List<Class> list) {
        if (Strategy.class.isAssignableFrom(cls)) {
            for (TypeVariable typeVar : cls.getTypeParameters()) {
                for (Type t : typeVar.getBounds()) {
                    if (t instanceof Class && ETLContext.class.isAssignableFrom((Class) t)) {
                        list.add((Class) t);
                        break;
                    }
                }
            }

            Type sup = cls.getGenericSuperclass();
            if (sup instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) sup;
                for (Type arg : ptype.getActualTypeArguments()) {
                    if (arg instanceof Class && ETLContext.class.isAssignableFrom((Class) arg)) {
                        list.add((Class) arg);
                    }
                }
            }

            for (Type type : cls.getGenericInterfaces()) {
                if (type instanceof ParameterizedType) {
                    ParameterizedType ptype = (ParameterizedType) type;
                    if (Strategy.class.isAssignableFrom((Class) ptype.getRawType())) {
                        for (Type arg : ptype.getActualTypeArguments()) {
                            if (arg instanceof Class && ETLContext.class.isAssignableFrom((Class) arg)) {
                                list.add((Class) arg);
                            }
                        }
                        analyze((Class) ptype.getRawType(), list);
                    }
                }
            }
        }
    }

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }
}
