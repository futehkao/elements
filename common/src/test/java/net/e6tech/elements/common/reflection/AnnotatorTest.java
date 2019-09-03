/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.common.reflection;

import net.e6tech.elements.common.Tags;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
@Tags.Common
public class AnnotatorTest {

    @Test
    public void basic() {
        Annotation1 a1 = Annotator.create(Annotation1.class, (setter, a) -> {
            setter.set(a::x, '0')
                    .set(a::y, "y")
                    .set(a::z, 1L);
        });
        System.out.println(a1.x());
        System.out.println(a1.y());

        System.out.println(a1.annotationType());
        System.out.println(a1.toString());
        System.out.println(a1.hashCode());

        Annotation1 a2 = Annotated.class.getAnnotation(Annotation1.class);
        System.out.println(a2.annotationType());
        System.out.println(a2.toString());
        System.out.println(a2.hashCode());

        assertTrue(a1.equals(a2));
        assertTrue(a2.equals(a1));
        assertTrue(a1.equals(a1));

    }

    public void put(Object obj, Callable call) {

    }

    @Target({ METHOD, TYPE })
    @Retention(RUNTIME)
    public @interface Annotation1 {
        char x() default '0';
        String y();
        long z();
    }

    @Annotation1(y = "y", z = 1L)
    public static class Annotated {

    }
}
