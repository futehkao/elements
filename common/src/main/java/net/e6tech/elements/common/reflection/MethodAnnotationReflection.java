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

package net.e6tech.elements.common.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MethodAnnotationReflection {

    private MethodAnnotationReflection() {
    }

    /**
     * Looks through the method's declaring class's hierarchy to find a method with a compatible which is annotated with the provided
     * annotation. This is particularly useful if an interface or superclass's method is annotated for a specific reason
     * and a child class overrides it to change the implementation. In that case, this method can be used to get the
     * parent class's method object so the annotations can be read (including Parameter annotations!).
     *
     * @param provided The method to look for the most recent implementation of using this annotation
     * @param annotationCls The annotation the method must contain.
     * @param <T> The type of the Annotation to be searched for.
     * @return Optionally, returns a method that has been annotated with annotationCls. If none is found, empty.
     */
    public static <T extends Annotation> Optional<Method> findMethodWithAnnotation(Method provided, Class<T> annotationCls) {
        if (annotationCls == null) return Optional.empty();
        return findMatchingMethodInClassHierarchy(provided, m -> m.getAnnotation(annotationCls) != null);
    }

    /**
     * Search through the class hierarchy for the first compatible method matching predicate. If none is found, then return empty.
     *
     * Note: This does a depth first search using the following rule:
     * * It first checks the declaring class
     * * When it checks a class, if there is no matching method, then it will check nodes in the following order:
     * 1) Interfaces in order of declaration
     * 2) This class's superclass.
     *
     * Note that if an interface extends other interfaces, then those are also included in this earch using the first rule.
     *
     * @param provided The provided method declaration, used as a starting place for the search.
     * @param predicate A predicate used to test if this method is the one being searched for.
     * @return Optional of the method, if found, or empty if no matching method is found that meets the predicate.
     */
    private static Optional<Method> findMatchingMethodInClassHierarchy(Method provided, Predicate<Method> predicate) {
        if (provided == null) return Optional.empty();
        if (predicate.test(provided)) return Optional.of(provided);

        Class declaring = provided.getDeclaringClass();
        Set<Class> checked = new HashSet<>();

        // Setup stack to check with superclass and interfaces.
        Deque<Class> classesToCheck = new ArrayDeque<>();

        // Adding the superclass first, as it will be checked last
        classesToCheck.add(declaring.getSuperclass());
        // Adding interfaces in reverse order to check them in order of declaration.
        for (int i = declaring.getInterfaces().length - 1; i >= 0; i--) {
            classesToCheck.add(declaring.getInterfaces()[i]);
        }

        // Depth first search using stack, checking interfaces first
        while (!classesToCheck.isEmpty()) {
            Class classToCheck = classesToCheck.pop();

            // Determine if we should bother checking this class
            if (classToCheck == null || classToCheck == Object.class || checked.contains(classToCheck))
                continue;

            // Check this class
            Optional<Method> found = getMethodForClass(provided, classToCheck)
                    // filter by if this annotation is present
                    .filter(predicate);
            if (found.isPresent()) return found;

            // If not found, then mark this as checked
            checked.add(classToCheck);

            // And add all interfaces and superclasses for this class to the stack, starting with superclass
            // so interfaces are at the top of the stack.
            checked.add(classToCheck.getSuperclass());
            for (Class anInterface : classToCheck.getInterfaces()) {
                classesToCheck.add(anInterface);
            }
        }

        return Optional.empty();
    }

    private static Optional<Method> getMethodForClass(Method methodOnDifferentClass, Class targetClass) {
        try {
            // Look to see if there is a method that has an exact signature match
            return Optional.of(targetClass.getMethod(methodOnDifferentClass.getName(), methodOnDifferentClass.getParameterTypes()));
        } catch (NoSuchMethodException e) {
            // ignored.
        }

        // In cases where an interface or abstract class has a more generic type parameter
        // than an implementing class, then we need to search in a more generic way.

        List<Method> sameNameSameParamCount = Arrays.stream(targetClass.getMethods())
                .filter(targetMethod -> methodOnDifferentClass.getName().equals(targetMethod.getName()))
                .filter(targetMethod -> methodOnDifferentClass.getParameterCount() == targetMethod.getParameterCount())
                .collect(Collectors.toList());

        // No matching name / Count of params.
        if (sameNameSameParamCount.isEmpty()) return Optional.empty();

        for (Method potential : sameNameSameParamCount) {
            boolean matches = true;
            for (int i = 0; i < potential.getParameters().length; i++) {
                Parameter potentialParam = potential.getParameters()[i];
                Parameter actualParam = methodOnDifferentClass.getParameters()[i];

                // If the potential param type is not assignable from the actual param type
                if (!potentialParam.getType().isAssignableFrom(actualParam.getType())) {
                    matches = false;
                    break;
                }
            }
            if (matches) return Optional.of(potential);
        }

        return Optional.empty();
    }
}
