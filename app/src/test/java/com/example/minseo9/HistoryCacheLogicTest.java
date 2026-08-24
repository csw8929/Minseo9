package com.example.minseo9;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HistoryCacheLogicTest {

    @Test
    public void chooseSlotToPush_bothSlotsEmpty_usesSlot0() {
        int slot = HistoryCacheLogic.chooseSlotToPush("A", null, null, "A", "B");
        assertEquals(HistoryCacheLogic.SLOT_0, slot);
    }

    @Test
    public void chooseSlotToPush_slot0Taken_usesSlot1() {
        int slot = HistoryCacheLogic.chooseSlotToPush("B", "A", null, "A", "B");
        assertEquals(HistoryCacheLogic.SLOT_1, slot);
    }

    @Test
    public void chooseSlotToPush_plateAlreadyInSlot0_updatesInPlace() {
        int slot = HistoryCacheLogic.chooseSlotToPush("A", "A", "B", "A", "B");
        assertEquals(HistoryCacheLogic.SLOT_0, slot);
    }

    @Test
    public void chooseSlotToPush_plateAlreadyInSlot1_updatesInPlace() {
        int slot = HistoryCacheLogic.chooseSlotToPush("B", "A", "B", "A", "B");
        assertEquals(HistoryCacheLogic.SLOT_1, slot);
    }

    @Test
    public void chooseSlotToPush_bothSlotsFull_evictsSlot0WhenSlot0IsStale() {
        // A no longer appears in either live plate this poll; B still does.
        int slot = HistoryCacheLogic.chooseSlotToPush("C", "A", "B", "B", "C");
        assertEquals(HistoryCacheLogic.SLOT_0, slot);
    }

    @Test
    public void chooseSlotToPush_bothSlotsFull_evictsSlot1WhenSlot1IsStale() {
        // B no longer appears live; A still does.
        int slot = HistoryCacheLogic.chooseSlotToPush("C", "A", "B", "A", "C");
        assertEquals(HistoryCacheLogic.SLOT_1, slot);
    }

    @Test
    public void chooseSlotToPush_bothSlotsFullAndBothStale_fallsBackToSlot0() {
        // Neither A nor B appears live this poll (both already left the route).
        int slot = HistoryCacheLogic.chooseSlotToPush("C", "A", "B", "X", "Y");
        assertEquals(HistoryCacheLogic.SLOT_0, slot);
    }

    @Test
    public void chooseSlotToPush_bothSlotsFullAndBothLive_incomingPlateNotLive_drops() {
        // Regression for the "3rd plate" bug: A has left the live response, but the two
        // existing history slots (B, C) are both still live — pushing A's (stale) history
        // must not evict either of them.
        int slot = HistoryCacheLogic.chooseSlotToPush("A", "B", "C", "B", "C");
        assertEquals(HistoryCacheLogic.DROP, slot);
    }

    @Test
    public void chooseSlotToPush_switchingBackAndForthRepeatedly_neverDropsEitherPlate() {
        // A<->B oscillation must never hit the drop path.
        String slot0 = null;
        String slot1 = null;
        String[] sequence = {"A", "B", "A", "B", "A", "B"};
        for (String plate : sequence) {
            int slot = HistoryCacheLogic.chooseSlotToPush(plate, slot0, slot1, "A", "B");
            if (slot == HistoryCacheLogic.SLOT_0) {
                slot0 = plate;
            } else if (slot == HistoryCacheLogic.SLOT_1) {
                slot1 = plate;
            } else {
                throw new AssertionError("A/B oscillation must never drop history, plate=" + plate);
            }
        }
    }

    @Test
    public void findSlot_matchesSlot0() {
        assertEquals(HistoryCacheLogic.SLOT_0, HistoryCacheLogic.findSlot("A", "A", "B"));
    }

    @Test
    public void findSlot_matchesSlot1() {
        assertEquals(HistoryCacheLogic.SLOT_1, HistoryCacheLogic.findSlot("B", "A", "B"));
    }

    @Test
    public void findSlot_noMatch_returnsNotFound() {
        assertEquals(HistoryCacheLogic.NOT_FOUND, HistoryCacheLogic.findSlot("C", "A", "B"));
    }

    @Test
    public void findSlot_emptyCache_returnsNotFound() {
        assertEquals(HistoryCacheLogic.NOT_FOUND, HistoryCacheLogic.findSlot("A", null, null));
    }
}
