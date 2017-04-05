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
package net.e6tech.elements.common.resources;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.script.AbstractScriptBase;

import java.util.function.Consumer;

/**
 * Created by futeh.
 */
abstract public class ResourceManagerScript extends AbstractScriptBase<ResourceManager> {
    Logger logger = Logger.getLogger();

    /**
     * This method is for catching key { closure } pattern.  The key could resolve to an instance
     * and the closure would be run with the instance as the delegate.
     * @param name method name
     * @param args arguments
     * @return result
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            return getMetaClass().invokeMethod(this, name, args);
        } catch (MissingMethodException ex) {
            if (ex.getArguments().length > 0 && ex.getArguments()[0] instanceof Closure) {

                // component() { this is outerClosure
                //    commServer =  CommServer
                //
                //    commServer {  // THIS IS the "closure"; its delegate should be outerClosure
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
                            logger.warn("component(" + atom.getName() + ")" + " trying to insert Closure with no instance named " + name);
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
        } catch (Throwable ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void bindClass(Class a, Class b) {
        ResourceManager resourceManager =  getVariable("resourceManager");
        resourceManager.bindClass(a, b);
    }

    public <T> T bindNamedInstance(String name, Class<T> a, T b) {
        ResourceManager resourceManager =  getVariable("resourceManager");
        return resourceManager.bindNamedInstance(name, a, b);
    }

    public <T> T bindNamedInstance(String name, T b) {
        ResourceManager resourceManager =  getVariable("resourceManager");
        return resourceManager.bindNamedInstance(name, (Class<T>) b.getClass(), b);
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

    public Atom prototype(String name, Closure closure) {
        Consumer<Atom> consumer = atomConsumer(closure);
        Atom atom = getShell().createAtom(name, consumer, null, true);
        return atom;
    }

    public Atom prototype(String name,  String prototypePath, Closure closure) {
        Atom prototype = (Atom) getShell().exec(prototypePath);
        Consumer<Atom> consumer = atomConsumer(closure);
        Atom atom = getShell().createAtom(name, consumer, prototype, true);
        return atom;
    }

    public Atom atom(String name, Closure closure) {
        Consumer<Atom> consumer = atomConsumer(closure);
        Atom atom = getShell().createAtom(name, consumer, null, false);
        return atom;
    }

    public Atom atom(String name, String prototypePath,  Closure closure) {
        Atom prototype = (Atom) getShell().exec(prototypePath);
        Consumer<Atom> consumer = atomConsumer(closure);
        Atom atom = getShell().createAtom(name, consumer, prototype, false);
        return atom;
    }

    private Consumer<Atom> atomConsumer(Closure closure) {
        Consumer<Atom> consumer = (atom) -> {
            final Closure clonedClosure = closure.rehydrate(atom, closure.getOwner(), closure.getOwner());
            clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
            clonedClosure.call(atom);
            // clonedClosure.setDelegate(null); DO NOT set to null.  run after needs it.

            // the delegate, a Component, is still needed for runAfter.  Therefore
            // set the closure delegate to null afterward using cleanup
            getShell().addCleanup(() -> {
                clonedClosure.setDelegate(null);
                Object owner = clonedClosure.getOwner();
                if (owner != null && owner instanceof Closure) {
                    ((Closure) owner).setDelegate(null);
                }
            });
        };
        return consumer;
    }
}
