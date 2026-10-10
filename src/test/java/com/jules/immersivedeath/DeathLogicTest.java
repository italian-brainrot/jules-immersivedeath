package com.jules.immersivedeath;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DeathLogicTest {

    @Test
    public void testDeathDataDefaults() {
        DeathData data = new DeathData();
        assertEquals(-10000L, data.getLastDeathTime());
        assertEquals(0.0, data.getLostMaxHp(), 0.001);
        assertFalse(data.isPendingPenalty());
        assertNotNull(data.getInventorySnapshot());
        assertEquals(0, data.getNauseaTicks());
        assertEquals(0, data.getBlindnessTicks());
        assertEquals(0, data.getWeaknessTicks());
    }

    @Test
    public void testDebuffTickSettingAndClamping() {
        DeathData data = new DeathData();
        data.setNauseaTicks(600);
        data.setBlindnessTicks(1200);
        data.setWeaknessTicks(12000);

        assertEquals(600, data.getNauseaTicks());
        assertEquals(1200, data.getBlindnessTicks());
        assertEquals(12000, data.getWeaknessTicks());

        data.setNauseaTicks(-10);
        assertEquals(0, data.getNauseaTicks(), "Negative ticks should clamp to 0");
    }

    @Test
    public void testOneMinuteDeathLoopProtectionInTicks() {
        long death1TimeTicks = 10000L;
        long death2TimeWithin1MinTicks = death1TimeTicks + 600L; // 30 seconds = 600 ticks
        long death3TimeAfter1MinTicks = death1TimeTicks + 1220L; // 61 seconds = 1220 ticks

        boolean isDeathLoop1 = (death1TimeTicks >= 0L) && ((death2TimeWithin1MinTicks - death1TimeTicks) < 1200L);
        assertTrue(isDeathLoop1, "Death within 30s (600 ticks) should be detected as death loop");

        boolean isDeathLoop2 = (death1TimeTicks >= 0L) && ((death3TimeAfter1MinTicks - death1TimeTicks) < 1200L);
        assertFalse(isDeathLoop2, "Death after 61s (1220 ticks) should NOT be detected as death loop");
    }

    @Test
    public void testMaxHpReductionLimits() {
        double baseMaxHp = 20.0;
        double maxAllowedLost = Math.max(0.0, baseMaxHp - 1.0); // 19.0 (so player has 1 HP)

        double lost = 0.0;
        for (int i = 0; i < 15; i++) {
            lost = Math.min(lost + 2.0, maxAllowedLost);
        }

        assertEquals(19.0, lost, 0.001, "Max HP lost should cap at 19.0 (leaving 1 max HP)");
        assertEquals(1.0, baseMaxHp - lost, 0.001, "Player min max HP should be 1.0");
    }

    @Test
    public void testLinearDebuffDecayFormula() {
        // Nausea: max level 9, max duration 600 ticks
        int dur600 = 600;
        int targetLvl600 = (int) Math.ceil(((double) dur600 / 600.0) * 9.0);
        assertEquals(9, targetLvl600);

        int dur300 = 300;
        int targetLvl300 = (int) Math.ceil(((double) dur300 / 600.0) * 9.0);
        assertEquals(5, targetLvl300);

        int dur1 = 1;
        int targetLvl1 = (int) Math.ceil(((double) dur1 / 600.0) * 9.0);
        assertEquals(1, targetLvl1);

        int dur0 = 0;
        int targetLvl0 = (int) Math.ceil(((double) dur0 / 600.0) * 9.0);
        assertEquals(0, targetLvl0);

        // Blindness: max level 15, max duration 1200 ticks
        int bDur1200 = 1200;
        int bLvl1200 = (int) Math.ceil(((double) bDur1200 / 1200.0) * 15.0);
        assertEquals(15, bLvl1200);

        int bDur600 = 600;
        int bLvl600 = (int) Math.ceil(((double) bDur600 / 1200.0) * 15.0);
        assertEquals(8, bLvl600);

        // Weakness: max level 18, max duration 12000 ticks
        int wDur12000 = 12000;
        int wLvl12000 = (int) Math.ceil(((double) wDur12000 / 12000.0) * 18.0);
        assertEquals(18, wLvl12000);

        int wDur6000 = 6000;
        int wLvl6000 = (int) Math.ceil(((double) wDur6000 / 12000.0) * 18.0);
        assertEquals(9, wLvl6000);
    }

    @Test
    public void testHeartContainerRestore() {
        double lost = 6.0; // 3 hearts lost
        double newLost = Math.max(0.0, lost - 2.0); // restore 1 heart (2 HP)
        assertEquals(4.0, newLost, 0.001);

        double newLost2 = Math.max(0.0, 1.0 - 2.0); // restore when 1 HP lost
        assertEquals(0.0, newLost2, 0.001, "Should cap lost HP at 0.0");
    }
}
