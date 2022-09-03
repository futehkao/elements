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
package net.e6tech.elements.jobs;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.jmx.JMXService;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.*;

/**
 * Created by futeh.
 */
public class JobServer {

    private static Logger logger = Logger.getLogger();

    private Map<String, Job> jobs = new LinkedHashMap<>();
    Scheduler scheduler;

    ResourceManager resourceManager;

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    @Inject
    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public List<String> listJobs() {
        List<String> list = new ArrayList<>(jobs.keySet().size());
        list.addAll(jobs.keySet());
        return list;
    }

    public Map<String, Job> getJobs() {
        return Collections.unmodifiableMap(jobs);
    }

    public Job getJob(String jobName) {
        return jobs.get(jobName);
    }

    public synchronized Scheduler getScheduler() {
        if (scheduler == null) {
            StdSchedulerFactory factory = new StdSchedulerFactory();
            try {
                scheduler = factory.getScheduler();
                scheduler.start();
            } catch (SchedulerException e) {
                logger.warn(e.getMessage(), e);
            }
        }
        return scheduler;
    }

    @SuppressWarnings("unchecked")
    public Job registerJob(String name, Object target) {
        Job job = resourceManager.registerBean(name, Job.class);
        job.setJobServer(this);

        Object instance = target;
        if (target instanceof  Class) {
            instance = resourceManager.newInstance((Class) target);
        }

        JMXService.registerMBean(instance, "net.e6tech:type=JobTarget,name=" + name);
        job.setTarget(instance);
        job.setScheduler(getScheduler());
        job.setName(name);
        JMXService.registerMBean(job, "net.e6tech:type=Job,name=" + name);

        jobs.put(name, job);

        return job;
    }

}
