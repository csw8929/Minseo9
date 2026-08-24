package com.example.minseo9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VehicleTargetResolverTest {

    @Test
    public void isBootstrap_nullPlate_true() {
        assertTrue(VehicleTargetResolver.isBootstrap(null));
    }

    @Test
    public void isBootstrap_emptyPlate_true() {
        assertTrue(VehicleTargetResolver.isBootstrap(""));
    }

    @Test
    public void isBootstrap_nonEmptyPlate_false() {
        assertFalse(VehicleTargetResolver.isBootstrap("12가1234"));
    }

    @Test
    public void resolve_bootstrap_selectsFirstVehicleWhenEtaKnown() {
        int result = VehicleTargetResolver.resolve(
                null, BusMonitorService.VEHICLE_FIRST, "A", "B", 8, 20);
        assertEquals(BusMonitorService.VEHICLE_FIRST, result);
    }

    @Test
    public void resolve_bootstrap_selectsSecondVehicleWhenEtaKnown() {
        int result = VehicleTargetResolver.resolve(
                "", BusMonitorService.VEHICLE_SECOND, "A", "B", 8, 20);
        assertEquals(BusMonitorService.VEHICLE_SECOND, result);
    }

    @Test
    public void resolve_bootstrap_noEtaForSelectedVehicle_returnsNone() {
        int result = VehicleTargetResolver.resolve(
                null, BusMonitorService.VEHICLE_FIRST, "A", "B", -1, 20);
        assertEquals(VehicleTargetResolver.NONE, result);
    }

    @Test
    public void resolve_targetStillInFirstSlot_followsIt() {
        int result = VehicleTargetResolver.resolve(
                "A", BusMonitorService.VEHICLE_SECOND, "A", "B", 8, 20);
        assertEquals(BusMonitorService.VEHICLE_FIRST, result);
    }

    @Test
    public void resolve_targetStillInSecondSlot_followsIt() {
        int result = VehicleTargetResolver.resolve(
                "B", BusMonitorService.VEHICLE_FIRST, "A", "B", 8, 20);
        assertEquals(BusMonitorService.VEHICLE_SECOND, result);
    }

    @Test
    public void resolve_targetMatchesSlotButThatSlotHasNoEta_doesNotFollow() {
        // Plate matches slot 1, but its predictTime is missing this poll — should not be
        // treated as "found" via that slot.
        int result = VehicleTargetResolver.resolve(
                "A", BusMonitorService.VEHICLE_FIRST, "A", "B", -1, 20);
        assertEquals(VehicleTargetResolver.NONE, result);
    }

    @Test
    public void resolve_targetNotInEitherSlot_platesStillPresent_returnsNone() {
        int result = VehicleTargetResolver.resolve(
                "C", BusMonitorService.VEHICLE_FIRST, "A", "B", 8, 20);
        assertEquals(VehicleTargetResolver.NONE, result);
    }

    @Test
    public void resolve_bothPlatesMissingThisPoll_fallsBackToSelectedVehicle() {
        // Metadata blip: GBIS omitted plate numbers this poll but still returned an ETA for
        // the selected slot — should not be treated as "target missing".
        int result = VehicleTargetResolver.resolve(
                "A", BusMonitorService.VEHICLE_SECOND, "", "", -1, 12);
        assertEquals(BusMonitorService.VEHICLE_SECOND, result);
    }

    @Test
    public void resolve_bothPlatesMissingThisPoll_selectedVehicleAlsoHasNoEta_returnsNone() {
        int result = VehicleTargetResolver.resolve(
                "A", BusMonitorService.VEHICLE_SECOND, null, null, -1, -1);
        assertEquals(VehicleTargetResolver.NONE, result);
    }

    @Test
    public void resolve_onlyOnePlateMissing_otherPlatePresentButDifferentTarget_returnsNone() {
        // Only one plate slot is empty; the other still reports a plate that isn't the target,
        // so this is NOT the "both plates missing" blip fallback.
        int result = VehicleTargetResolver.resolve(
                "C", BusMonitorService.VEHICLE_FIRST, "A", "", 8, -1);
        assertEquals(VehicleTargetResolver.NONE, result);
    }
}
