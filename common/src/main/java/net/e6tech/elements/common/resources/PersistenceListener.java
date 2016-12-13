package net.e6tech.elements.common.resources;

import java.io.Serializable;

/**
 * This inteface should be implemented by classes that are interested in
 * intercepting load and persist.  It is used by Interceptor.
 * Created by futeh.
 */
public interface PersistenceListener {

    boolean onLoad(
            Serializable id,
            Object[] state,
            String[] propertyNames);

    /**
     * This method is called when EntityManager.persist is called.
     */
    boolean onSave(
            Serializable id,
            Object[] state,
            String[] propertyNames);

    /**
     * preFlush is called either EntityManager.flush is called or as part of commit.
     */
    void preFlush();
}
