package com.hermit.btreprap;

import java.util.Set;

import com.hermit.btreprap.free.R;
import com.hermit.btreprap.SelectDeviceActivity.IncomingHandler;
import com.hermit.btreprap.service.RepRapConnectionService;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceActivity extends Activity {
	
	private final static int STATE_START = 1;
	private final static int STATE_GETTING_STATUS = 2;
	private final static int STATE_GOT_STATUS = 3;
	
	private int mState;
	private Bundle mStatus;
	
	public static class IncomingHandler extends Handler
	{
		private DeviceActivity mActivity = null;
		private Bundle mLastStaus = null;
		
		public void Attach(DeviceActivity activity)
		{
			mActivity = activity;
			
			if(mActivity.mState == STATE_GETTING_STATUS && mLastStaus != null)
			{
				mActivity.onStatus(mLastStaus);
			}
		}
		
		public void Detach()
		{
			mActivity = null;
		}
		
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RepRapConnectionService.MSG_STATUS:
                	mLastStaus = msg.getData();
                	if(mActivity != null) mActivity.onStatus(mLastStaus);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
	
	private IncomingHandler mHandler;
    private Messenger mMessenger;
    private Messenger mServiceMessenger;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_layout);
        
        mHandler = new IncomingHandler();
    	mMessenger = new Messenger(mHandler);
    	mHandler.Attach(this);
        
        if(savedInstanceState == null)
        {
        	mState = STATE_START;
        }
        else
        {
        	mState = savedInstanceState.getInt("mState");
        	
        	if(mState == STATE_GOT_STATUS)
        	{
        		Bundle status = savedInstanceState.getBundle("mStatus");
        		onStatus(status);
        	}
        }
        mServiceMessenger = getIntent().getParcelableExtra("Messenger");
     
        try {
            Message msg = Message.obtain(null,
                    RepRapConnectionService.MSG_REGISTER_CLIENT);
            msg.replyTo = mMessenger;
            mServiceMessenger.send(msg);
            
            if(mState == STATE_START)
            {
	            msg = Message.obtain(null,
	                    RepRapConnectionService.MSG_GET_STATUS);
	            msg.replyTo = mMessenger;
	            mState = STATE_GETTING_STATUS;
	            mServiceMessenger.send(msg);
            }

        } catch (RemoteException e) {
            // In this case the service has crashed before we could even
            // do anything with it; we can count on soon being
            // disconnected (and then reconnected if it can be restarted)
            // so there is no need to do anything here.
        	Log.e("BTRR", "connection error", e);
        }
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Message msg = Message.obtain(null,
                RepRapConnectionService.MSG_UNREGISTER_CLIENT);
        msg.replyTo = mMessenger;
        try {
			mServiceMessenger.send(msg);
		} catch (RemoteException e) {
		}
        
        mHandler.Detach();
	}
	
	private void setFieldText(int id, String text)
	{
		TextView vw = (TextView)findViewById(id); 
		vw.setText(text);
	}
    
    private void onStatus(Bundle status) {
		mStatus = status;
		mState = STATE_GOT_STATUS;
		
		//populate fields...
		setFieldText(R.id.textName, mStatus.getString("FIRMWARE_NAME"));
		setFieldText(R.id.textUrl, mStatus.getString("FIRMWARE_URL"));
		setFieldText(R.id.textMachine, mStatus.getString("MACHINE_TYPE"));
		setFieldText(R.id.textProtocol, mStatus.getString("PROTOCOL_VERSION"));
		
	}

	@Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putInt("mState", mState);
    	outState.putBundle("mStatus", mStatus);
    }
}
