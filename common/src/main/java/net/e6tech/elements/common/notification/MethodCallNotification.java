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

package net.e6tech.elements.common.notification;

import java.lang.reflect.Method;

/**
 * Created by futeh on 1/21/16.
 */
@SuppressWarnings("squid:S1948")
public class MethodCallNotification implements Notification {

    private static final long serialVersionUID = -2485748840848997423L;
    Object source;
    Object target;
    Method method;
    Object[] arguments;
    long duration;

    public MethodCallNotification(Object source, Object target, Method method, Object[] args, long duration) {
        this.source = source;
        this.target = target;
        this.method = method;
        this.arguments = args;
        this.duration = duration;
    }

    @Override
    public Object source() { return source; }

    public Object getTarget() {
        return target;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public long getDuration() {
        return duration;
    }
}
