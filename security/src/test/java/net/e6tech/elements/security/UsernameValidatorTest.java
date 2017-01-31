package net.e6tech.elements.security;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UsernameValidatorTest {

    @Test
    public void test() throws Exception {
        assertFalse(UsernameValidator.validate(null));
        assertFalse(UsernameValidator.validate(""));
        // too short
        assertFalse(UsernameValidator.validate("1"));
        assertFalse(UsernameValidator.validate("12"));
        assertFalse(UsernameValidator.validate("123"));
        assertFalse(UsernameValidator.validate("1234"));
        assertFalse(UsernameValidator.validate("12345"));
        assertFalse(UsernameValidator.validate("123456"));
        assertFalse(UsernameValidator.validate("1234567"));
        // too long
        assertFalse(UsernameValidator.validate("0123456789012345678901234567890123"));
        // no spaces
        assertFalse(UsernameValidator.validate("barry 12"));
        // invalid characters (only "." "-" "_" "@" allowed
        assertFalse(UsernameValidator.validate("passw*rd"));
        assertFalse(UsernameValidator.validate("password!"));
        // only lowercase allowed
        assertFalse(UsernameValidator.validate("BarryMeyer"));

        // valid usernames
        assertTrue(UsernameValidator.validate("barry123"));
        assertTrue(UsernameValidator.validate("barry.meyer"));
        assertTrue(UsernameValidator.validate("barry_meyer"));
        assertTrue(UsernameValidator.validate("barry-meyer"));
    }
}
