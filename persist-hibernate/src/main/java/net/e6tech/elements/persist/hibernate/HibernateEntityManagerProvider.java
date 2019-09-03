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


package net.e6tech.elements.persist.hibernate;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.InstanceNotFoundException;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectReference;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.persist.*;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.internal.SessionImpl;
import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.spi.IdentifierGeneratorStrategyProvider;

import javax.naming.Context;
import javax.persistence.Cache;
import javax.persistence.EntityManager;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class HibernateEntityManagerProvider extends EntityManagerProvider {

    private static Logger logger = Logger.getLogger();

    private Map<String, IdentifierGenerator> identifierGenerators = new LinkedHashMap<>();

    private net.e6tech.elements.common.interceptor.Interceptor interceptor = new net.e6tech.elements.common.interceptor.Interceptor();

    @Override
    public void initialize(Resources resources) {
        if (System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null) {
            InitialContextFactory.setDefault();
        }

        // Note: AvailableSettings.IDENTIFIER_GENERATOR_STRATEGY_PROVIDER is deprecated.
        // note: another way to do it is to subclass HibernatePersistenceProvider and override
        // getEntityManagerFactoryBuilder(PersistenceUnitDescriptor persistenceUnitDescriptor, Map integration, ClassLoader providedClassLoader)
        // to provide a builder (which subclasses EntityManagerFactoryBuilderImpl in order to hook in the strategies.
        if (identifierGenerators.size() > 0) {
            Map<String, Class<?>> strategies = new LinkedHashMap<>();
            for (Map.Entry<String, IdentifierGenerator> entry: identifierGenerators.entrySet()) {
                Class<IdentifierGenerator> cls = interceptor
                        .newPrototypeClass((Class<IdentifierGenerator>)entry.getValue().getClass(), entry.getValue());
                strategies.put(entry.getKey(), cls);
            }

            getPersistenceProperties().put(AvailableSettings.IDENTIFIER_GENERATOR_STRATEGY_PROVIDER,
                    (IdentifierGeneratorStrategyProvider)() -> strategies);
        }
        super.initialize(resources);
    }

    public HibernateEntityManagerProvider register(String strategy, IdentifierGenerator generator) {
        identifierGenerators.put(strategy, generator);
        return this;
    }

    @Override
    protected void evictCollectionRegion(EvictCollectionRegion notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        hibernateCache.evictCollectionData(notification.getRole());
    }

    @Override
    protected void evictEntityRegion(EvictEntityRegion notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        hibernateCache.evictEntityData(notification.getEntityName());
    }

    @Override
    protected void evictEntity(EvictEntity notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        try {
            ObjectReference ref = notification.getObjectReference();
            hibernateCache.evictEntityData(getClass().getClassLoader().loadClass(ref.getType()), (Serializable) ref.getId());
        } catch (ClassNotFoundException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void onOpen(Resources resources) {
        super.onOpen(resources);
        EntityManager em = resources.getMapVariable(EntityManager.class).get(getProviderName());
        SessionImpl session = (SessionImpl) em.getDelegate();
        resources.getMapVariable(SessionImpl.class).put(getProviderName(), session);

        // cannot call resources.inject(interceptor), resources is not fully open yet
        if (session.getInterceptor() instanceof PersistenceInterceptor) {
            PersistenceInterceptor i = (PersistenceInterceptor) session.getInterceptor();
            i.setResources(resources);
            if (i instanceof Interceptor) {
               ((Interceptor) i).setSessionFactory(session.getSessionFactory());
            }
        }
    }

    @Override
    public void cleanup(Resources resources) {
        super.cleanup(resources);
        try {
            SessionImpl session = getSessionImpl(resources);
            if (session.getInterceptor() instanceof PersistenceInterceptor) {
                PersistenceInterceptor i = (PersistenceInterceptor) session.getInterceptor();
                i.cleanup(resources);
            }
        } catch (InstanceNotFoundException ex) {
            // don't care
        }
    }

    @Override
    public void cancelQuery(Resources resources) {
        super.cancelQuery(resources);
        try {
            getSessionImpl(resources).cancelQuery();
        } catch (Exception ex) {
            // don't care
        }
    }

    private SessionImpl getSessionImpl(Resources resources) {
        return resources.getMapVariable(SessionImpl.class).get(getProviderName());
    }

}
