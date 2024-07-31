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
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BLE_Gatt_Service extends Service {
    private BluetoothGatt bluetoothGatt;
    private static final String TAG = "GATService";
    private BluetoothGattCharacteristic characteristic;
    private Context context;
    UUID CHARACTERISTIC_UUID = UUID.fromString("72563044-DB33-4692-A45D-C5212EEBABFA");
    private static final int BLOCK_SIZE = 160;
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
        if (device != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (bluetoothGatt == null) return null;

        BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
        if (service != null) {
            return service.getCharacteristic(characteristicUUID);
        }
        return null;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Соеденено с GATT server.");
                if (ActivityCompat.checkSelfPermission(BLE_Gatt_Service.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Attempting to start service discovery: " + bluetoothGatt.discoverServices());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Отсоеденино от GATT server.");
                sendDataToActivity("Отсоединение от GATT сервера.");
            } else {
                Log.w(TAG, "Статус соединения изменился: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.i("BluetoothGattCallback", "Service UUID: " + service.getUuid().toString());

                    // Перебор всех характеристик для текущей службы
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic charac  : characteristics) {
                        Log.i("BluetoothGattCallback", "Characteristic UUID: " + charac.getUuid().toString());
                        if (charac.getUuid().equals(UUID.fromString(CHARACTERISTIC_UUID.toString()))) {
                            characteristic = charac;
                        }
                        // Проверка, поддерживает ли характеристика уведомления
                        int properties = charac.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            // Подписка на уведомления для этой характеристики
                            if (ActivityCompat.checkSelfPermission(BLE_Gatt_Service.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                            gatt.setCharacteristicNotification(charac, true);

                            // Найти дескриптор, который управляет уведомлениями
                            BluetoothGattDescriptor descriptor = charac.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // Client Characteristic Configuration descriptor UUID
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
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
            if ((characteristic.getProperties()
                    & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                return;
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
                byte[] data = characteristic.getValue();
                String dataString = new String(data, StandardCharsets.UTF_8);
                Log.i(TAG, "Received data: " + dataString);
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Ошибка получения данных.", e);
                    }
                }).join();
                sendDataToActivity(dataString);
            }
        }

    };

    public boolean startDataTransmission(byte[] data) throws InterruptedException {
        Log.i(TAG, "Начата передача данных.");
        if (characteristic != null) {
            if(bluetoothGatt != null) {
                Log.i(TAG, "Все ок.");

                characteristic.setValue(data);

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Bluetooth connect permission not granted.");
                    return false;
                }

                boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Ошибка передачи данных.", e);
                    }
                }).join();
                if (success) {
                    Log.i(TAG, "Data write request sent.");
                    return true;
                } else {
                    Log.w(TAG, "Data write request failed.");
                    return false;
                }
            }
            else {
                Log.e(TAG, "BluetoothGatt not initialized.");
                return false;
            }
        } else {
            Log.e(TAG, " characteristic not initialized.");
            return false;
        }
    }

    private void sendDataToActivity(String data) {
        Intent intent = new Intent("com.example.bletesttask.DATA");
        intent.putExtra("data", data);
        sendBroadcast(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }
}