package com.hermit.btreprap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;

import com.hermit.btreprap.service.RepRapConnectionService;
import com.hermit.btreprap.service.RepRapConnectionService.IncomingHandler;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.os.RemoteException;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class SelectDeviceActivity extends ListActivity {

    private static final int REQUEST_ENABLE_BT = 2;
    
    private static final int STATE_START = 0;
    private static final int STATE_GETTING_LIST = 1;
    private static final int STATE_WAITING_USER = 2;
    private static final int STATE_CONNECTING = 3;
    private static final int STATE_CONNECTED = 4;
    
    public static class IncomingHandler extends Handler
	{
		private SelectDeviceActivity mActivity = null;
		private Set<BluetoothDevice> mLastSet = null;
		
		public void Attach(SelectDeviceActivity activity)
		{
			mActivity = activity;
			
			if(mActivity.mState == STATE_GETTING_LIST && mLastSet != null)
			{
				mActivity.onDeviceList(mLastSet);
			}
		}
		
		public void Detach()
		{
			mActivity = null;
		}
		
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RepRapConnectionService.MSG_DEVICE_LIST:
                	mLastSet = (Set<BluetoothDevice>)msg.obj;
                	if(mActivity != null) mActivity.onDeviceList(mLastSet);
                    break;
                case RepRapConnectionService.MSG_CONNECTION_FAILED:
                	mActivity.mProgress.dismiss();
                	mActivity.mProgress = null;
                	mActivity.mState = STATE_WAITING_USER;
                	Toast.makeText(mActivity, "Unable to connect to device!", 1000).show();
                	break;
                case RepRapConnectionService.MSG_CONNECTED:
                	mActivity.mState = STATE_CONNECTED;
                	
                	//test!
                	Message rmsg = Message.obtain(null,
    	                    RepRapConnectionService.MSG_COMMAND);
    	            rmsg.obj = "M115";
					try {
						mActivity.mServiceMessenger.send(rmsg);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                	break;
            	//TEST!
                case RepRapConnectionService.MSG_COMMAND_RESPONSE:
                	mActivity.mProgress.dismiss();
                	mActivity.mProgress = null;
                    mActivity.setResult(RESULT_OK);
                    mActivity.finish();
                	break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
	
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private IncomingHandler mHandler;
    private Messenger mMessenger;
    private Messenger mServiceMessenger;
    private ProgressDialog mProgress;
    
    private int mState;
    private ArrayList<String> mDevices;
    
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {

	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  We are communicating with our
	        // service through an IDL interface, so get a client-side
	        // representation of that from the raw service object.
	        mServiceMessenger = new Messenger(service);
	
	        // We want to monitor the service for as long as we are
	        // connected to it.
	        try {
	            Message msg = Message.obtain(null,
	                    RepRapConnectionService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger;
	            mServiceMessenger.send(msg);
	            
	            if(mState == STATE_START)
	            {
		            msg = Message.obtain(null,
		                    RepRapConnectionService.MSG_GET_DEVICES);
		            msg.replyTo = mMessenger;
		            mState = STATE_GETTING_LIST;
		            mServiceMessenger.send(msg);
	            }
	
	        } catch (RemoteException e) {
	            // In this case the service has crashed before we could even
	            // do anything with it; we can count on soon being
	            // disconnected (and then reconnected if it can be restarted)
	            // so there is no need to do anything here.
	        }
	    }
	
	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        mServiceMessenger = null;
	    }
	};
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setResult(RESULT_CANCELED);
        
        mHandler = new IncomingHandler();
    	mMessenger = new Messenger(mHandler);
    	
        if(savedInstanceState == null)
        {
        	mState = STATE_START;
        }else{
        	mState = savedInstanceState.getInt("mState");
        	
    		if(mState >= STATE_WAITING_USER)
    		{
    			mDevices = savedInstanceState.getStringArrayList("mDevices");
    			refreshDeviceList();
    		}
    		
    		if(mState == STATE_CONNECTING)
    		{
    			mProgress = ProgressDialog.show(this, null, "Please wait...");
    		}
        }

        mHandler.Attach(this);
    	bindService(new Intent(this, RepRapConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putInt("mState", mState);
		
		if(mState >= STATE_WAITING_USER)
		{
			outState.putStringArrayList("mDevices", mDevices);
		}
	}
	
	protected void onDestroy ()
	{
		super.onDestroy();
		Message msg = Message.obtain(null,
                RepRapConnectionService.MSG_UNREGISTER_CLIENT);
        msg.replyTo = mMessenger;
        try {
			mServiceMessenger.send(msg);
		} catch (RemoteException e) {
		}
        
        mHandler.Detach();        
		unbindService(mConnection);
		
		if(mProgress != null)
		{
			mProgress.dismiss();
			mProgress = null;
		}
	}
	
	public void onDeviceList(Set<BluetoothDevice> devices)
	{
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.device_name); 
        mDevices = new ArrayList<String>();
        
        setListAdapter(adapter);
        
        if (devices.size() > 0) {
            for (BluetoothDevice device : devices) {
                adapter.add(device.getName() + "\n" + device.getAddress());
                mDevices.add(device.getName() + "\n" + device.getAddress());
            }
        }
        mState = STATE_WAITING_USER;
	}

	public void refreshDeviceList()
	{
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.device_name); 
        
        setListAdapter(adapter);
        
        if (mDevices.size() > 0) {
            for (String device : mDevices) {
                adapter.add(device);
            }
        }
	}
	
	public void onListItemClick (ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);

		// Get the device MAC address, which is the last 17 chars in the View
        String info = ((TextView) v).getText().toString();
        String address = info.substring(info.length() - 17);
        
        mProgress = ProgressDialog.show(this, "Connecting", "Please wait...");
        
        //connect
        try {
	        mState = STATE_CONNECTING;
	        Message msg = Message.obtain(null,
	                RepRapConnectionService.MSG_CONNECT_DEVICE);
	        msg.replyTo = mMessenger;
	        msg.obj = address;
	        mServiceMessenger.send(msg);
        }
        catch(RemoteException e)
        {
        
        }
	}
    
    

}
