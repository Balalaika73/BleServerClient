package com.example.bletesttask;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BLE_Gatt_Service extends Service {
    private BluetoothGatt bluetoothGatt;
    private static final String TAG = "GATService";
    private BluetoothGattCharacteristic characteristic;
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("72563044-DB33-4692-A45D-C5212EEBABFA");
    private static final long DELAY_MS = 60;

    @Override
    public IBinder onBind(Intent intent) {
        return new BTLeServiceBinder();
    }

    public class BTLeServiceBinder extends Binder {
        BLE_Gatt_Service getService() {
            return BLE_Gatt_Service.this;
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (device != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (bluetoothGatt == null) return null;

        BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
        return service != null ? service.getCharacteristic(characteristicUUID) : null;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStateChange: newState=" + newState + ", status=" + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                if (ActivityCompat.checkSelfPermission(BLE_Gatt_Service.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                sendDataToActivity("Disconnected from GATT server.");
                sendBroadcast(new Intent("com.example.bletestask.DISCONNECTED"));
            } else {
                Log.w(TAG, "Connection state changed: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.i(TAG, "Service UUID: " + service.getUuid().toString());

                    for (BluetoothGattCharacteristic charac : service.getCharacteristics()) {
                        Log.i(TAG, "Characteristic UUID: " + charac.getUuid().toString());

                        if (charac.getUuid().equals(CHARACTERISTIC_UUID)) {
                            characteristic = charac;
                        }

                        if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            if (ActivityCompat.checkSelfPermission(BLE_Gatt_Service.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                gatt.setCharacteristicNotification(charac, true);
                                BluetoothGattDescriptor descriptor = charac.getDescriptor(
                                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                if (descriptor != null) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                }
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic read: " + characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic write successful");
            } else {
                Log.w(TAG, "Characteristic write failed: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                String dataString = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                Log.i(TAG, "Received data: " + dataString);
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Data retrieval error.", e);
                    }
                }).join();
                sendDataToActivity(dataString);
            }
        }
    };

    public boolean startDataTransmission(byte[] data) throws InterruptedException {
        Log.i(TAG, "Starting data transmission.");
        if (characteristic != null && bluetoothGatt != null) {
            characteristic.setValue(data);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Data transmission error.", e);
                    }
                }).join();
                if (success) {
                    Log.i(TAG, "Data write request sent.");
                    return true;
                } else {
                    Log.w(TAG, "Data write request failed.");
                }
            } else {
                Log.w(TAG, "Bluetooth connect permission not granted.");
            }
        } else {
            Log.e(TAG, "BluetoothGatt or characteristic not initialized.");
        }
        return false;
    }

    private void sendDataToActivity(String data) {
        Intent intent = new Intent("com.example.bletestask.DATA");
        intent.putExtra("data", data);
        sendBroadcast(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed.");
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.close();
                bluetoothGatt = null;
                Log.i(TAG, "BluetoothGatt connection closed.");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
