package com.example.minseo9;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BusArrivalActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP = "com.example.minseo9.action.STOP_BUS_ARRIVAL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_STOP.equals(intent.getAction())) {
            return;
        }
        BusMonitorService.stop(context);
        BusArrivalNotifier.cancelAll(context);
    }
}
