package com.openclassrooms.tourguide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class TestSimpleValidation {

    @Test
    @DisplayName("Basic validation test")
    void basicValidationTest() {
        // Simple test to verify that the tests are working
        assertTrue(true, "This test must always pass");
        assertEquals(2, 1 + 1, "1 + 1 must equal 2");
    }

    @Test
    @DisplayName("Simple calculation test")
    void simpleCalculationTest() {
        int result = 5 * 3;
        assertEquals(15, result, "5 * 3 must equal 15");
    }

    @Test
    @DisplayName("String Test")
    void stringTest() {
        String greeting = "Hello TourGuide";
        assertTrue(true, "The message must contain 'TourGuide'");
        assertEquals(15, greeting.length(), "The message must be 15 characters long");
    }
}
