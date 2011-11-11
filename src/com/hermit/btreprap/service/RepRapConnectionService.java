package com.hermit.btreprap.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.widget.Toast;

public class RepRapConnectionService extends Service {

	public static final int STATE_DISCONNECTED = 0;
	public static final int STATE_CONNECTING = 1;
	public static final int STATE_CONNECTED = 2;

	// Messages from the client
	public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_GET_DEVICES = 3;
    public static final int MSG_CONNECT_DEVICE = 4;
    public static final int MSG_COMMAND = 5;
    public static final int MSG_GET_STATUS = 6;
    public static final int MSG_GET_FILE_LIST = 7;
    public static final int MSG_SEND_FILE = 8;
    
    // Response messages
	public static final int MSG_STATE_CHANGED = 10;
	public static final int MSG_DEVICE_LIST = 11;
	public static final int MSG_CONNECTED = 12;
	public static final int MSG_CONNECTION_FAILED = 13;
	public static final int MSG_COMMAND_RESPONSE = 14;
	public static final int MSG_STATUS = 15;
	public static final int MSG_FILE_LIST = 16;
	public static final int MSG_SEND_PROGRESS = 17;
	
	// Unique UUID for this application TODO: Change this
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    
    private BluetoothDevice mDevice;
	private BluetoothAdapter mBtAdapter;
	private BluetoothSocket mSocket;
	
	private InputStream mInStream;
    private OutputStream mOutStream;
    
	private int mState = STATE_DISCONNECTED;
	
	private ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	private BluetoothConnectThread mConnectThread;
	private RepRapCommandThread mCommandThread;
	private Bundle mStatus = new Bundle();
	private WakeLock mWakeLock;
	
	//static string
	private static final String CMD_CAPABILITIES = "M115";
	private static final String REGEX_FIRMWARE_NAME = "FIRMWARE_NAME\\:(\\S+)";
	private static final String REGEX_FIRMWARE_URL = "FIRMWARE_URL\\:(\\S+)";
	private static final String REGEX_PROTOCOL_VERSION = "PROTOCOL_VERSION\\:(\\S+)";
	private static final String REGEX_MACHINE_TYPE = "MACHINE_TYPE\\:(\\S+)";
	private static final String REGEX_EXTRUDER_COUNT = "EXTRUDER_COUNT\\:(\\S+)";
	
	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

    @Override
	public void onCreate() {
		super.onCreate();
		// Get the local Bluetooth adapter
    	mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    	final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "My Tag");
        mWakeLock.acquire();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		this.Disconnect();
		mWakeLock.release();
	}
	
	private synchronized void Disconnect()
	{
		//cancel threads
		if(mConnectThread != null){mConnectThread.cancel(); mConnectThread = null; }
		if(mCommandThread != null){mCommandThread.cancel(); mCommandThread = null; }
		
		//disconnect from bluetooth device
		if(mSocket != null){ try {
			mSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} mSocket = null; }
		
		if(mDevice != null)
		{
			mDevice = null;
		}
		
		//TODO: close bluetooth connection if we were the ones who opened it
		
		setState(STATE_DISCONNECTED);
	}
	
	public synchronized Set<BluetoothDevice> GetDevices()
	{
		// Get a set of currently paired devices
        return mBtAdapter.getBondedDevices();
	}
	
	public synchronized Boolean SendCommand(String command)
	{
		if(mState == STATE_CONNECTED)
		{
			//write to the connected stream
			return true;
		}
		
		return false;
	}
	
	private synchronized void setState(int state)
	{
		mState = state;
		
		for (int i=mClients.size()-1; i>=0; i--) {
            try {
        		Message msg = Message.obtain(null, MSG_STATE_CHANGED);
        		msg.arg1 = state;

        		mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
	}
	
	private void sendMessage(int what)
	{
		for (int i=mClients.size()-1; i>=0; i--) {
            try {
        		Message msg = Message.obtain(null, what);
        		mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
	}
	
	private void sendObject(int what, Object o)
	{
		for (int i=mClients.size()-1; i>=0; i--) {
            try {
        		Message msg = Message.obtain(null, what);
        		msg.obj = o;

        		mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
	}
	
	private void sendBundle(int what, Bundle o)
	{
		for (int i=mClients.size()-1; i>=0; i--) {
            try {
        		Message msg = Message.obtain(null, what);
        		msg.setData(o);

        		mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
	}
	
	private String getMatch(String regEx, String text)
	{
		Pattern p = Pattern.compile(regEx);
	    Matcher m = p.matcher(text);
	    
	    if(m.find()) {
	    	return m.group(1);
    	}
	    
	    return "";
	}
	
	private void parseCapabilities(String cap)
	{
		mStatus.putString("FIRMWARE_NAME", getMatch(REGEX_FIRMWARE_NAME, cap));
		mStatus.putString("FIRMWARE_URL", getMatch(REGEX_FIRMWARE_URL, cap));
		mStatus.putString("MACHINE_TYPE", getMatch(REGEX_MACHINE_TYPE, cap));
		mStatus.putString("PROTOCOL_VERSION", getMatch(REGEX_PROTOCOL_VERSION, cap));
		mStatus.putString("EXTRUDER_COUNT", getMatch(REGEX_EXTRUDER_COUNT, cap));
		
		sendBundle(MSG_STATUS, mStatus);
	}

	public static class IncomingHandler extends Handler
	{
		private WeakReference<RepRapConnectionService> mService;
		
		public IncomingHandler(RepRapConnectionService service)
		{
			mService = new WeakReference<RepRapConnectionService>(service);
		}
				
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                	mService.get().mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                	mService.get().mClients.remove(msg.replyTo);
                    break;
                case MSG_GET_DEVICES:
                	mService.get().sendObject(MSG_DEVICE_LIST, mService.get().GetDevices());
                	break;
                case MSG_CONNECT_DEVICE:
                	String address = (String)msg.obj;
                	mService.get().connect(address);
                	break;
                case MSG_COMMAND:
                	String command = (String)msg.obj;
                	mService.get().command(command);
                	break;
                case MSG_COMMAND_RESPONSE:
                	Bundle data = msg.getData();
                	
                	//check for status messages and update status data...
                	if(data.getString("Command") == CMD_CAPABILITIES)
                	{
                		//parse out the values...
                		mService.get().parseCapabilities(data.getString("Response"));
                	}
                	
                	mService.get().sendBundle(MSG_COMMAND_RESPONSE, data);
                	break;
                case MSG_GET_STATUS:
                	mService.get().sendBundle(MSG_STATUS, mService.get().mStatus);
                	break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
	
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

	public void connectionFailed() {
		mConnectThread = null;
		sendMessage(MSG_CONNECTION_FAILED);
	}

	public void command(String command) {
		mCommandThread = new RepRapCommandThread(mMessenger, command, mInStream, mOutStream);
		mCommandThread.start();
	}

	public void connect(String address) {
    	mDevice = mBtAdapter.getRemoteDevice(address);
    	// Start the thread to connect with the given device
        mConnectThread = new BluetoothConnectThread(mDevice, mBtAdapter, this);
        mConnectThread.start();
        setState(STATE_CONNECTING);
	}

	public void connected(BluetoothSocket mmSocket) {
		mConnectThread = null;
		mSocket = mmSocket;
		try {
			mInStream = mSocket.getInputStream();
			mOutStream = mSocket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		sendMessage(MSG_CONNECTED);
	}
}
