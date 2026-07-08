package com.example.minseo9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EtaPresenterTest {

    private static final String FULL = "%1$d개 정류장 전 · 잔여좌석 %2$d";
    private static final String LOCATION_ONLY = "%1$d개 정류장 전";
    private static final String SEATS_ONLY = "잔여좌석 %1$d";

    @Test
    public void isUrgent_trueAtThresholdAndBelow() {
        assertTrue(EtaPresenter.isUrgent(3));
        assertTrue(EtaPresenter.isUrgent(0));
    }

    @Test
    public void isUrgent_falseAboveThreshold() {
        assertFalse(EtaPresenter.isUrgent(4));
        assertFalse(EtaPresenter.isUrgent(15));
    }

    @Test
    public void isUrgent_falseForNegativeEta() {
        assertFalse(EtaPresenter.isUrgent(-1));
    }

    @Test
    public void buildCaption_bothKnown_usesFullFormat() {
        String caption = EtaPresenter.buildCaption(20, 12, FULL, LOCATION_ONLY, SEATS_ONLY);
        assertEquals("20개 정류장 전 · 잔여좌석 12", caption);
    }

    @Test
    public void buildCaption_bothZero_usesFullFormat() {
        String caption = EtaPresenter.buildCaption(0, 0, FULL, LOCATION_ONLY, SEATS_ONLY);
        assertEquals("0개 정류장 전 · 잔여좌석 0", caption);
    }

    @Test
    public void buildCaption_locationOnly() {
        String caption = EtaPresenter.buildCaption(5, -1, FULL, LOCATION_ONLY, SEATS_ONLY);
        assertEquals("5개 정류장 전", caption);
    }

    @Test
    public void buildCaption_seatsOnly() {
        String caption = EtaPresenter.buildCaption(-1, 8, FULL, LOCATION_ONLY, SEATS_ONLY);
        assertEquals("잔여좌석 8", caption);
    }

    @Test
    public void buildCaption_neitherKnown_returnsEmpty() {
        String caption = EtaPresenter.buildCaption(-1, -1, FULL, LOCATION_ONLY, SEATS_ONLY);
        assertEquals("", caption);
    }
}
