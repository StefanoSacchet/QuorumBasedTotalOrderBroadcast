package it.unitn.ds1.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class ExampleTest {

    @BeforeAll
    static void setUp() throws IOException {
        System.out.println("Setting up the test");
    }

    @Test
    void testAddition() {
        int result = 1 + 1;
        assertEquals(2, result, "1 + 1 should equal 2");
    }

    @Test
    void testSubtraction() {
        int result = 5 - 3;
        assertEquals(2, result, "5 - 3 should equal 2");
    }
}
