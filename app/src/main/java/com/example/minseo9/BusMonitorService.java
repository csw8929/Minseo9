package com.example.minseo9;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BusMonitorService extends Service {
    private static final String TAG = "BusMonitorService";
    public static final String ACTION_STATUS = "com.example.minseo9.ACTION_STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_ETA_MINUTES = "eta_minutes";
    public static final String EXTRA_LOCATION_NO = "location_no";
    public static final String EXTRA_SEAT_COUNT = "seat_count";
    public static final String EXTRA_FORCE_REFRESH = "force_refresh";
    public static final int VEHICLE_FIRST = 1;
    public static final int VEHICLE_SECOND = 2;
    public static final int STATION_ID = 228000883;
    public static final int ROUTE_ID = 234000027;

    private static final String STATION_LABEL = "동천동현대홈타운2차아파트(29116)";
    private static final String ROUTE_LABEL = "6900 잠실종합운동장 방향";
    private static final String CHANNEL_ID = "bus_monitor";
    private static final String PREFS_NAME = "bus_monitor";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_SELECTED_VEHICLE = "selected_vehicle";
    private static final String KEY_PREVIOUS_ETA = "previous_eta";
    private static final String KEY_NOTIFIED_MASK = "notified_mask";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;
    private static final long WAKE_LOCK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    public static final int URGENT_THRESHOLD_MINUTES = 3;
    public static final int[] THRESHOLDS = {15, 10, 5, URGENT_THRESHOLD_MINUTES, 1};

    private final GbisArrivalClient arrivalClient = new GbisArrivalClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> notifiedThresholds = new HashSet<>();
    private BusArrivalNotifier arrivalNotifier;
    private ScheduledExecutorService executorService;
    private Integer previousEtaMinutes;
    private int currentSelectedVehicle = VEHICLE_FIRST;
    private PowerManager.WakeLock wakeLock;

    public static boolean start(Context context) {
        setMonitoringActive(context, true);
        Intent intent = new Intent(context, BusMonitorService.class);
        intent.putExtra(EXTRA_FORCE_REFRESH, true);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException exception) {
            setMonitoringActive(context, false);
            return false;
        }
    }

    public static void refreshNow(Context context) {
        Intent intent = new Intent(context, BusMonitorService.class);
        intent.putExtra(EXTRA_FORCE_REFRESH, true);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        setMonitoringActive(context, false);
        resetNotificationState(context);
        BusArrivalNotifier.cancelAll(context);
        context.stopService(new Intent(context, BusMonitorService.class));
    }

    public static boolean isMonitoringActive(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ACTIVE, false);
    }

    public static int getSelectedVehicle(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_SELECTED_VEHICLE, VEHICLE_FIRST);
    }

    public static void setSelectedVehicle(Context context, int vehicleIndex) {
        int safeVehicleIndex = vehicleIndex == VEHICLE_SECOND ? VEHICLE_SECOND : VEHICLE_FIRST;
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_SELECTED_VEHICLE, safeVehicleIndex)
                .apply();
    }

    public static void resetNotificationState(Context context) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_PREVIOUS_ETA)
                .putInt(KEY_NOTIFIED_MASK, 0)
                .apply();
    }

    private static void setMonitoringActive(Context context, boolean active) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, active)
                .apply();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        arrivalNotifier = new BusArrivalNotifier(this);
        currentSelectedVehicle = getSelectedVehicle(this);
        loadNotificationState();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithNotification("도착 정보를 확인하고 있습니다.");
        startPolling();
        if (intent != null && intent.getBooleanExtra(EXTRA_FORCE_REFRESH, false)) {
            pollOnce();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        releaseWakeLock();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startPolling() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(this::pollArrival, 30, 30, TimeUnit.SECONDS);
    }

    private void pollOnce() {
        startPolling();
        executorService.execute(this::pollArrival);
    }

    private void pollArrival() {
        renewWakeLock();
        try {
            GbisArrivalClient.Arrival arrival = arrivalClient.fetchArrival(STATION_ID, ROUTE_ID);
            int selectedVehicle = getSelectedVehicle(this);
            if (arrival.predictTime(selectedVehicle) < 0) {
                publishStatus("도착 예정 정보가 없습니다.");
                updateForegroundNotification("도착 예정 정보가 없습니다.");
                return;
            }

            if (selectedVehicle != currentSelectedVehicle) {
                currentSelectedVehicle = selectedVehicle;
                previousEtaMinutes = null;
                notifiedThresholds.clear();
                saveNotificationState();
            }
            int selectedEtaMinutes = arrival.predictTime(selectedVehicle);
            String status = formatStatus(arrival);
            publishStatus(status, selectedEtaMinutes,
                    arrival.locationNo(selectedVehicle), arrival.remainSeatCount(selectedVehicle));
            updateForegroundNotification(status);
            if (selectedEtaMinutes >= 0) {
                notifyCrossedThresholds(arrival, selectedVehicle);
                previousEtaMinutes = selectedEtaMinutes;
                saveNotificationState();
            }

            if (selectedEtaMinutes >= 0 && (selectedEtaMinutes <= 0 || notifiedThresholds.contains(1))) {
                finishMonitoring("선택한 차량이 도착해 모니터링을 종료했습니다.");
            }
        } catch (IOException exception) {
            Log.e(TAG, "도착 정보 조회 실패", exception);
            publishStatus("도착 정보 조회 실패: " + exception.getMessage());
        } catch (RuntimeException exception) {
            Log.e(TAG, "도착 정보 처리 중 오류", exception);
            publishStatus("도착 정보 처리 중 오류가 발생했습니다: " + exception.getMessage());
        }
    }

    private String formatStatus(GbisArrivalClient.Arrival arrival) {
        StringBuilder builder = new StringBuilder();
        int selectedVehicle = getSelectedVehicle(this);
        builder.append("알림 대상: ")
                .append(vehicleLabel(selectedVehicle))
                .append("\n")
                .append(formatVehicleLine(arrival, VEHICLE_FIRST, selectedVehicle))
                .append("\n\n")
                .append(formatVehicleLine(arrival, VEHICLE_SECOND, selectedVehicle));
        return builder.toString();
    }

    private String formatVehicleLine(GbisArrivalClient.Arrival arrival, int vehicleIndex, int selectedVehicle) {
        int etaMinutes = arrival.predictTime(vehicleIndex);
        if (etaMinutes < 0) {
            return linePrefix(vehicleIndex, selectedVehicle) + vehicleLabel(vehicleIndex) + ": 정보 없음";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(linePrefix(vehicleIndex, selectedVehicle))
                .append(vehicleLabel(vehicleIndex))
                .append(": ")
                .append(etaMinutes)
                .append("분 전");

        int locationNo = arrival.locationNo(vehicleIndex);
        if (locationNo >= 0) {
            builder.append(" · ").append(locationNo).append("개 정류장 전");
        }

        String stationName = arrival.stationName(vehicleIndex);
        if (!stationName.isEmpty()) {
            builder.append(" · 현재 ").append(stationName);
        }
        return builder.toString();
    }

    private void notifyCrossedThresholds(GbisArrivalClient.Arrival arrival, int vehicleIndex) {
        int etaMinutes = arrival.predictTime(vehicleIndex);

        if (previousEtaMinutes == null) {
            Integer selectedThreshold = null;
            for (int threshold : THRESHOLDS) {
                if (!notifiedThresholds.contains(threshold) && etaMinutes <= threshold) {
                    selectedThreshold = threshold;
                }
            }
            if (selectedThreshold == null) {
                return;
            }
            notifiedThresholds.add(selectedThreshold);
            saveNotificationState();
            arrivalNotifier.notifyThreshold(selectedThreshold, formatNotificationBody(arrival, vehicleIndex));
            return;
        }

        boolean anyNotified = false;
        for (int threshold : THRESHOLDS) {
            if (notifiedThresholds.contains(threshold)) {
                continue;
            }
            if (previousEtaMinutes > threshold && etaMinutes <= threshold) {
                notifiedThresholds.add(threshold);
                arrivalNotifier.notifyThreshold(threshold, formatNotificationBody(arrival, vehicleIndex));
                anyNotified = true;
            }
        }

        if (anyNotified) {
            saveNotificationState();
        }
    }

    private void loadNotificationState() {
        int storedPreviousEta = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_PREVIOUS_ETA, -1);
        previousEtaMinutes = storedPreviousEta >= 0 ? storedPreviousEta : null;

        notifiedThresholds.clear();
        int mask = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_NOTIFIED_MASK, 0);
        for (int threshold : THRESHOLDS) {
            if ((mask & thresholdBit(threshold)) != 0) {
                notifiedThresholds.add(threshold);
            }
        }
    }

    private void saveNotificationState() {
        int mask = 0;
        for (int threshold : notifiedThresholds) {
            mask |= thresholdBit(threshold);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_PREVIOUS_ETA, previousEtaMinutes != null ? previousEtaMinutes : -1)
                .putInt(KEY_NOTIFIED_MASK, mask)
                .apply();
    }

    private int thresholdBit(int threshold) {
        return 1 << threshold;
    }

    private void finishMonitoring(String status) {
        setMonitoringActive(this, false);
        publishStatus(status);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private String formatNotificationBody(GbisArrivalClient.Arrival arrival, int vehicleIndex) {
        String arrivalStationName = arrival.stationName(vehicleIndex);
        String stationName = arrivalStationName.isEmpty() ? "현재 위치 확인 중" : arrivalStationName;
        int remainSeatCount = arrival.remainSeatCount(vehicleIndex);
        String seatCount = remainSeatCount >= 0
                ? String.valueOf(remainSeatCount)
                : "-";
        int locationNo = arrival.locationNo(vehicleIndex);
        String locationText = locationNo >= 0
                ? locationNo + " 정거장 전"
                : "남은 정거장 확인 중";
        return stationName + "(" + seatCount + ") - " + locationText;
    }

    private String vehicleLabel(int vehicleIndex) {
        return vehicleIndex == VEHICLE_SECOND ? "다음 차량" : "이번 차량";
    }

    private String linePrefix(int vehicleIndex, int selectedVehicle) {
        return vehicleIndex == selectedVehicle ? "▶ " : "   ";
    }

    private void updateForegroundNotification(String status) {
        mainHandler.post(() -> getNotificationManager().notify(
                FOREGROUND_NOTIFICATION_ID,
                buildStatusNotification(status)
        ));
    }

    private Notification buildStatusNotification(String status) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(String.format(Locale.KOREA, "%s 감시 중", ROUTE_LABEL))
                .setContentText(STATION_LABEL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(createMainPendingIntent())
                .build();
    }

    private void startForegroundWithNotification(String status) {
        Notification notification = buildStatusNotification(status);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent createMainPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private void publishStatus(String status) {
        publishStatus(status, -1, -1, -1);
    }

    private void publishStatus(String status, int etaMinutes, int locationNo, int seatCount) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_ETA_MINUTES, etaMinutes);
        intent.putExtra(EXTRA_LOCATION_NO, locationNo);
        intent.putExtra(EXTRA_SEAT_COUNT, seatCount);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "버스 모니터링",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("6900번 도착 정보를 주기적으로 확인");
        getNotificationManager().createNotificationChannel(channel);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private synchronized void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Minseo9:BusMonitor");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
    }

    private synchronized void renewWakeLock() {
        if (wakeLock != null) {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }
}
