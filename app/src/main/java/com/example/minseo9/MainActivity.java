package com.example.minseo9;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private RadioGroup vehicleRadioGroup;
    private final GbisArrivalClient arrivalClient = new GbisArrivalClient();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startMonitoring();
                } else {
                    Toast.makeText(this, R.string.notification_permission_needed, Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(BusMonitorService.EXTRA_STATUS);
            if (status != null) {
                statusText.setText(status);
            }
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

        statusText = findViewById(R.id.statusText);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
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
                BusMonitorService.resetNotificationState(this);
                BusMonitorService.refreshNow(this);
            } else {
                refreshArrivalPreview();
            }
        });
        findViewById(R.id.startButton).setOnClickListener(view -> requestNotificationPermissionOrStart());
        findViewById(R.id.stopButton).setOnClickListener(view -> stopMonitoring());
        if (BusMonitorService.isMonitoringActive(this)) {
            BusMonitorService.refreshNow(this);
        } else {
            refreshArrivalPreview();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BusMonitorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(statusReceiver);
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
            startMonitoring();
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void startMonitoring() {
        BusMonitorService.resetNotificationState(this);
        BusMonitorService.start(this);
    }

    private void stopMonitoring() {
        BusMonitorService.stop(this);
        statusText.setText(R.string.monitor_stopped);
    }

    private void refreshArrivalPreview() {
        statusText.setText(R.string.monitor_loading);
        executorService.execute(() -> {
            try {
                GbisArrivalClient.Arrival arrival = arrivalClient.fetchArrival(
                        BusMonitorService.STATION_ID,
                        BusMonitorService.ROUTE_ID);
                String status = formatStatus(arrival);
                runOnUiThread(() -> statusText.setText(status));
            } catch (IOException exception) {
                runOnUiThread(() -> statusText.setText("도착 정보 조회 실패: " + exception.getMessage()));
            }
        });
    }

    private String formatStatus(GbisArrivalClient.Arrival arrival) {
        int selectedVehicle = BusMonitorService.getSelectedVehicle(this);
        return "알림 대상: " + vehicleLabel(selectedVehicle)
                + "\n" + formatVehicleLine(arrival, BusMonitorService.VEHICLE_FIRST, selectedVehicle)
                + "\n\n" + formatVehicleLine(arrival, BusMonitorService.VEHICLE_SECOND, selectedVehicle);
    }

    private String formatVehicleLine(
            GbisArrivalClient.Arrival arrival,
            int vehicleIndex,
            int selectedVehicle
    ) {
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

    private String linePrefix(int vehicleIndex, int selectedVehicle) {
        return vehicleIndex == selectedVehicle ? "▶ " : "   ";
    }

    private String vehicleLabel(int vehicleIndex) {
        return vehicleIndex == BusMonitorService.VEHICLE_SECOND ? "다음 차량" : "이번 차량";
    }
}
