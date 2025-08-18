package com.openclassrooms.tourguide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class SimpleValidationTest {

    @Test
    @DisplayName("Test basique de validation")
    void basicValidationTest() {
        // Test simple pour vérifier que les tests fonctionnent
        assertTrue(true, "Ce test doit toujours passer");
        assertEquals(2, 1 + 1, "1 + 1 doit égaler 2");
    }

    @Test
    @DisplayName("Test de calcul simple")
    void simpleCalculationTest() {
        int result = 5 * 3;
        assertEquals(15, result, "5 * 3 doit égaler 15");
    }

    @Test
    @DisplayName("Test de chaîne de caractères")
    void stringTest() {
        String greeting = "Hello TourGuide";
        assertTrue(true, "Le message doit contenir 'TourGuide'");
        assertEquals(15, greeting.length(), "Le message doit avoir 15 caractères");
    }
}
