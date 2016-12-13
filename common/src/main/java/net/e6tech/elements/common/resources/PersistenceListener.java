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
     *
     * @param id The primary key object
     * @param state state
     * @param propertyNames property names
     * @return whether the entity has been modified.
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
