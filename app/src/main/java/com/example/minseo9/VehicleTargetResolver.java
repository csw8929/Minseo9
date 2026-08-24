package com.example.minseo9;

final class VehicleTargetResolver {
    static final int NONE = 0;

    private VehicleTargetResolver() {
    }

    static boolean isBootstrap(String targetPlateNo) {
        return targetPlateNo == null || targetPlateNo.isEmpty();
    }

    static int resolve(
            String targetPlateNo, int selectedVehicle,
            String plate1, String plate2, int predictTime1, int predictTime2
    ) {
        int selectedPredictTime = selectedVehicle == BusMonitorService.VEHICLE_SECOND
                ? predictTime2 : predictTime1;

        if (isBootstrap(targetPlateNo)) {
            return selectedPredictTime >= 0 ? selectedVehicle : NONE;
        }

        if (targetPlateNo.equals(plate1) && predictTime1 >= 0) {
            return BusMonitorService.VEHICLE_FIRST;
        }
        if (targetPlateNo.equals(plate2) && predictTime2 >= 0) {
            return BusMonitorService.VEHICLE_SECOND;
        }

        boolean bothPlatesMissing = (plate1 == null || plate1.isEmpty())
                && (plate2 == null || plate2.isEmpty());
        if (bothPlatesMissing && selectedPredictTime >= 0) {
            return selectedVehicle;
        }
        return NONE;
    }
}
