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


package net.e6tech.elements.persist.hibernate;

import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectReference;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.persist.*;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.SessionImpl;

import javax.naming.Context;
import javax.persistence.Cache;
import javax.persistence.EntityManager;
import java.io.Serializable;

/**
 * Created by futeh.
 */
public class HibernateEntityManagerProvider extends EntityManagerProvider {

    @Override
    public void initialize(Resources resources) {
        if (System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null) {
            InitialContextFactory.setDefault();
        }
        super.initialize(resources);
    }

    @Override
    protected void evictCollectionRegion(EvictCollectionRegion notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        hibernateCache.evictCollectionRegion(notification.getRole());
    }

    @Override
    protected void evictEntityRegion(EvictEntityRegion notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        hibernateCache.evictEntityRegion(notification.getEntityName());
    }

    @Override
    protected void evictEntity(EvictEntity notification) {
        Cache cache = emf.getCache();
        org.hibernate.Cache hibernateCache = cache.unwrap(org.hibernate.Cache.class);
        try {
            ObjectReference ref = notification.getObjectReference();
            hibernateCache.evictEntity(getClass().getClassLoader().loadClass(ref.getType()), (Serializable) ref.getId());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(Resources resources) {
        super.onOpen(resources);
        EntityManager em = resources.getInstance(EntityManager.class);
        SessionImpl session = (SessionImpl) em.getDelegate();
        SessionFactoryImplementor  factory = session.getSessionFactory();
        resources.bind(SessionImpl.class, session);
        resources.bind(SessionFactoryImplementor.class, factory);
        if (session.getInterceptor() instanceof PersistenceInterceptor) {
            PersistenceInterceptor interceptor = (PersistenceInterceptor) session.getInterceptor();
            resources.inject(interceptor);
        }
    }

}
