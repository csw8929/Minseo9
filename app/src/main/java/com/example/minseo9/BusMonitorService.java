package com.example.minseo9;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    public static final String EXTRA_STATION_NAME = "station_name";
    public static final String EXTRA_UPDATED_AT = "updated_at";
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
    private static final String KEY_TARGET_PLATE_NO = "target_plate_no";
    private static final String KEY_TARGET_PREVIOUS_ETA = "target_previous_eta";
    private static final String KEY_TARGET_NOTIFIED_MASK = "target_notified_mask";
    private static final String KEY_TARGET_MISSING_POLLS = "target_missing_polls";
    private static final String KEY_TARGET_GENERATION = "target_generation";
    private static final String KEY_HISTORY_PLATE_0 = "history_plate_0";
    private static final String KEY_HISTORY_ETA_0 = "history_eta_0";
    private static final String KEY_HISTORY_MASK_0 = "history_mask_0";
    private static final String KEY_HISTORY_PLATE_1 = "history_plate_1";
    private static final String KEY_HISTORY_ETA_1 = "history_eta_1";
    private static final String KEY_HISTORY_MASK_1 = "history_mask_1";
    private static final String KEY_SWITCH_PENDING = "switch_pending";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;
    private static final long WAKE_LOCK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long POLL_INTERVAL_SECONDS = 20;
    private static final int TARGET_MISSING_POLL_LIMIT = 3;
    public static final int URGENT_THRESHOLD_MINUTES = 3;
    public static final int[] THRESHOLDS = {15, 10, 5, URGENT_THRESHOLD_MINUTES, 1};

    private final GbisArrivalClient arrivalClient = new GbisArrivalClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BusArrivalNotifier arrivalNotifier;
    private ScheduledExecutorService executorService;
    private PowerManager.WakeLock wakeLock;

    private static final class TargetState {
        String plateNo;
        Integer previousEtaMinutes;
        int missingPolls;
        int generation;
        final Set<Integer> notifiedThresholds = new HashSet<>();
    }

    private static final class EffectiveVehicle {
        final int vehicleIndex;
        final int etaMinutes;
        final int locationNo;
        final int seatCount;
        final String stationName;

        EffectiveVehicle(int vehicleIndex, int etaMinutes, int locationNo, int seatCount, String stationName) {
            this.vehicleIndex = vehicleIndex;
            this.etaMinutes = etaMinutes;
            this.locationNo = locationNo;
            this.seatCount = seatCount;
            this.stationName = stationName;
        }
    }

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
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "강제 새로고침 시작 실패", exception);
        }
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
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int nextGeneration = prefs.getInt(KEY_TARGET_GENERATION, 0) + 1;
        prefs.edit()
                .remove(KEY_TARGET_PLATE_NO)
                .remove(KEY_TARGET_PREVIOUS_ETA)
                .remove(KEY_TARGET_NOTIFIED_MASK)
                .remove(KEY_TARGET_MISSING_POLLS)
                .remove(KEY_HISTORY_PLATE_0)
                .remove(KEY_HISTORY_ETA_0)
                .remove(KEY_HISTORY_MASK_0)
                .remove(KEY_HISTORY_PLATE_1)
                .remove(KEY_HISTORY_ETA_1)
                .remove(KEY_HISTORY_MASK_1)
                .remove(KEY_SWITCH_PENDING)
                .putInt(KEY_TARGET_GENERATION, nextGeneration)
                .apply();
    }

    public static void switchTarget(Context context) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SWITCH_PENDING, true)
                .apply();
        BusArrivalNotifier.cancelThresholdNotifications(context);
        refreshNow(context);
    }

    private static void pushHistory(
            SharedPreferences prefs, String plateNo, int previousEta, int mask,
            String livePlate1, String livePlate2
    ) {
        String slot0Plate = prefs.getString(KEY_HISTORY_PLATE_0, null);
        String slot1Plate = prefs.getString(KEY_HISTORY_PLATE_1, null);
        int slot = HistoryCacheLogic.chooseSlotToPush(plateNo, slot0Plate, slot1Plate, livePlate1, livePlate2);
        if (slot == HistoryCacheLogic.DROP) {
            return;
        }
        String plateKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_PLATE_0 : KEY_HISTORY_PLATE_1;
        String etaKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_ETA_0 : KEY_HISTORY_ETA_1;
        String maskKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_MASK_0 : KEY_HISTORY_MASK_1;
        prefs.edit()
                .putString(plateKey, plateNo)
                .putInt(etaKey, previousEta)
                .putInt(maskKey, mask)
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
        executorService.scheduleWithFixedDelay(
                this::pollArrival, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void pollOnce() {
        startPolling();
        executorService.execute(this::pollArrival);
    }

    private void pollArrival() {
        renewWakeLock();
        try {
            GbisArrivalClient.Arrival arrival = arrivalClient.fetchArrival(STATION_ID, ROUTE_ID);

            if (consumeSwitchPending()) {
                applyTargetSwitch(arrival);
            }

            int selectedVehicle = getSelectedVehicle(this);
            TargetState state = loadTargetState();

            EffectiveVehicle effective = resolveEffectiveVehicle(arrival, selectedVehicle, state);

            if (effective == null) {
                state.missingPolls++;
                if (state.missingPolls < TARGET_MISSING_POLL_LIMIT) {
                    saveTargetState(state);
                    publishSuccessStatus("도착 예정 정보가 없습니다.", -1, -1, -1, "");
                    updateForegroundNotification("도착 예정 정보가 없습니다.");
                    return;
                }

                BusArrivalNotifier.cancelThresholdNotifications(this);
                finishMonitoring("추적 중인 차량 정보를 더 이상 찾을 수 없어 모니터링을 종료했습니다.");
                return;
            }

            state.missingPolls = 0;
            int etaMinutes = effective.etaMinutes;
            String status = formatStatus(arrival, effective.vehicleIndex);
            publishSuccessStatus(status, etaMinutes, effective.locationNo, effective.seatCount, effective.stationName);
            updateForegroundNotification(status);

            if (etaMinutes == 0) {
                BusArrivalNotifier.cancelThresholdNotifications(this);
                arrivalNotifier.notifyArrival(formatNotificationBody(arrival, effective.vehicleIndex));
                saveTargetState(state);
                finishMonitoring("선택한 차량이 도착해 모니터링을 종료했습니다.");
                return;
            }

            notifyCrossedThresholds(arrival, effective.vehicleIndex, state);
            state.previousEtaMinutes = etaMinutes;
            saveTargetState(state);
        } catch (IOException exception) {
            Log.e(TAG, "도착 정보 조회 실패", exception);
            publishStatus("도착 정보 조회 실패: " + exception.getMessage());
        } catch (RuntimeException exception) {
            Log.e(TAG, "도착 정보 처리 중 오류", exception);
            publishStatus("도착 정보 처리 중 오류가 발생했습니다: " + exception.getMessage());
        }
    }

    private boolean consumeSwitchPending() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean pending = prefs.getBoolean(KEY_SWITCH_PENDING, false);
        if (pending) {
            prefs.edit().remove(KEY_SWITCH_PENDING).apply();
        }
        return pending;
    }

    private void applyTargetSwitch(GbisArrivalClient.Arrival arrival) {
        BusArrivalNotifier.cancelThresholdNotifications(this);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentPlate = prefs.getString(KEY_TARGET_PLATE_NO, null);
        if (currentPlate != null && !currentPlate.isEmpty()) {
            pushHistory(prefs, currentPlate,
                    prefs.getInt(KEY_TARGET_PREVIOUS_ETA, -1),
                    prefs.getInt(KEY_TARGET_NOTIFIED_MASK, 0),
                    arrival.plateNo(VEHICLE_FIRST), arrival.plateNo(VEHICLE_SECOND));
        }
        prefs.edit()
                .remove(KEY_TARGET_PLATE_NO)
                .remove(KEY_TARGET_PREVIOUS_ETA)
                .remove(KEY_TARGET_NOTIFIED_MASK)
                .remove(KEY_TARGET_MISSING_POLLS)
                .apply();
    }

    private EffectiveVehicle resolveEffectiveVehicle(
            GbisArrivalClient.Arrival arrival,
            int selectedVehicle,
            TargetState state
    ) {
        boolean bootstrap = VehicleTargetResolver.isBootstrap(state.plateNo);
        int resolvedVehicle = VehicleTargetResolver.resolve(
                state.plateNo, selectedVehicle,
                arrival.plateNo(VEHICLE_FIRST), arrival.plateNo(VEHICLE_SECOND),
                arrival.predictTime(VEHICLE_FIRST), arrival.predictTime(VEHICLE_SECOND));
        if (resolvedVehicle == VehicleTargetResolver.NONE) {
            return null;
        }
        if (bootstrap) {
            String plateNo = arrival.plateNo(resolvedVehicle);
            if (plateNo != null && !plateNo.isEmpty()) {
                state.plateNo = plateNo;
                restoreFromHistoryIfMatching(state);
            }
        }
        return toEffectiveVehicle(arrival, resolvedVehicle);
    }

    private void restoreFromHistoryIfMatching(TargetState state) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int slot = HistoryCacheLogic.findSlot(
                state.plateNo,
                prefs.getString(KEY_HISTORY_PLATE_0, null),
                prefs.getString(KEY_HISTORY_PLATE_1, null));
        if (slot == HistoryCacheLogic.NOT_FOUND) {
            return;
        }
        String etaKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_ETA_0 : KEY_HISTORY_ETA_1;
        String maskKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_MASK_0 : KEY_HISTORY_MASK_1;
        String plateKey = slot == HistoryCacheLogic.SLOT_0 ? KEY_HISTORY_PLATE_0 : KEY_HISTORY_PLATE_1;

        int historyEta = prefs.getInt(etaKey, -1);
        state.previousEtaMinutes = historyEta >= 0 ? historyEta : null;
        int historyMask = prefs.getInt(maskKey, 0);
        state.notifiedThresholds.clear();
        for (int threshold : THRESHOLDS) {
            if ((historyMask & thresholdBit(threshold)) != 0) {
                state.notifiedThresholds.add(threshold);
            }
        }
        // Consumed — clear this slot so it doesn't keep matching once it's the active target again.
        prefs.edit()
                .remove(plateKey)
                .remove(etaKey)
                .remove(maskKey)
                .apply();
    }

    private EffectiveVehicle toEffectiveVehicle(GbisArrivalClient.Arrival arrival, int vehicleIndex) {
        return new EffectiveVehicle(
                vehicleIndex,
                arrival.predictTime(vehicleIndex),
                arrival.locationNo(vehicleIndex),
                arrival.remainSeatCount(vehicleIndex),
                arrival.stationName(vehicleIndex));
    }

    private String formatStatus(GbisArrivalClient.Arrival arrival, int effectiveVehicle) {
        StringBuilder builder = new StringBuilder();
        builder.append("알림 대상: ")
                .append(vehicleLabel(effectiveVehicle))
                .append("\n")
                .append(formatVehicleLine(arrival, VEHICLE_FIRST, effectiveVehicle))
                .append("\n\n")
                .append(formatVehicleLine(arrival, VEHICLE_SECOND, effectiveVehicle));
        return builder.toString();
    }

    private String formatVehicleLine(GbisArrivalClient.Arrival arrival, int vehicleIndex, int effectiveVehicle) {
        int etaMinutes = arrival.predictTime(vehicleIndex);
        if (etaMinutes < 0) {
            return linePrefix(vehicleIndex, effectiveVehicle) + vehicleLabel(vehicleIndex) + ": 정보 없음";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(linePrefix(vehicleIndex, effectiveVehicle))
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

    private void notifyCrossedThresholds(
            GbisArrivalClient.Arrival arrival,
            int vehicleIndex,
            TargetState state
    ) {
        int etaMinutes = arrival.predictTime(vehicleIndex);
        for (int threshold : ThresholdCrossingLogic.newlyCrossedThresholds(
                state.previousEtaMinutes, etaMinutes, state.notifiedThresholds, THRESHOLDS)) {
            state.notifiedThresholds.add(threshold);
            arrivalNotifier.notifyThreshold(threshold, formatNotificationBody(arrival, vehicleIndex));
        }
    }

    private TargetState loadTargetState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        TargetState state = new TargetState();
        state.generation = prefs.getInt(KEY_TARGET_GENERATION, 0);
        state.plateNo = prefs.getString(KEY_TARGET_PLATE_NO, null);

        int storedPreviousEta = prefs.getInt(KEY_TARGET_PREVIOUS_ETA, -1);
        state.previousEtaMinutes = storedPreviousEta >= 0 ? storedPreviousEta : null;

        state.missingPolls = prefs.getInt(KEY_TARGET_MISSING_POLLS, 0);

        int mask = prefs.getInt(KEY_TARGET_NOTIFIED_MASK, 0);
        for (int threshold : THRESHOLDS) {
            if ((mask & thresholdBit(threshold)) != 0) {
                state.notifiedThresholds.add(threshold);
            }
        }
        return state;
    }

    /**
     * Persists target state, but only if no reset/switch happened since {@link #loadTargetState()}
     * was called for this poll. Prevents an in-flight poll (blocked on network I/O while the user
     * switches vehicles) from overwriting the fresh state a switch just wrote with stale data.
     */
    private void saveTargetState(TargetState state) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getInt(KEY_TARGET_GENERATION, 0) != state.generation) {
            return;
        }
        int mask = 0;
        for (int threshold : state.notifiedThresholds) {
            mask |= thresholdBit(threshold);
        }
        prefs.edit()
                .putString(KEY_TARGET_PLATE_NO, state.plateNo)
                .putInt(KEY_TARGET_PREVIOUS_ETA, state.previousEtaMinutes != null ? state.previousEtaMinutes : -1)
                .putInt(KEY_TARGET_NOTIFIED_MASK, mask)
                .putInt(KEY_TARGET_MISSING_POLLS, state.missingPolls)
                .apply();
    }

    private int thresholdBit(int threshold) {
        return 1 << threshold;
    }

    private void finishMonitoring(String status) {
        setMonitoringActive(this, false);
        BusArrivalNotifier.cancelThresholdNotifications(this);
        resetNotificationState(this);
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
        publishStatus(status, -1, -1, -1, "", false);
    }

    private void publishSuccessStatus(String status, int etaMinutes, int locationNo, int seatCount, String stationName) {
        publishStatus(status, etaMinutes, locationNo, seatCount, stationName, true);
    }

    private void publishStatus(
            String status,
            int etaMinutes,
            int locationNo,
            int seatCount,
            String stationName,
            boolean success
    ) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_ETA_MINUTES, etaMinutes);
        intent.putExtra(EXTRA_LOCATION_NO, locationNo);
        intent.putExtra(EXTRA_SEAT_COUNT, seatCount);
        intent.putExtra(EXTRA_STATION_NAME, stationName);
        if (success) {
            intent.putExtra(EXTRA_UPDATED_AT, System.currentTimeMillis());
        }
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
