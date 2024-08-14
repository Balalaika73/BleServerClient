package com.example.bletesttask;

import static android.content.ContentValues.TAG;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DeviceDataActivity extends AppCompatActivity {
    Button sendData;
    private BluetoothDevice blD;
    private BLE_Gatt_Service bleGattService;
    private boolean isBound = false;
    private BluetoothAdapter bluetoothAdapter;
    EditText dataText;
    TextView nameDev;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> receiveData;
    private static final int BLOCK_SIZE = 160;
    private static final long DELAY_MS = 60;
    private int totalBlocks;
    private int currentBlockIndex = 0;
    private Handler handler = new Handler();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BLE_Gatt_Service.BTLeServiceBinder binder = (BLE_Gatt_Service.BTLeServiceBinder) service;
            bleGattService = binder.getService();
            isBound = true;
            Log.i(TAG, "BLE_Gatt_Service connected");
            startConnection(blD);
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
        setContentView(R.layout.activity_device_data);
        sendData = findViewById(R.id.sendData);
        dataText = findViewById(R.id.dataText);
        nameDev = findViewById(R.id.nameDev);
        listView = findViewById(R.id.getData);
        receiveData = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, receiveData);
        listView.setAdapter(adapter);

        IntentFilter filter = new IntentFilter("com.example.bletesttask.DATA");
        registerReceiver(dataReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Intent intent = new Intent(this, BLE_Gatt_Service.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        blD = getIntent().getParcelableExtra("device");

        sendData.setOnClickListener(v -> {
            if (bleGattService != null) {
                String str = dataText.getText().toString().trim();
                if (str.isEmpty()) {
                    showMessage("Введите данные для отправки.");
                    return;
                }
                byte[] data = str.getBytes(StandardCharsets.UTF_8);
                sendNextBlock(data);
            } else {
                showMessage("BLE Service is not bound yet.");
            }
        });

    }

    public void sendNextBlock(byte[] data) {
        List<byte[]> blocks = splitDataIntoBlocks(data, BLOCK_SIZE);
        CompletableFuture<Void> previousTransmission = CompletableFuture.completedFuture(null);

        for (byte[] block : blocks) {
            System.out.println("Последний байт блока example3 имеет позицию: " + isLastBytePartOfSymbol(block));
            previousTransmission = previousTransmission.thenCompose(v -> {
                return CompletableFuture.supplyAsync(() -> {
                            try {
                                return bleGattService.startDataTransmission(block);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return null;
                        })
                        .thenAccept(success -> {
                            if (success) {
                                Log.i(TAG, "Block sent successfully.");
                            } else {
                                Log.w(TAG, "Failed to send block.");
                            }
                        })
                        .thenRun(() -> {
                            try {
                                Thread.sleep(DELAY_MS); // Задержка в миллисекундах
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Block transmission delay interrupted.", e);
                            }
                        });
            });
        }
        previousTransmission.join();
    }

    public static List<byte[]> splitDataIntoBlocks(byte[] data, int blockSize) {
        List<byte[]> blocks = new ArrayList<>();
        int totalLength = data.length;
        int offset = 0;

        while (offset < totalLength) {
            int length = Math.min(blockSize, totalLength - offset);

            byte[] tempBlock = new byte[length];
            System.arraycopy(data, offset, tempBlock, 0, length);
            int a = isLastBytePartOfSymbol(tempBlock);

            if (a > 0 && length > a) {
                length -= a;
            }

            byte[] block = new byte[length];
            System.arraycopy(data, offset, block, 0, length);
            blocks.add(block);

            offset += length;
        }

        return blocks;
    }


    public static int isLastBytePartOfSymbol(byte[] bytes) {
        if (bytes.length == 0) {
            return 0;
        }

        byte lastByte = bytes[bytes.length - 1];

        if ((lastByte & 0x80) == 0) {
            return 0;
        } else if ((lastByte & 0xC0) == 0x80) {
            for (int i = bytes.length - 2; i >= 0; i--) {
                byte b = bytes[i];
                if ((b & 0x80) == 0) {
                    return 0;
                } else if ((b & 0xC0) == 0x80) {
                    continue;
                } else if ((b & 0xE0) == 0xC0) {
                    return 2;
                } else if ((b & 0xF0) == 0xE0) {
                    return 3;
                } else if ((b & 0xF8) == 0xF0) {
                    return 4;
                }
            }
            return 0;
        } else {
            return 1;
        }
    }


    private void startConnection(BluetoothDevice device) {
        if (device != null) {
            if (isBound && bleGattService != null) {
                try {
                    bleGattService.connectToDevice(device);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    String deviceName = device.getName();
                    if(!TextUtils.isEmpty(deviceName)) {
                        showMessage(deviceName + " подключено");
                        nameDev.setText(deviceName);
                    } else {
                        showMessage(device.getAddress() + " подключено");
                        nameDev.setText(device.getAddress());
                    }
                } catch (Exception e) {
                    showMessage("Ошибка подключения" + e.getMessage());
                }
            }
        }
        else
            Log.i(TAG, "Нет устройства");
    }

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra("data");
            if (data != null) {
                receiveData.add(data);
                adapter.notifyDataSetChanged();
            }
        }
    };

    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dataReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}