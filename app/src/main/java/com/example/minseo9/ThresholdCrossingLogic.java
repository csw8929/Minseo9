package com.example.minseo9;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ThresholdCrossingLogic {
    private ThresholdCrossingLogic() {
    }

    static List<Integer> newlyCrossedThresholds(
            Integer previousEtaMinutes, int etaMinutes, Set<Integer> notifiedThresholds, int[] thresholds
    ) {
        List<Integer> crossed = new ArrayList<>();

        if (previousEtaMinutes == null) {
            Integer selectedThreshold = null;
            for (int threshold : thresholds) {
                if (!notifiedThresholds.contains(threshold) && etaMinutes <= threshold) {
                    selectedThreshold = threshold;
                }
            }
            if (selectedThreshold != null) {
                crossed.add(selectedThreshold);
            }
            return crossed;
        }

        for (int threshold : thresholds) {
            if (notifiedThresholds.contains(threshold)) {
                continue;
            }
            if (previousEtaMinutes > threshold && etaMinutes <= threshold) {
                crossed.add(threshold);
            }
        }
        return crossed;
    }
}
