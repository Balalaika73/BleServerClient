package com.example.bletesttask;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class BLEDeviceAdapter extends ArrayAdapter<BLE_Device> {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private int resourceView;
    private ArrayList<BLE_Device> devices = new ArrayList<>();
    Activity activity;
    public BLEDeviceAdapter(Activity activity, int resource, ArrayList<BLE_Device> mdevices) {
        super(activity.getApplicationContext(), resource, mdevices);
        this.activity = activity;
        resourceView = resource;
        devices = mdevices;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) activity.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(resourceView, parent, false);
        }

        BLE_Device deviceItem = getItem(position);
        int rssi = deviceItem.getRSSI();

        TextView nameView = convertView.findViewById(R.id.nameDev);
        TextView rssiView = convertView.findViewById(R.id.rssi);
        TextView addressView = convertView.findViewById(R.id.macDev);

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) getContext(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
            return convertView;
        }
        String deviceName = deviceItem.getName();
        if (deviceName != null && !deviceName.isEmpty()) {
            nameView.setText(deviceItem.getName());
        } else {
            nameView.setText("Null");
        }
        addressView.setText(deviceItem.getAddress());
        rssiView.setText(Integer.toString(rssi));

        return convertView;
    }
}
