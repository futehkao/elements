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


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.e6tech.elements.cassandra.driver.cql.AsyncResultSet;
import net.e6tech.elements.cassandra.driver.metadata.TableMetadata;
import net.e6tech.elements.cassandra.etl.ETLContext;
import net.e6tech.elements.cassandra.etl.Strategy;
import net.e6tech.elements.cassandra.generator.Generator;
import net.e6tech.elements.cassandra.generator.IndexGenerator;
import net.e6tech.elements.cassandra.generator.TableGenerator;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings({"unchecked", "squid:S00115", "squid:S3776"})
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
    private boolean dropColumn = false;
    private int threadSize = 1;
    private long validationWait = 1000L;
    private int tableCreationAsyncSize = 0;

    public int getThreadSize() {
        return threadSize;
    }

    public void setThreadSize(int threadSize) {
        this.threadSize = threadSize;
    }

    public Schema threadSize(int threadSize) {
        setThreadSize(threadSize);
        return this;
    }

    public long getValidationWait() {
        return validationWait;
    }

    public void setValidationWait(long validationWait) {
        this.validationWait = validationWait;
    }

    public int getTableCreationAsyncSize() {
        return tableCreationAsyncSize;
    }

    public void setTableCreationAsyncSize(int tableCreationAsyncSize) {
        this.tableCreationAsyncSize = tableCreationAsyncSize;
    }

    public Schema validationWait(long wait) {
        setValidationWait(wait);
        return this;
    }

    public List<Map<String, String>> getCodecs() {
        return codecs;
    }

    public void setCodecs(List<Map<String, String>> codecs) {
        this.codecs = codecs;
    }

    public boolean isDropColumn() {
        return dropColumn;
    }

    public void setDropColumn(boolean dropColumn) {
        this.dropColumn = dropColumn;
    }

    public SessionProvider getProvider(Resources resources) {
        return resources.getInstance(SessionProvider.class);
    }

    private  <A extends Annotation> Class[] scanClasses(Class<A> annotationClass, String ... packageNames) {
        if (packageNames == null || packageNames.length == 0)
            return new Class[0];
        PackageScanner packageScanner = new PackageScanner();
        List<Class> list = new ArrayList<>();
        for (String packageName : packageNames) {
            Class[] classes = packageScanner.getTopLevelClassesRecursive(provision.getPluginClassLoader(), packageName);
            for (Class cls : classes) {
                if (cls.getAnnotation(annotationClass) != null) {
                    list.add(cls);
                }
            }
        }
        return list.toArray(new Class[0]);
    }

    public void createTables(String keyspace, String ... packageNames) {
        Generator generator = provision.getInstance(SessionProvider.class).getGenerator();
        Class<? extends Annotation> annotation = generator.tableAnnotation();
        Class[] classes = scanClasses(annotation, packageNames);
        createTables(keyspace, classes);
    }

    public void createTables(String keyspace, Class ... classes) {

        if (classes == null)
            return;

        provision.open().accept(Resources.class, resources -> {
            List<TableGenerator> tableGenerators = new LinkedList<>();
            Session session = resources.getInstance(Session.class);
            SessionProvider provider = getProvider(resources);

            // create tables
            if (tableCreationAsyncSize <= 1) {
                for (Class cls : classes) {
                    TableGenerator tableGenerator = provider.getGenerator().getTable(keyspace, cls);
                    String cql = tableGenerator.generate();
                    if (logger.isInfoEnabled())
                        logger.info("Creating table synchronously, {}", tableGenerator.fullyQualifiedTableName());
                    session.execute(keyspace, cql);
                    tableGenerators.add(tableGenerator);
                }
            } else {
                tableGenerators = asyncTableGen(resources, keyspace, classes);
            }

            // diff tables
            for (TableGenerator gen : tableGenerators) {
                try {
                    gen.diff(session, keyspace, provider.getTableMetadata(keyspace, gen.getTableName()), isDropColumn());
                } catch (Throwable th) {
                    throw new SystemException("Cannot diff table " + gen.getTableName(), th);
                }
            }

            // create indexes
            for (Class cls : classes) {
                // create indexes
                IndexGenerator indexGenerator =  provider.getGenerator().createIndexes(keyspace, cls);
                List<String> statements = indexGenerator.generate();
                for (String cql : statements) {
                    try {
                        logger.info("Generating indexes asynchronously for class {}", indexGenerator.fullyQualifiedTableName());
                        session.execute(keyspace, cql);
                        Future<AsyncResultSet> future = session.executeAsync(keyspace, cql);
                    } catch (Exception ex) {
                        logger.info("Syntax error in creating index for {}", cls);
                        logger.info(cql);
                        throw ex;
                    }
                }
            }
        });

        validateTables(keyspace, classes);
    }

    protected List<TableGenerator> asyncTableGen(Resources resources, String keyspace, Class ... classes) {
        List<TableGenerator> tableGenerators = new LinkedList<>();
        int index = 0;
        List<Class> chunk = new ArrayList<>();
        for (int i = index; i < classes.length && chunk.size() < tableCreationAsyncSize; i++) {
            chunk.add(classes[i]);
            index = i + 1;
        }

        List<AsyncTableGenerator> tableCreation = new ArrayList<>();
        while (! chunk.isEmpty()) {
            for (Class cls : chunk) {
                if (getTableName(cls) == null)
                    continue;
                AsyncTableGenerator asyncTableGenerator = new AsyncTableGenerator(resources, keyspace, cls);
                tableCreation.add(asyncTableGenerator);
                tableGenerators.add(asyncTableGenerator.generator());
            }

            for (AsyncTableGenerator asyncTableGenerator : tableCreation) {
                try {
                    asyncTableGenerator.complete();
                } catch (Exception ex) {
                    throw new SystemException("Cannot create table for " + asyncTableGenerator.tableClass.getName(), ex);
                }
            }
            tableCreation.clear();
            chunk = new ArrayList<>();
            for (int i = index; i < classes.length && chunk.size() < tableCreationAsyncSize; i++) {
                chunk.add(classes[i]);
                index = i + 1;
            }
        }
        return tableGenerators;
    }

    protected String getTableName(Class entityClass) {
        Generator generator = provision.getInstance(SessionProvider.class).getGenerator();
        Class tmp = entityClass;
        while (tmp != null && tmp != Object.class) {
            if (generator.tableAnnotation(tmp) != null)
                return generator.tableName(tmp);
            tmp = tmp.getSuperclass();
        }
        return null;
    }

    public void validateTables(String keyspace, Class ... classes) {
        AtomicBoolean validated = new AtomicBoolean(classes.length == 0); // if 0, validated is set to true
        AtomicInteger index = new AtomicInteger(0);
        while (!validated.get()) {
            provision.open().accept(Resources.class, resources -> {
                SessionProvider provider = getProvider(resources);
                for (int i = index.get(); i < classes.length; i++) {
                    Class cls = classes[i];
                    TableGenerator generator = provider.getGenerator().getTable(keyspace, cls);
                    TableMetadata metadata = provider.getTableMetadata(keyspace, generator.getTableName());
                    if (metadata == null) {
                        index.set(i);
                        break;
                    } else if (i == classes.length - 1) {
                        validated.set(true);
                    }
                }
            });
            if (!validated.get()) {
                try {
                    Thread.sleep(validationWait);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
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
                    resources.getInstance(Session.class).execute(keyspace, stmt);
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
            logger.error("Cannot retrieve script {}{}.cql ", cls.getSimpleName(), postfix);
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
        extract(packageName, recursive, null);
    }

    public void extract(String packageName, boolean recursive, Consumer<ETLContext> customizer) {
        Map<Class<Strategy>, ETLContext> map = scan(packageName, recursive, customizer);
        ExecutorService threadPool = null;
        try {
            int totalEntries = map.size();
            if (totalEntries == 0)
                return;
            int threadNeeded = threadSize;
            if (threadNeeded > map.size())
                threadNeeded = map.size();
            threadPool = Executors.newFixedThreadPool(threadNeeded);
            List<Future<?>> futures = new LinkedList<>();
            for (Map.Entry<Class<Strategy>, ETLContext> entry : map.entrySet()) {
                Future<?> future = threadPool.submit(() -> {
                    try {
                        Strategy strategy = entry.getKey().getDeclaredConstructor().newInstance();
                        strategy.run(entry.getValue());
                    } catch (Exception e) {
                        logger.error("Cannot extract {}", entry.getKey());
                        throw new SystemException(e);
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    logger.warn("Interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException)
                        throw (RuntimeException) e.getCause();
                    else
                        throw new SystemException(e.getCause());
                }
            }
        } finally {
            if (threadPool != null)
                threadPool.shutdown();
        }
    }

    public Map<Class<Strategy>, ETLContext> scan(String packageName, boolean recursive, Consumer<ETLContext> customizer) {
        PackageScanner scanner = new PackageScanner();
        Class[] classes = recursive ? scanner.getTopLevelClassesRecursive(provision.getPluginClassLoader(), packageName)
                : scanner.getTopLevelClasses(provision.getPluginClassLoader(), packageName);

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

        Map<Class<Strategy>, ETLContext> result = new LinkedHashMap<>();
        for (Map.Entry<Class, Class> entry : map.entrySet()) {
            ETLContext context = null;
            try {
                context = (ETLContext) provision.newInstance(entry.getValue());
                if (customizer != null) {
                    customizer.accept(context);
                }
                result.put(entry.getKey(), context);
            } catch (Exception e) {
                logger.error("Cannot extract {}", entry.getKey());
                throw new SystemException(e);
            }
        }
        return result;
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

    class AsyncTableGenerator {
        String keyspace;
        SessionProvider provider;
        Session session;
        TableGenerator tableGenerator;
        TableMetadata metadata;
        Class tableClass;
        Future<AsyncResultSet> future;

        AsyncTableGenerator(Resources resources, String keyspace, Class cls) {
            this.keyspace = keyspace;
            session = resources.getInstance(Session.class);
            provider = getProvider(resources);
            tableGenerator = provider.getGenerator().getTable(keyspace, cls);
            tableClass = cls;
            generate();
        }

        TableGenerator generator() {
            return tableGenerator;
        }

        void generate() {
            metadata = provider.getTableMetadata(keyspace, tableGenerator.getTableName());
            if (metadata == null) {
                String cql = tableGenerator.generate();
                try {
                    if (logger.isInfoEnabled())
                        logger.info("Creating table asynchronously, {}", tableGenerator.fullyQualifiedTableName());
                    future = session.executeAsync(keyspace, cql);
                } catch (Exception ex) {
                    logger.info("Syntax error in creating table for {}", tableClass);
                    logger.info(cql);
                    throw ex;
                }
            }
        }

        void complete() throws ExecutionException, InterruptedException {
            try {
                tryComplete();
                future = null;
            } catch (Exception ex) {
                tryComplete();
            }
        }

        void tryComplete() throws ExecutionException, InterruptedException {
            if (future == null) {
                generate();
            }

            if (metadata == null && future == null)
                throw new ExecutionException("Cannot generate table " + tableClass.getName(), new SystemException("Cannot generate table " + tableClass.getName()));

            if (future != null) {
                future.get();
            }
        }
    }
}
