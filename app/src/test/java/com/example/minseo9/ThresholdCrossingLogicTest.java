package com.example.minseo9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ThresholdCrossingLogicTest {

    private static final int[] THRESHOLDS = {15, 10, 5, 3, 1};

    @Test
    public void coldStart_etaAboveAllThresholds_notifiesNothing() {
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 20, new HashSet<>(), THRESHOLDS);
        assertTrue(crossed.isEmpty());
    }

    @Test
    public void coldStart_etaBetweenThresholds_notifiesOnlyTightestApplicable() {
        // eta=4 is <= 15, 10, and 5, but not 3 or 1 — should only fire the tightest (5),
        // not all three it technically qualifies for.
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 4, new HashSet<>(), THRESHOLDS);
        assertEquals(List.of(5), crossed);
    }

    @Test
    public void coldStart_etaExactlyOnThreshold_notifiesThatThreshold() {
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 15, new HashSet<>(), THRESHOLDS);
        assertEquals(List.of(15), crossed);
    }

    @Test
    public void coldStart_etaZero_notifiesTightestThreshold() {
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 0, new HashSet<>(), THRESHOLDS);
        assertEquals(List.of(1), crossed);
    }

    @Test
    public void coldStart_restoredHistoryAlreadyCoversCoarserThresholds_firesOnlyTightestNewOne() {
        // Simulates resolveEffectiveVehicle's history-restore bootstrap path: 15 and 10 were
        // already notified (restored from history), so cold start must fire only the next
        // tightest still-unnotified threshold that eta qualifies for (5), not re-fire 15/10.
        Set<Integer> alreadyNotified = new HashSet<>();
        alreadyNotified.add(15);
        alreadyNotified.add(10);
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 4, alreadyNotified, THRESHOLDS);
        assertEquals(List.of(5), crossed);
    }

    @Test
    public void coldStart_gapInRestoredHistory_stopsAtLastEligibleThresholdBeforeGap() {
        // If a coarser threshold (5) was somehow already notified but a finer one wasn't, the
        // single-selection scan still only picks the last threshold it can update to while
        // walking descending order — it does not "skip past" a notified entry to a finer one.
        Set<Integer> alreadyNotified = new HashSet<>();
        alreadyNotified.add(5);
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 4, alreadyNotified, THRESHOLDS);
        assertEquals(List.of(10), crossed);
    }

    @Test
    public void coldStart_allThresholdsAlreadyNotified_notifiesNothing() {
        Set<Integer> alreadyNotified = new HashSet<>();
        for (int t : THRESHOLDS) {
            alreadyNotified.add(t);
        }
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                null, 0, alreadyNotified, THRESHOLDS);
        assertTrue(crossed.isEmpty());
    }

    @Test
    public void steadyDecrease_crossesSingleThreshold() {
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                16, 14, new HashSet<>(), THRESHOLDS);
        assertEquals(List.of(15), crossed);
    }

    @Test
    public void steadyDecrease_noThresholdCrossedYet_notifiesNothing() {
        // Both previous and current eta sit strictly between 15 and 10 — no boundary crossed.
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                14, 13, new HashSet<>(), THRESHOLDS);
        assertTrue(crossed.isEmpty());
    }

    @Test
    public void largeJump_crossesAllIntermediateThresholdsAtOnce() {
        // A single poll can skip straight from 20 minutes to arrival — every threshold in
        // between must fire, in descending order.
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                20, 0, new HashSet<>(), THRESHOLDS);
        assertEquals(List.of(15, 10, 5, 3, 1), crossed);
    }

    @Test
    public void alreadyNotifiedThreshold_isNeverReNotified() {
        Set<Integer> alreadyNotified = new HashSet<>();
        alreadyNotified.add(15);
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                20, 0, alreadyNotified, THRESHOLDS);
        assertEquals(List.of(10, 5, 3, 1), crossed);
    }

    @Test
    public void etaIncreasing_neverFiresFalsePositive() {
        // Bus moves further away again after already being close — must not re-fire a
        // threshold it's now moving away from.
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                4, 6, new HashSet<>(), THRESHOLDS);
        assertTrue(crossed.isEmpty());
    }

    @Test
    public void etaUnchanged_noThresholdReFired() {
        List<Integer> crossed = ThresholdCrossingLogic.newlyCrossedThresholds(
                5, 5, new HashSet<>(), THRESHOLDS);
        assertTrue(crossed.isEmpty());
    }
}
