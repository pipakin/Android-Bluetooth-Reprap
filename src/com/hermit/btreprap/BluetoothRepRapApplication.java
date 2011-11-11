package com.hermit.btreprap;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

public class BluetoothRepRapApplication extends Application {
	
	private BluetoothDevice mDevice;
	
	public BluetoothDevice getDevice()
	{
		return mDevice;
	}
	
	public void setDevice(BluetoothDevice dev)
	{
		mDevice = dev;
	}
	
	private static BluetoothRepRapApplication singleton;
	
	public static BluetoothRepRapApplication getInstance()
	{
		return singleton;
	}
	 
	@Override
	public void onCreate() {
		super.onCreate();
		singleton = this;
	}
	
}
