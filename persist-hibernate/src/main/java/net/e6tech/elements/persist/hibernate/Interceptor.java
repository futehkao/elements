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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.resources.PersistenceListener;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectReference;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.persist.EvictCollectionRegion;
import net.e6tech.elements.persist.EvictEntity;
import net.e6tech.elements.persist.PersistenceInterceptor;
import net.e6tech.elements.persist.Watcher;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Created by futeh.
 */
public class Interceptor extends EmptyInterceptor implements PersistenceInterceptor {

    private static final long serialVersionUID = 2386314971138960957L;
    // below @Inject happens during HibernateEntityManagerProvider.afterOpen(Resources resources)
    private transient Resources resources;
    private transient SessionFactoryImplementor sessionFactory;
    private transient NotificationCenter notificationCenter;

    public Resources getResources() {
        return resources;
    }

    @Inject(optional = true)
    @Override
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public SessionFactoryImplementor getSessionFactory() {
        return sessionFactory;
    }

    @Inject(optional = true)
    public void setSessionFactory(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public NotificationCenter getNotificationCenter() {
        return notificationCenter;
    }

    @Inject(optional = true)
    public void setNotificationCenter(NotificationCenter notificationCenter) {
        this.notificationCenter = notificationCenter;
    }

    public void cleanup(Resources resources) {
        this.resources = null;
        this.sessionFactory = null;
        this.notificationCenter = null;
    }

    @Override
    public boolean onFlushDirty(
            Object entity,
            Serializable id,
            Object[] currentState,
            Object[] previousState,
            String[] propertyNames,
            Type[] types) {
        boolean modified = false;
        if (entity instanceof PersistenceListener) {
            if (resources != null)
                resources.inject(entity);
            long start = System.currentTimeMillis();
            modified = ((PersistenceListener) entity).onFlush(id, currentState, previousState, propertyNames);
            Watcher.addGracePeriod(System.currentTimeMillis() - start);
        }
        return modified;
    }


    @Override
    public boolean onLoad(
            Object entity,
            Serializable id,
            Object[] state,
            String[] propertyNames,
            Type[] types) {
        // commented out because of performance impact
        // we could consider if resources is not null then resources.inject(entity)
        // however, performance may be problem
        // UPDATE: performance definitely be problematic
        boolean modified = false;
        if (entity instanceof PersistenceListener) {
            if (resources != null)
                resources.inject(entity);
            long start = System.currentTimeMillis();
            modified = ((PersistenceListener) entity).onLoad(id, state, propertyNames);
            Watcher.addGracePeriod(System.currentTimeMillis() - start);
        }
        return modified;
    }

    /*
     * When persist is called ...
     */
    @Override
    public boolean onSave(
            Object entity,
            Serializable id,
            Object[] state,
            String[] propertyNames,
            Type[] types) {
        // commented out because of performance impact
        // we could consider if resources is not null then resources.inject(entity)
        // however, performance may be problem.
        // UPDATE: performance definitely be problematic
        boolean modified = false;
        if (entity instanceof PersistenceListener) {
            if (resources != null)
                resources.inject(entity);
            long start = System.currentTimeMillis();
            modified = ((PersistenceListener) entity).onSave(id, state, propertyNames);
            Watcher.addGracePeriod(System.currentTimeMillis() - start);
        }

        publishEntityChanged(entity, id);

        return modified;
    }

    /*
     * This call is triggered by execution of queries or commit
     */
    @Override
    public void preFlush(Iterator entities) {
        List<PersistenceListener> listeners = null;
        while (entities.hasNext()) {
            Object entity = entities.next();
            if (entity instanceof PersistenceListener) {
                if (listeners == null)
                    listeners = new ArrayList<>();
                listeners.add((PersistenceListener) entity);
            }
        }
        if (listeners != null) {
            try {
                long start = System.currentTimeMillis();
                for (PersistenceListener p : listeners) {
                    p.preFlush();
                }
                Watcher.addGracePeriod(System.currentTimeMillis() - start);
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
    }

    @Override
    public void onCollectionUpdate(Object collection, Serializable key) {
        publishCollectionChanged(collection);
    }

    @Override
    public void onCollectionRemove(Object collection, Serializable key) {
        publishCollectionChanged(collection);
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    protected void publishCollectionChanged(Object collection) {

        if (notificationCenter != null && collection instanceof PersistentCollection) {
            PersistentCollection coll = (PersistentCollection) collection;
            boolean cached = false;
            if (sessionFactory != null) {
                cached = sessionFactory.getMetamodel().collectionPersister(coll.getRole()).hasCache();
            }

            /* Another way of doing it
            EntityManager em = resources.getInstance(EntityManager.class);
            Cache cache = em.unwrap(Session.class).getSessionFactory().getCache();
            boolean cached = cache.containsCollection(coll.getRole(), key);
            */
            if (cached) {
                // publisher.publish(EntityManagerProvider.CACHE_EVICT_COLLECTION_REGION, coll.getRole());
                // center.fireNotification(new EvictCollectionRegion(coll.getRole()));
                notificationCenter.publish(EvictCollectionRegion.class, new EvictCollectionRegion(coll.getRole()));
            }
        }
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    protected void publishEntityChanged(Object entity, Serializable key) {
        boolean cached = false;
        if (notificationCenter != null) {
            if (sessionFactory != null) {
                cached = sessionFactory.getMetamodel().locateEntityPersister(entity.getClass()).canWriteToCache();
            }
            if (cached) {
                // center.fireNotification(new EvictEntity(this, new ObjectReference(entity.getClass(), key)));
                notificationCenter.publish(EvictEntity.class, new EvictEntity(new ObjectReference(entity.getClass(), key)));
            }
        }
    }
}
