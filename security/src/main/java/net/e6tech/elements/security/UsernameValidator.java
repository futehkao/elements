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
package net.e6tech.elements.security;

import java.util.regex.Pattern;

public class UsernameValidator {
    public static final String USERNAME_PATTERN = "^[a-z0-9._-]{8,32}$";
    private static Pattern pattern = Pattern.compile(USERNAME_PATTERN);

    private UsernameValidator() {
    }

    public static boolean validate(String username) {
        if ((username == null) || (username.length() == 0))
            return false;
        return pattern.matcher(username).matches();
    }
}
