package com.example.minseo9;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int HERO_NUMBER_FADE_MS = 120;
    private static final int PULSE_CYCLE_MS = 800;
    private static final float PULSE_MIN_ALPHA = 0.3f;
    private static final float DISABLED_BUTTON_ALPHA = 0.5f;

    private View pulseDot;
    private TextView monitoringLabel;
    private TextView idleText;
    private View heroNumberRow;
    private TextView heroNumberText;
    private TextView heroUnitText;
    private TextView heroCaptionText;
    private TextView stationNameText;
    private RadioGroup vehicleRadioGroup;
    private Button startButton;
    private Button stopButton;
    private AlertDialog batteryOptimizationDialog;
    private ObjectAnimator pulseAnimator;
    private Integer lastDisplayedEta;
    private final GbisArrivalClient arrivalClient = new GbisArrivalClient();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkBatteryOptimizationThenStart();
                } else {
                    Toast.makeText(this, R.string.notification_permission_needed, Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(BusMonitorService.EXTRA_STATUS);
            int etaMinutes = intent.getIntExtra(BusMonitorService.EXTRA_ETA_MINUTES, -1);
            int locationNo = intent.getIntExtra(BusMonitorService.EXTRA_LOCATION_NO, -1);
            int seatCount = intent.getIntExtra(BusMonitorService.EXTRA_SEAT_COUNT, -1);
            String stationName = intent.getStringExtra(BusMonitorService.EXTRA_STATION_NAME);
            renderEta(etaMinutes, locationNo, seatCount, stationName, status);
            updateMonitoringUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pulseDot = findViewById(R.id.pulseDot);
        monitoringLabel = findViewById(R.id.monitoringLabel);
        idleText = findViewById(R.id.idleText);
        heroNumberRow = findViewById(R.id.heroNumberRow);
        heroNumberText = findViewById(R.id.heroNumberText);
        heroUnitText = findViewById(R.id.heroUnitText);
        heroCaptionText = findViewById(R.id.heroCaptionText);
        stationNameText = findViewById(R.id.stationNameText);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        int selectedVehicle = BusMonitorService.getSelectedVehicle(this);
        vehicleRadioGroup.check(selectedVehicle == BusMonitorService.VEHICLE_SECOND
                ? R.id.secondVehicleRadio
                : R.id.firstVehicleRadio);
        vehicleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int vehicleIndex = checkedId == R.id.secondVehicleRadio
                    ? BusMonitorService.VEHICLE_SECOND
                    : BusMonitorService.VEHICLE_FIRST;
            BusMonitorService.setSelectedVehicle(this, vehicleIndex);
            if (BusMonitorService.isMonitoringActive(this)) {
                BusMonitorService.refreshNow(this);
            } else {
                refreshArrivalPreview();
            }
        });
        startButton.setOnClickListener(view -> requestNotificationPermissionOrStart());
        stopButton.setOnClickListener(view -> stopMonitoring());
        if (BusMonitorService.isMonitoringActive(this)) {
            BusMonitorService.refreshNow(this);
        } else {
            refreshArrivalPreview();
        }
        updateMonitoringUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BusMonitorService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        updateMonitoringUi();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
        if (batteryOptimizationDialog != null) {
            batteryOptimizationDialog.dismiss();
            batteryOptimizationDialog = null;
        }
        stopPulseAnimation();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

    private void requestNotificationPermissionOrStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            checkBatteryOptimizationThenStart();
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void checkBatteryOptimizationThenStart() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoringOptimizations = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        if (ignoringOptimizations) {
            validateAndStartMonitoring();
            return;
        }

        batteryOptimizationDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.battery_optimization_title)
                .setMessage(R.string.battery_optimization_message)
                .setPositiveButton(R.string.battery_optimization_open_settings, (dialog, which) -> {
                    requestIgnoreBatteryOptimizations();
                    validateAndStartMonitoring();
                })
                .setNegativeButton(R.string.battery_optimization_skip, (dialog, which) -> validateAndStartMonitoring())
                .show();
    }

    private void requestIgnoreBatteryOptimizations() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, R.string.battery_optimization_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void validateAndStartMonitoring() {
        startButton.setEnabled(false);
        startButton.setAlpha(DISABLED_BUTTON_ALPHA);
        showIdle(getString(R.string.monitor_loading));
        executorService.execute(() -> {
            try {
                SelectedArrival selectedArrival = fetchSelectedVehicleArrival();
                int etaMinutes = selectedArrival.arrival.predictTime(selectedArrival.selectedVehicle);
                if (etaMinutes >= 0) {
                    runOnUiThread(this::startMonitoring);
                } else {
                    runOnUiThread(() -> failToStart(getString(R.string.monitor_start_failed_no_info)));
                }
            } catch (IOException exception) {
                Log.e(TAG, "모니터링 시작 전 도착 정보 조회 실패", exception);
                runOnUiThread(() -> failToStart(
                        getString(R.string.monitor_fetch_failed, exception.getMessage())));
            }
        });
    }

    private void failToStart(String message) {
        startButton.setEnabled(true);
        startButton.setAlpha(1f);
        showIdle(message);
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.monitor_start_failed_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void startMonitoring() {
        BusMonitorService.resetNotificationState(this);
        if (!BusMonitorService.start(this)) {
            failToStart(getString(R.string.monitor_start_failed_background));
            return;
        }
        showIdle(getString(R.string.monitor_loading));
        updateMonitoringUi();
        Toast.makeText(this, R.string.monitor_started, Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        BusMonitorService.stop(this);
        showIdle(getString(R.string.monitor_stopped));
        updateMonitoringUi();
    }

    private void refreshArrivalPreview() {
        showIdle(getString(R.string.monitor_loading));
        executorService.execute(() -> {
            try {
                SelectedArrival selectedArrival = fetchSelectedVehicleArrival();
                int etaMinutes = selectedArrival.arrival.predictTime(selectedArrival.selectedVehicle);
                int locationNo = selectedArrival.arrival.locationNo(selectedArrival.selectedVehicle);
                int seatCount = selectedArrival.arrival.remainSeatCount(selectedArrival.selectedVehicle);
                String stationName = selectedArrival.arrival.stationName(selectedArrival.selectedVehicle);
                runOnUiThread(() -> renderEta(etaMinutes, locationNo, seatCount, stationName,
                        getString(R.string.monitor_idle)));
            } catch (IOException exception) {
                Log.e(TAG, "도착 정보 미리보기 조회 실패", exception);
                runOnUiThread(() -> showIdle(
                        getString(R.string.monitor_fetch_failed, exception.getMessage())));
            }
        });
    }

    private SelectedArrival fetchSelectedVehicleArrival() throws IOException {
        GbisArrivalClient.Arrival arrival = arrivalClient.fetchArrival(
                BusMonitorService.STATION_ID,
                BusMonitorService.ROUTE_ID);
        int selectedVehicle = BusMonitorService.getSelectedVehicle(this);
        return new SelectedArrival(arrival, selectedVehicle);
    }

    private static final class SelectedArrival {
        final GbisArrivalClient.Arrival arrival;
        final int selectedVehicle;

        SelectedArrival(GbisArrivalClient.Arrival arrival, int selectedVehicle) {
            this.arrival = arrival;
            this.selectedVehicle = selectedVehicle;
        }
    }

    private void renderEta(int etaMinutes, int locationNo, int seatCount, String stationName, String fallbackMessage) {
        if (etaMinutes < 0) {
            showIdle(fallbackMessage);
            return;
        }

        idleText.setVisibility(View.GONE);
        heroNumberRow.setVisibility(View.VISIBLE);
        if (lastDisplayedEta == null || lastDisplayedEta != etaMinutes) {
            animateHeroNumberChange(String.valueOf(etaMinutes));
            lastDisplayedEta = etaMinutes;
        }

        int colorRes = EtaPresenter.isUrgent(etaMinutes) ? R.color.warn : R.color.accent;
        int color = ContextCompat.getColor(this, colorRes);
        heroNumberText.setTextColor(color);
        heroUnitText.setTextColor(color);

        String caption = buildCaption(locationNo, seatCount);
        if (caption.isEmpty()) {
            heroCaptionText.setVisibility(View.GONE);
        } else {
            heroCaptionText.setVisibility(View.VISIBLE);
            heroCaptionText.setText(caption);
        }

        String stationLine = buildStationLine(stationName);
        if (stationLine.isEmpty()) {
            stationNameText.setVisibility(View.GONE);
        } else {
            stationNameText.setVisibility(View.VISIBLE);
            stationNameText.setText(stationLine);
        }
    }

    private void showIdle(String message) {
        heroNumberText.animate().cancel();
        heroNumberRow.setVisibility(View.GONE);
        heroCaptionText.setVisibility(View.GONE);
        stationNameText.setVisibility(View.GONE);
        idleText.setVisibility(View.VISIBLE);
        idleText.setText(message);
        lastDisplayedEta = null;
        heroNumberText.setAlpha(1f);
    }

    private void animateHeroNumberChange(String text) {
        heroNumberText.animate().cancel();
        heroNumberText.animate()
                .alpha(0f)
                .setDuration(HERO_NUMBER_FADE_MS)
                .withEndAction(() -> {
                    heroNumberText.setText(text);
                    heroNumberText.animate().alpha(1f).setDuration(HERO_NUMBER_FADE_MS).start();
                })
                .start();
    }

    private String buildCaption(int locationNo, int seatCount) {
        return EtaPresenter.buildCaption(locationNo, seatCount,
                getString(R.string.monitor_caption_full),
                getString(R.string.monitor_caption_location_only),
                getString(R.string.monitor_caption_seats_only));
    }

    private String buildStationLine(String stationName) {
        return EtaPresenter.buildStationLine(stationName, getString(R.string.monitor_station_line));
    }

    private void updateMonitoringUi() {
        boolean monitoring = BusMonitorService.isMonitoringActive(this);
        startButton.setEnabled(true);
        startButton.setAlpha(1f);
        startButton.setVisibility(monitoring ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(monitoring ? View.VISIBLE : View.GONE);
        monitoringLabel.setVisibility(monitoring ? View.VISIBLE : View.GONE);
        if (monitoring) {
            startPulseAnimation();
        } else {
            stopPulseAnimation();
        }
    }

    private void startPulseAnimation() {
        pulseDot.setVisibility(View.VISIBLE);
        if (pulseAnimator != null) {
            return;
        }
        pulseAnimator = ObjectAnimator.ofFloat(pulseDot, View.ALPHA, 1f, PULSE_MIN_ALPHA);
        pulseAnimator.setDuration(PULSE_CYCLE_MS);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        pulseDot.setAlpha(1f);
        pulseDot.setVisibility(View.GONE);
    }
}
