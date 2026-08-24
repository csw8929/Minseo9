package com.example.minseo9;

final class HistoryCacheLogic {
    static final int SLOT_0 = 0;
    static final int SLOT_1 = 1;
    static final int NOT_FOUND = -1;
    static final int DROP = -1;

    private HistoryCacheLogic() {
    }

    static int chooseSlotToPush(
            String plateNo, String slot0Plate, String slot1Plate,
            String livePlate1, String livePlate2
    ) {
        if (plateNo.equals(slot0Plate)) {
            return SLOT_0;
        }
        if (plateNo.equals(slot1Plate)) {
            return SLOT_1;
        }
        if (slot0Plate == null) {
            return SLOT_0;
        }
        if (slot1Plate == null) {
            return SLOT_1;
        }
        boolean slot0StillLive = slot0Plate.equals(livePlate1) || slot0Plate.equals(livePlate2);
        boolean slot1StillLive = slot1Plate.equals(livePlate1) || slot1Plate.equals(livePlate2);
        if (slot0StillLive && slot1StillLive) {
            boolean plateStillLive = plateNo.equals(livePlate1) || plateNo.equals(livePlate2);
            if (!plateStillLive) {
                return DROP;
            }
        }
        return (slot0StillLive && !slot1StillLive) ? SLOT_1 : SLOT_0;
    }

    static int findSlot(String plateNo, String slot0Plate, String slot1Plate) {
        if (plateNo.equals(slot0Plate)) {
            return SLOT_0;
        }
        if (plateNo.equals(slot1Plate)) {
            return SLOT_1;
        }
        return NOT_FOUND;
    }
}
