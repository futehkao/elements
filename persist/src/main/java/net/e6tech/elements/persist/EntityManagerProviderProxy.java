/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.persist;

import net.e6tech.elements.common.resources.Resources;

import java.util.Optional;


public class EntityManagerProviderProxy extends EntityManagerProvider {

    private String delegateName;

    @Override
    public void initialize(Resources resources) {
        // do nothing
    }

    public String getDelegateName() {
        return delegateName;
    }

    public void setDelegateName(String delegateName) {
        this.delegateName = delegateName;
    }

    @Override
    public void onOpen(Resources resources, String alias, EntityManagerConfig config) {
        EntityManagerProvider delegate = getResourceManager().getBean(delegateName);
        if (delegate != null) {
            delegate.onOpen(resources, alias, config);
        }
    }

    private Optional<EntityManagerProvider> getDelegate(Resources resources) {
        return Optional.ofNullable(resources.getMapVariable(EntityManagerProvider.class).get(delegateName));
    }

    @Override
    public void afterOpen(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> afterOpen(resources, alias));
    }

    @Override
    public void onCommit(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> onCommit(resources, alias));
    }

    @Override
    public void afterCommit(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> afterCommit(resources, alias));
    }

    @Override
    public void afterAbort(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> afterAbort(resources, alias));
    }

    @Override
    public void onAbort(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> onAbort(resources, alias));
    }

    @Override
    public void onClosed(Resources resources, String alias) {
        getDelegate(resources).ifPresent(delegate -> onClosed(resources, alias));
    }

    @Override
    public void onShutdown(String alias) {
        // do nothing, proxy cannot shut down its delegate
    }
}
