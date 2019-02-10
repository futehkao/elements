/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.persist.criteria;

import net.e6tech.elements.common.interceptor.Interceptor;
import net.e6tech.elements.common.interceptor.InterceptorHandler;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;

/**
 * Created by futeh.
 */
public abstract class Handler implements InterceptorHandler {

    static Interceptor interceptor = new Interceptor();

    private EntityManager entityManager;
    private CriteriaBuilder builder;
    private CriteriaQuery query;
    private Path path;

    public Handler(EntityManager entityManager, CriteriaBuilder builder, CriteriaQuery query, Path path) {
        this.entityManager = entityManager;
        this.builder = builder;
        this.query = query;
        this.path = path;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public CriteriaBuilder getBuilder() {
        return builder;
    }

    public void setBuilder(CriteriaBuilder builder) {
        this.builder = builder;
    }

    public CriteriaQuery getQuery() {
        return query;
    }

    public void setQuery(CriteriaQuery query) {
        this.query = query;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public abstract void onQuery();
}
