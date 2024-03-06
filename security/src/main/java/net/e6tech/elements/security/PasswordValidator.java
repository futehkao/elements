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
package net.e6tech.elements.security;

public class PasswordValidator {
    public static final String SPECIAL_CHARACTERS = "!@#$%^&*()~`-=_+[]{}|:\";',./<>?";

    private int minPasswordLength;
    private int maxPasswordLength;
    private int minNumberOfGroups;

    private static PasswordValidator defaultValidator = new PasswordValidator(12,32,3);

    public PasswordValidator() {
    }

    public PasswordValidator(int minPasswordLength, int maxPasswordLength, int minNumberOfGroups) {
        this.minPasswordLength = minPasswordLength;
        this.maxPasswordLength = maxPasswordLength;
        this.minNumberOfGroups = minNumberOfGroups;
    }

    public static PasswordValidator getDefaultValidator() {
        return defaultValidator;
    }

    public static void setDefaultValidator(PasswordValidator validator) {
        defaultValidator = validator;
    }

    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    public void setMinPasswordLength(int minPasswordLength) {
        this.minPasswordLength = minPasswordLength;
    }

    public int getMaxPasswordLength() {
        return maxPasswordLength;
    }

    public void setMaxPasswordLength(int maxPasswordLength) {
        this.maxPasswordLength = maxPasswordLength;
    }

    public int getMinNumberOfGroups() {
        return minNumberOfGroups;
    }

    public void setMinNumberOfGroups(int minNumberOfGroups) {
        this.minNumberOfGroups = minNumberOfGroups;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S3776"})
    public boolean check(String pwd) {
        String password = pwd;
        if ((password == null) || (password.length() == 0)) {
            return false;
        }

        password = password.trim();
        int len = password.length();
        if (minPasswordLength > 0 && len < minPasswordLength) {
            return false;
        }

        if (maxPasswordLength > 0 && len > maxPasswordLength) {
            return false;
        }

        int nLowerCase = 0;
        int nUpperCase = 0;
        int nSpecial = 0;
        int nDigits = 0;

        char[] aC = password.toCharArray();
        for(char c : aC) {
            if (Character.isUpperCase(c)) {
                nUpperCase++;
            }
            else if (Character.isLowerCase(c)) {
                nLowerCase++;
            }
            else if (Character.isDigit(c)) {
                nDigits++;
            }
            else if (SPECIAL_CHARACTERS.indexOf(String.valueOf(c)) >= 0) {
                nSpecial++;
            }
            else {
                return false;
            }
        }

        int count = 0;
        if (nUpperCase > 0)
            count++;
        if (nLowerCase > 0)
            count++;
        if (nSpecial > 0)
            count++;
        if (nDigits > 0)
            count++;

        return count >= minNumberOfGroups;
    }

    public static boolean validate(String password) {
        return defaultValidator.check(password);
    }
}