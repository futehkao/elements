package net.e6tech.elements.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordValidatorTest {

    @Test
    public void testDefaultValidator() throws Exception {
        assertFalse(PasswordValidator.validate(null));
        assertFalse(PasswordValidator.validate(""));
        // too short
        assertFalse(PasswordValidator.validate("1"));
        assertFalse(PasswordValidator.validate("12"));
        assertFalse(PasswordValidator.validate("123"));
        assertFalse(PasswordValidator.validate("1234"));
        assertFalse(PasswordValidator.validate("12345"));
        assertFalse(PasswordValidator.validate("123456"));
        assertFalse(PasswordValidator.validate("1234567"));
        assertFalse(PasswordValidator.validate("123456789012"));
        // too long (32 limit)                  01234567890123456789012345678901
        assertFalse(PasswordValidator.validate("BarryMeyer11234567890123456789012"));
        // does minimum of 3 groups
        assertFalse(PasswordValidator.validate("barryMEYER"));
        assertFalse(PasswordValidator.validate("barry12345"));
        assertFalse(PasswordValidator.validate("BARRY12345"));
        assertFalse(PasswordValidator.validate("barrymeyer@"));
        assertFalse(PasswordValidator.validate("BARRYMEYER@"));
        assertFalse(PasswordValidator.validate("1234567@"));
        // valid passwords
        assertTrue(PasswordValidator.validate("BarrrrrryMeyer1"));
        assertTrue(PasswordValidator.validate("BarrrrrryMeyer!"));
        assertTrue(PasswordValidator.validate("barrrrrrrrry12!"));
        assertTrue(PasswordValidator.validate("BARRrrrrrrrY12!"));
    }
}
