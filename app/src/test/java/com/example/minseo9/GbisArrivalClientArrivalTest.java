package com.example.minseo9;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GbisArrivalClientArrivalTest {

    @Test
    public void predictTime_selectsFirstVehicleByDefault() {
        GbisArrivalClient.Arrival arrival = new GbisArrivalClient.Arrival(
                8, "", -1, "", "", "", "", "", "", -1, "");

        assertEquals(8, arrival.predictTime(BusMonitorService.VEHICLE_FIRST));
    }

    @Test
    public void predictTime_selectsSecondVehicleWhenRequested() {
        GbisArrivalClient.Arrival arrival = new GbisArrivalClient.Arrival(
                -1, "12", -1, "", "", "", "", "", "", -1, "");

        assertEquals(12, arrival.predictTime(BusMonitorService.VEHICLE_SECOND));
    }

    @Test
    public void predictTime_secondVehicleFallsBackToMinusOneWhenUnparseable() {
        GbisArrivalClient.Arrival arrival = new GbisArrivalClient.Arrival(
                5, "", -1, "", "", "", "", "", "", -1, "");

        assertEquals(-1, arrival.predictTime(BusMonitorService.VEHICLE_SECOND));
    }
}
