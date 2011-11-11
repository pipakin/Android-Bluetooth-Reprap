package com.hermit.btreprap.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BluetoothConnectThread extends Thread {
	private static final String TAG = "BTRR";
	private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private final BluetoothAdapter mAdapter;
    private final RepRapConnectionService mService;

    public BluetoothConnectThread(BluetoothDevice device, BluetoothAdapter adapter, RepRapConnectionService service) {
        mmDevice = device;
        mAdapter = adapter;
        mService = service;
        BluetoothSocket tmp = null;

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
        	//Method m = device.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
        	//tmp = (BluetoothSocket) m.invoke(device, 1);

            tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (Exception e) {
            Log.e(TAG, "create() failed", e);
        }
        mmSocket = tmp;
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectThread");
        setName("ConnectThread");

        // Always cancel discovery because it will slow down a connection
        mAdapter.cancelDiscovery();

        // Make a connection to the BluetoothSocket
        try {
            // This is a blocking call and will only return on a
            // successful connection or an exception
            mmSocket.connect();
        } catch (IOException e) {
            mService.connectionFailed();
            Log.e(TAG, "connection failure", e);
            // Close the socket
            try {
                mmSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "unable to close() socket during connection failure", e2);
            }
            return;
        }

        // Start the connected thread
        mService.connected(mmSocket);
    }

    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }
}
