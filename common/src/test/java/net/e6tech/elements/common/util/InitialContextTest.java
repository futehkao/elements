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
package net.e6tech.elements.common.util;

import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * Created by futeh.
 */
public class InitialContextTest {

    @Test
    public void test() throws Exception {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY , net.e6tech.elements.common.util.InitialContextFactory.class.getName());
        InitialContext ctx = new InitialContext();
        ctx.bind("a/b/c", "hello world");
        System.out.println(ctx.lookup("a/b/c"));

        ctx.bind("a/b/d", "hello world2");
        System.out.println(ctx.lookup("a/b/d"));

        ctx.bind("java:dataSource/test", "hello world3");
        System.out.println(ctx.lookup("java:dataSource/test"));

        ctx.bind("a.b.c/d e/f.g.h/i", "a.b.c");
        System.out.println(ctx.lookup("a.b.c/d e/f.g.h/i"));

        Context subctx = (Context) ctx.lookup("a.b.c/d e/f.g.h");
        System.out.println(subctx.lookup("h"));

        ctx.close();
    }
}
