package com.example.bletesttask;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int BLUETOOTH_REQ_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView blStatus;
    private Button startSearch;
    private ImageButton turnOnB, turnOffB;
    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private BLEDeviceAdapter deviceAdapter;
    private ArrayList<BLE_Device> deviceList = new ArrayList<>();
    private LocationManager locationManager;
    private Set<String> deviceAddresses = new HashSet<>();
    private BLE_Gatt_Service bleGattService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BLE_Gatt_Service.BTLeServiceBinder binder = (BLE_Gatt_Service.BTLeServiceBinder) service;
            bleGattService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            Log.i(TAG, "Service disconnected");
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blStatus = findViewById(R.id.blStatus);
        startSearch = findViewById(R.id.runSearch);
        turnOnB = findViewById(R.id.blTurnOn);
        turnOffB = findViewById(R.id.blTurnOf);
        listView = findViewById(R.id.list_dev);

        deviceAdapter = new BLEDeviceAdapter(this, R.layout.device_item, deviceList);
        listView.setAdapter(deviceAdapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Log.i(TAG, "Нажата кнопка подключенния");
            BLE_Device selectedDevice = deviceList.get(position);
            deviceList.clear(); // Очистить список перед новым поиском
            deviceAdapter.notifyDataSetChanged();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.stopLeScan(leScanCallback);
            //startConnection(selectedDevice.getBluetoothDevice());
            Intent intent = new Intent(MainActivity.this, DeviceDataActivity.class);
            intent.putExtra("device", selectedDevice.getBluetoothDevice());
            startActivity(intent);
        });

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        checkPermissions();

        if (bluetoothAdapter == null) {
            blStatus.setText("Ошибка службы Bluetooth");
        } else {
            blStatus.setText("Служба Bluetooth работает корректно");
            updateUI();
        }

        turnOnB.setOnClickListener(v -> {
            Log.i("TAG", "Кнопка нажата");
            enableBluetooth();
            enableLocation();
        });

        turnOffB.setOnClickListener(v -> disableBluetooth());
        startSearch.setOnClickListener(v -> startBluetoothDiscovery());

        Intent intent = new Intent(this, BLE_Gatt_Service.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        }
    }

    private void updateUI() {
        if (bluetoothAdapter.isEnabled()) {
            blStatus.setText("Bluetooth включен");
        } else {
            blStatus.setText("Bluetooth выключен");
        }
    }

    private void startBluetoothDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST_CODE);
            return;
        }
        deviceList.clear(); // Очистить список перед новым поиском
        deviceAdapter.notifyDataSetChanged();
        bluetoothAdapter.startLeScan(leScanCallback);
        showMessage("Начат поиск устройств");
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(() -> {
                if (!deviceList.contains(device)) {
                    if (!deviceAddresses.contains(device.getAddress())) {
                        deviceAddresses.add(device.getAddress());
                        BLE_Device ble_device = new BLE_Device(MainActivity.this, device);
                        deviceList.add(ble_device);
                        deviceAdapter.notifyDataSetChanged();
                        Log.i("TAG", device.getAddress());

                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
                            return;
                        }
                        String deviceName = device.getName();
                        if (deviceName != null && !deviceName.isEmpty()) {
                            Log.i("TAG", deviceName);
                        } else {
                            Log.i("TAG", "Device name is not available");
                        }
                    }
                }
            });
        }
    };


    private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
            } else {
                Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnOn, BLUETOOTH_REQ_CODE);
            }
        } else {
            Toast.makeText(this, "Служба Bluetooth уже включена", Toast.LENGTH_SHORT).show();
        }
    }

    public void enableLocation() {      //проверка состояния местоположения
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Служба местоположения уже включена", Toast.LENGTH_SHORT).show();
        }
    }

    private void disableBluetooth() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.disable();
            blStatus.setText("Bluetooth выключен");
            showMessage("Bluetooth выключен");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_REQ_CODE) {
            if (resultCode == RESULT_OK) {
                showMessage("Bluetooth включен");
                updateUI();
            } else {
                showMessage("Не смогли включить Bluetooth");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth();
            } else {
                Toast.makeText(this, "Разрешение на подключение Bluetooth не предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //состояния приложения
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BLE_Gatt_Service.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}