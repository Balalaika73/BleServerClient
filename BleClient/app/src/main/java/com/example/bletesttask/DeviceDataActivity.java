package com.example.bletesttask;

import static android.content.ContentValues.TAG;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeviceDataActivity extends AppCompatActivity {
    private static final int BLOCK_SIZE = 160;
    private static final long DELAY_MS = 60;

    private Button sendData;
    private EditText dataText;
    private TextView nameDev;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> receiveData;
    private BluetoothDevice blD;
    private BLE_Gatt_Service bleGattService;
    private boolean isBound = false;
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
        IntentFilter disconnectFilter = new IntentFilter("com.example.bletestask.DISCONNECTED");
        registerReceiver(disconnectReceiver, disconnectFilter);

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
                showMessage("BLE служба не включена.");
            }
        });
    }

    private void sendNextBlock(byte[] data) {
        List<byte[]> blocks = splitDataIntoBlocks(data, BLOCK_SIZE);
        CompletableFuture<Void> previousTransmission = CompletableFuture.completedFuture(null);

        for (byte[] block : blocks) {
            previousTransmission = previousTransmission.thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                try {
                    return bleGattService.startDataTransmission(block);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error in transmission", e);
                    return false;
                }
            }).thenAccept(success -> {
                if (success) {
                    Log.i(TAG, "Block sent successfully.");
                } else {
                    Log.w(TAG, "Failed to send block.");
                }
            }).thenRun(() -> {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Block transmission delay interrupted.", e);
                }
            }));
        }

        previousTransmission.join();
    }

    private List<byte[]> splitDataIntoBlocks(byte[] data, int blockSize) {
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

    private int isLastBytePartOfSymbol(byte[] bytes) {
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
        if (device != null && isBound && bleGattService != null) {
            try {
                bleGattService.connectToDevice(device);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                String deviceName = device.getName();
                if (!TextUtils.isEmpty(deviceName)) {
                    showMessage(deviceName + " подключено");
                    nameDev.setText(deviceName);
                } else {
                    showMessage(device.getAddress() + " подключено");
                    nameDev.setText(device.getAddress());
                }
            } catch (Exception e) {
                showMessage("Ошибка подключения: " + e.getMessage());
            }
        } else {
            Log.i(TAG, "Нет устройства");
        }
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

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.bletesttask.DISCONNECTED".equals(intent.getAction())) {
                Intent mainIntent = new Intent(DeviceDataActivity.this, MainActivity.class);
                startActivity(mainIntent);

                showMessage("Отключение от GATT сервера.");
            }
        }
    };

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dataReceiver);
        unregisterReceiver(disconnectReceiver);
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
