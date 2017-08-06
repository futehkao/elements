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
package net.e6tech.elements.common.util;

import net.e6tech.elements.common.logging.Logger;
import org.osjava.sj.memory.MemoryContext;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S2176", "squid:S1319", "squid:S1149"})
public class InitialContextFactory implements javax.naming.spi.InitialContextFactory {
    static InheritableThreadLocal<Context> threadLocal = new InheritableThreadLocal<>();

    public static void setDefault() {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY , InitialContextFactory.class.getName());
    }

    @Override
    public Context getInitialContext(Hashtable environment) throws NamingException {
        Context ctx = threadLocal.get();
        if (ctx == null) {
            ctx = createContext(environment);
            threadLocal.set(ctx);
        }
        return ctx;
    }

    public Context createContext(Hashtable environment) {

        /* The default is 'flat', which isn't hierarchial and not what I want. */
        environment.put("jndi.syntax.direction", "left_to_right");

        /* Separator is required for non-flat */
        environment.put("jndi.syntax.separator", "/");

        Context ctx = new MemoryContext(environment);

        return (Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {Context.class},
                new ContextInvocationHandler(ctx));
    }

    public static class ContextInvocationHandler implements InvocationHandler {
        Context ctx;
        List<Name> bound = new LinkedList<>();

        public ContextInvocationHandler(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("bind".equals(methodName)) {
                Name name = null;
                if (args[0] instanceof String) {
                    name = ctx.getNameParser("").parse((String) args[0]);
                } else if (args[0] instanceof Name) {
                    name = (Name) args[0];
                } else {
                    throw new SystemException("Incompatible method argument[0]: expecting String of Name but got " + args[0]);
                }
                createContexts(name, ctx);
                bound.add(name);
                ctx.bind(name, args[1]);
            } else if ("rebind".equals(methodName)) {
                Name name = null;
                if (args[0] instanceof String) {
                    name = ctx.getNameParser("").parse((String) args[0]);
                } else if (args[0] instanceof Name) {
                    name = (Name) args[0];
                }  else {
                    throw new SystemException("Incompatible method argument[0]: expecting String of Name but got " + args[0]);
                }
                createContexts(name, ctx);
                bound.add(name);
                ctx.rebind(name, args[1]);
            } else if ("close".equals(methodName)) {
                return null;
            } else {
                try {
                    return method.invoke(ctx, args);
                } catch (InvocationTargetException e) {
                    Logger.suppress(e);
                    throw e.getTargetException();
                }
            }
            return null;
        }

        private Context createContexts(Name name, Context context) throws NamingException {
            Context subCtx = context;
            if (name.size() > 1) {
                Object object = subCtx.lookup(name.getPrefix(1));
                if (object instanceof Context) {
                    return createContexts(name.getSuffix(1), (Context) object);
                } else if (object != null) {
                    throw new NamingException("already bound");
                } else {
                    subCtx = subCtx.createSubcontext(name.getPrefix(1));
                    return createContexts(name.getSuffix(1), subCtx);
                }
            }
            return subCtx;
        }
    }

}
