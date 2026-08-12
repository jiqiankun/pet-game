package com.petgame.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameRandom 统一随机工具测试。
 */
class GameRandomTest {

    @Test
    void fixedSeed_shouldProduceDeterministicResults() {
        GameRandom r1 = new GameRandom(12345L);
        GameRandom r2 = new GameRandom(12345L);

        for (int i = 0; i < 100; i++) {
            assertEquals(r1.nextInt(1, 100), r2.nextInt(1, 100));
        }
    }

    @Test
    void nextInt_shouldStayInRange() {
        GameRandom random = new GameRandom(42L);
        for (int i = 0; i < 1000; i++) {
            int val = random.nextInt(5, 10);
            assertTrue(val >= 5 && val <= 10, "nextInt(5,10) 结果越界: " + val);
        }
    }

    @Test
    void nextInt_sameMinMax_shouldReturnMin() {
        GameRandom random = new GameRandom(1L);
        assertEquals(7, random.nextInt(7, 7));
    }

    @Test
    void nextInt_minGreaterThanMax_shouldThrow() {
        GameRandom random = new GameRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(10, 5));
    }

    @Test
    void nextDouble_shouldStayInRange() {
        GameRandom random = new GameRandom(42L);
        for (int i = 0; i < 1000; i++) {
            double val = random.nextDouble(1.0, 2.0);
            assertTrue(val >= 1.0 && val <= 2.0, "nextDouble(1.0,2.0) 结果越界: " + val);
        }
    }

    @Test
    void chance_zeroProbability_shouldNeverHit() {
        GameRandom random = new GameRandom(42L);
        for (int i = 0; i < 100; i++) {
            assertFalse(random.chance(0.0));
        }
    }

    @Test
    void chance_oneProbability_shouldAlwaysHit() {
        GameRandom random = new GameRandom(42L);
        for (int i = 0; i < 100; i++) {
            assertTrue(random.chance(1.0));
        }
    }

    @Test
    void getSeed_shouldReturnConstructorSeed() {
        GameRandom random = new GameRandom(999L);
        assertEquals(999L, random.getSeed());
    }
}
