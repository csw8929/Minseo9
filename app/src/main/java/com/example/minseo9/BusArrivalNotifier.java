package com.example.minseo9;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public final class BusArrivalNotifier {
    public static final String CHANNEL_ID = "bus_arrival_watch_v2";
    private static final int NOTIFICATION_ID_BASE = 2000;

    private static final String CHANNEL_NAME = "버스 도착 알림";

    private final Context context;

    public BusArrivalNotifier(Context context) {
        this.context = context.getApplicationContext();
        ensureChannel();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("6900번 버스 도착 단계별 알림");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    public void notifyThreshold(int threshold, String body) {
        String title = "6900번 " + threshold + "분 전입니다";

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(context, BusArrivalActionReceiver.class);
        stopIntent.setAction(BusArrivalActionReceiver.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(
                context, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, "추적 중지", stopPi);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        nm.notify(notificationId(threshold), builder.build());
    }

    public static void cancelAll(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        for (int threshold : BusMonitorService.THRESHOLDS) {
            nm.cancel(notificationId(threshold));
        }
    }

    private static int notificationId(int threshold) {
        return NOTIFICATION_ID_BASE + threshold;
    }
}
