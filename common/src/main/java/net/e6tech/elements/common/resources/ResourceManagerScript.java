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
package net.e6tech.elements.common.resources;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.script.AbstractScriptBase;
import net.e6tech.elements.common.script.Scripting;
import net.e6tech.elements.common.util.SystemException;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public abstract class ResourceManagerScript extends AbstractScriptBase<ResourceManager> {
    private static Logger logger = Logger.getLogger();
    private Bootstrap bootstrap;

    @SuppressWarnings("squid:S1192")
    public Bootstrap getBootstrap() {
        if (bootstrap != null)
            return bootstrap;

        if (hasVariable("bootstrap")) {
            bootstrap = getVariable("bootstrap");
        } else {
            bootstrap = new Bootstrap(getShell());
            getShell().getScripting().put("bootstrap", bootstrap);
        }
        return bootstrap;
    }

    /**
     * This method is for catching key { closure } pattern.  The key could resolve to an instance
     * and the closure would be run with the instance as the delegate.
     * @param name method name
     * @param args arguments
     * @return result
     */
    @Override
    @SuppressWarnings({"squid:S134", "squid:CommentedOutCodeLine", "squid:S3776"})
    public Object invokeMethod(String name, Object args) {
        try {
            return getMetaClass().invokeMethod(this, name, args);
        } catch (MissingMethodException ex) {
            if (ex.getArguments().length > 0 && ex.getArguments()[0] instanceof Closure) {

                // atom() { this is outerClosure
                //    commServer =  CommServer
                //
                //    commServer {  <-- THIS IS the "closure"; its delegate should be outerClosure
                //        addService('pingService', PingServer)
                //        ...
                //    }
                // })

                Closure closure = (Closure) ex.getArguments()[0];
                if (closure.getDelegate() instanceof Closure) {
                    Closure outerClosure = (Closure) closure.getDelegate();
                    if (outerClosure.getDelegate() instanceof Atom) {
                        Atom atom = (Atom) outerClosure.getDelegate();
                        Object value = atom.get(name);
                        if (value == null) {
                            logger.warn("component({})" + " trying to insert Closure with no instance named {}", atom.getName(), name);
                        }
                        return atom.put(name, closure);
                    } else {
                        throw ex;
                    }
                } else {
                    throw ex;
                }
            } else {
                throw ex;
            }
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    public <T> T bind(T resource) {
        return getShell().bind(resource);
    }

    public <T> T bind(Class<T> cls, T resource) {
        return getShell().bind(cls, resource);
    }

    public <T> T rebind(T resource) {
        return getShell().rebind(resource);
    }

    public <T> T rebind(Class<T> cls, T resource) {
        return getShell().rebind(cls, resource);
    }

    public void bindClass(Class a, Class b) {
        getShell().bindClass(a, b);
    }

    public <T> T bindNamedInstance(String name, Class<T> a, T b) {
        return getShell().bindNamedInstance(a, name, b);
    }

    public <T> T bindNamedInstance(String name, T b) {
        return getShell().bindNamedInstance((Class<T>) b.getClass(), name, b);
    }

    public <T> T registerBean(String name, Object instance) {
        return getShell().registerBean(name, instance);
    }

    public void unregisterBean(String name) {
        getShell().unregisterBean(name);
    }

    public <T> T getBean(String name) {
        return getShell().getBean(name);
    }

    public Atom atom(Closure closure) {
        return atom(null, closure);
    }

    public AtomBuilder prototype(String name) {
        return new AtomBuilder(name, true, getShell());
    }

    public Atom prototype(String name, Closure closure) {
        return prototype(name)
                .build(closure);
    }

    public Atom prototype(String name,  String prototypePath, Closure closure) {
        return prototype(name)
                .from(prototypePath)
                .build(closure);
    }

    public AtomBuilder atom(String name) {
        return new AtomBuilder(name, false, getShell());
    }

    public Atom atom(String name, Closure closure) {
        return atom(name).build(closure);
    }

    public Atom atom(String name, String prototypePath, Closure closure) {
        return atom(name)
                .from(prototypePath)
                .build(closure);
    }

    public Bootstrap boot(Object bootScript, Object ... components) {
        getBootstrap().boot(bootScript, components);
        return bootstrap;
    }

    public static class AtomBuilder {
        private Map<String, Object> settings;
        private String name;
        private Closure closure;
        private ResourceManager resourceManager;
        private String prototypePath;
        private boolean prototype = false;

        public AtomBuilder(String name, boolean prototype, ResourceManager resourceManager) {
            this.name = name;
            this.prototype = prototype;
            this.resourceManager = resourceManager;
        }

        public AtomBuilder settings(Map<String, Object> settings) {
            this.settings = settings;
            return this;
        }

        public AtomBuilder from(String prototypePath) {
            this.prototypePath = prototypePath;
            return this;
        }

        public AtomBuilder prototype(boolean p) {
            prototype = p;
            return this;
        }

        public Atom build(Closure closure) {
            this.closure = closure;
            return build();
        }

        public Atom build() {
            Atom existing = resourceManager.getAtom(name);
            if (existing != null)
                return existing;

            boolean hasPrevious = resourceManager.getScripting().containsKey(Atom.OVERRIDE_SETTINGS);
            Map<String, Object> previous = (Map) resourceManager.getScripting().get(Atom.OVERRIDE_SETTINGS);
            Atom prototypeAtom = null;
            try {
                if (settings != null) {
                    resourceManager.getScripting().put(Atom.OVERRIDE_SETTINGS, settings);
                }
                prototypeAtom = (prototypePath != null) ? (Atom) resourceManager.exec(prototypePath) : null;
            } finally {
                if (hasPrevious) {
                   resourceManager.getScripting().put(Atom.OVERRIDE_SETTINGS, previous);
                } else if (settings != null){
                    resourceManager.getScripting().remove(Atom.OVERRIDE_SETTINGS);
                }
            }
            return resourceManager.createAtom(name, atomConsumer(), prototypeAtom, prototype);
        }

        private Consumer<Atom> atomConsumer() {
            if (closure == null)
                return atom -> {};
            return atom -> {
                if (closure != null)
                    atom.setScriptLoader(closure.getClass().getClassLoader());
                else
                    atom.setScriptLoader(resourceManager.getScripting().getScriptLoader());

                final Closure clonedClosure = closure.rehydrate(atom, closure.getOwner(), closure.getOwner());
                clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
                if (settings != null)
                    atom.setDefaultSettings(settings);

                clonedClosure.call(atom);
                // clonedClosure.setDelegate(null); DO NOT set to null.  run after needs it.

                // the delegate, a Component, is still needed for runAfter.  Therefore
                // set the closure delegate to null afterward using cleanup
                resourceManager.addCleanup(() -> {
                    clonedClosure.setDelegate(null);
                    Object owner = clonedClosure.getOwner();
                    if (owner instanceof Closure) {
                        ((Closure) owner).setDelegate(null);
                    }
                });
            };
        }
    }
}
