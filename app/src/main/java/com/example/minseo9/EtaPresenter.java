package com.example.minseo9;

import java.util.Locale;

final class EtaPresenter {

    private EtaPresenter() {
    }

    static boolean isUrgent(int etaMinutes) {
        return etaMinutes >= 0 && etaMinutes <= BusMonitorService.URGENT_THRESHOLD_MINUTES;
    }

    static String buildCaption(
            int locationNo,
            int seatCount,
            String fullFormat,
            String locationOnlyFormat,
            String seatsOnlyFormat
    ) {
        if (locationNo >= 0 && seatCount >= 0) {
            return String.format(Locale.KOREA, fullFormat, locationNo, seatCount);
        }
        if (locationNo >= 0) {
            return String.format(Locale.KOREA, locationOnlyFormat, locationNo);
        }
        if (seatCount >= 0) {
            return String.format(Locale.KOREA, seatsOnlyFormat, seatCount);
        }
        return "";
    }

    static String buildStationLine(String stationName, String format) {
        if (stationName == null || stationName.isEmpty()) {
            return "";
        }
        return String.format(Locale.KOREA, format, stationName);
    }
}
