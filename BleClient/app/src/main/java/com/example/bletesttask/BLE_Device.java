package com.example.bletesttask;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

public class BLE_Device {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private BluetoothDevice bluetoothDevice;
    private int rssi;
    private Context context;
    private Activity activity;

    public BLE_Device(Context context, BluetoothDevice bluetoothDevice) {
        this.context = context;
        this.bluetoothDevice = bluetoothDevice;
        if (context instanceof Activity) {
            this.activity = (Activity) context;
        }
    }

    public String getAddress() {
        return bluetoothDevice.getAddress();
    }


    public String getName() {
        if (activity != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Если разрешение не предоставлено, запросить его
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
                return null;
            }
        }
        return bluetoothDevice.getName();
    }

    public void setBluetoothDevice(BluetoothDevice device) {
        this.bluetoothDevice = device;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void setRSSI(int rssi) {
        this.rssi = rssi;
    }

    public int getRSSI() {
        return rssi;
    }
}
