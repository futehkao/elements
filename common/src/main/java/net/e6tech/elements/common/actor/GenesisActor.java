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

package net.e6tech.elements.common.actor;

import com.google.common.io.ByteStreams;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.e6tech.elements.common.actor.typed.Guardian;
import net.e6tech.elements.common.actor.typed.worker.WorkerPoolConfig;
import net.e6tech.elements.common.federation.Registry;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.*;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
public class GenesisActor implements Initializable, net.e6tech.elements.common.federation.Genesis {
    private static Logger logger = Logger.getLogger();
    public static final String WORKER_POOL_DISPATCHER = "worker-pool-dispatcher";
    private String name;
    private String configuration;
    private Guardian guardian;
    private WorkerPoolConfig workPoolConfig = new WorkerPoolConfig();
    private long timeout = 5000L;
    private String profile = "remote";
    private Config config;

    public GenesisActor() {
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public WorkerPoolConfig getWorkPoolConfig() {
        return workPoolConfig;
    }

    public void setWorkPoolConfig(WorkerPoolConfig workPoolConfig) {
        this.workPoolConfig = workPoolConfig;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Config getConfig() {
        return config;
    }

    public GenesisActor(Provision provision, WorkerPoolConfig workerPoolConfig) {
        if (provision == null || provision.getBean(Guardian.class) == null) {
            WorkerPoolConfig wpc = workerPoolConfig;
            if (workerPoolConfig == null)
                wpc = new WorkerPoolConfig();
            setName(getClass().getSimpleName() + wpc.hashCode());
            setProfile("local");
            setWorkPoolConfig(wpc);
            initialize((Resources) null);
            if (provision != null)
                provision.getResourceManager().addResourceProvider(ResourceProvider.wrap(getClass().getSimpleName(), (OnShutdown) this::shutdown));
            getGuardian().setEmbedded(true);
        } else {
            guardian = provision.getBean(Guardian.class);
            setName(guardian.getName());
            if (workerPoolConfig != null)
                setWorkPoolConfig(workerPoolConfig);
        }
    }

    @Override
    public void initialize(Resources resources) {
        if (name == null)
            throw new IllegalStateException("name is null");

        Config conf = null;

        // Create an Akka system
        if (configuration != null) {
            conf = ConfigFactory.parseString(configuration);
        } else {
            conf = ConfigFactory.defaultApplication();
        }

        initialize(conf);

        if (resources != null) {
            final ResourceManager resourceManager = resources.getResourceManager();
            resourceManager.addResourceProvider(ResourceProvider.wrap("Genesis", (OnShutdown) this::shutdown));
            if (guardian != null)
                resourceManager.registerBean(guardian.getName(), guardian);
        }
    }

    public void initialize(Config cfg) {
        if (name == null)
            throw new IllegalStateException("name is null");

        config = (cfg != null) ? cfg : ConfigFactory.defaultApplication();

        if (profile != null) {
            if (!profile.endsWith(".conf"))
                profile += ".conf";
            String path = GenesisActor.class.getPackage().getName().replace('.', '/');
            try (InputStream in = GenesisActor.class.getClassLoader().getResourceAsStream(path + "/" + profile)) {
                byte[] bytes = ByteStreams.toByteArray(in);
                String classPathConfig = new String(bytes, StandardCharsets.UTF_8);
                config = config.withFallback(ConfigFactory.parseString(classPathConfig));
            } catch (IOException e) {
                throw new SystemException(e);
            }
        }

        if (!config.hasPath(WORKER_POOL_DISPATCHER)) {
            config = config.withFallback(ConfigFactory.parseString(
                    WORKER_POOL_DISPATCHER + " {\n" +
                            "  type = Dispatcher\n" +
                            "  thread-pool-executor {\n" +
                            "      keep-alive-time = 60s\n" +
                            "      core-pool-size-min = 8\n" +
                            "      core-pool-size-factor = 5.0\n" +
                            "      # unbounded so that max-pool-size-factor has no effect.\n" +
                            "      task-queue-size = -1\n" +
                            "      allow-core-timeout = on\n" +
                            "    }\n" +
                            "  throughput = 1\n" +
                            "}"
            ));
        }

        // Create an Akka system
        guardian = Guardian.create(getName(), getTimeout(), config, workPoolConfig);
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public void shutdown() {
        guardian.getSystem().terminate();
        try {
            guardian.getSystem().getWhenTerminated().toCompletableFuture().get();
        } catch (Exception e) {
            logger.warn("Error during shutdown", e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public void terminate() {
        if (guardian != null)
            guardian.getSystem().terminate();
    }

    public Registry getRegistry() {
        return null;
    }

    public CompletionStage<Void> async(Runnable runnable) {
        return async(runnable, getTimeout());
    }

    public CompletionStage<Void> async(Runnable runnable, long timeout) {
        return guardian.talk(timeout).async(runnable);
    }

    public <R> CompletionStage<R> async(Supplier<R> supplier) {
        return async(supplier, getTimeout());
    }

    public <R> CompletionStage<R> async(Supplier<R> supplier, long timeout) {
        return guardian.talk(timeout).async(() -> supplier.get());
    }
}
