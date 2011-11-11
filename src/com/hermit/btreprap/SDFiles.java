package com.hermit.btreprap;

import java.util.ArrayList;
import java.util.Set;

import com.hermit.btreprap.SelectDeviceActivity.IncomingHandler;
import com.hermit.btreprap.service.RepRapConnectionService;

import android.app.ListActivity;
import android.app.ProgressDialog;
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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class SDFiles extends ListActivity {
	private static final int STATE_START = 0;
    private static final int STATE_GETTING_LIST = 1;
	private static final int STATE_WAITING_USER = 2;
	
    public static class IncomingHandler extends Handler
	{
		private SDFiles mActivity = null;
		private Bundle mLastSet = null;
		
		public void Attach(SDFiles activity)
		{
			mActivity = activity;
			
			if(mActivity.mState == STATE_GETTING_LIST && mLastSet != null)
			{
				mActivity.onCommandResponse(mLastSet);
			}
		}
		
		public void Detach()
		{
			mActivity = null;
		}
		
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RepRapConnectionService.MSG_COMMAND_RESPONSE:
                	Bundle command = msg.getData();
                	if(mActivity != null) mActivity.onCommandResponse(command);
                	break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
    
    private void onCommandResponse(Bundle data)
    {
    	String[] files;
    	files = data.getString("Response").split("\n");
    	
    	mFiles = new ArrayList<String>();
    	for(int i=1;i<files.length-2;i++)
    	{
    		mFiles.add(files[i]);
    	}
    	
    	mState = STATE_WAITING_USER;
    	
    	refreshFileList();
    	
    }
	
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private IncomingHandler mHandler;
    private Messenger mMessenger;
    private Messenger mServiceMessenger;
    
    private int mState;
    private ArrayList<String> mFiles;
    
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
		                    RepRapConnectionService.MSG_COMMAND);
		            msg.replyTo = mMessenger;
		            msg.obj = "M20";
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
        
        mHandler = new IncomingHandler();
    	mMessenger = new Messenger(mHandler);
    	
        if(savedInstanceState == null)
        {
        	mState = STATE_START;
        }else{
        	mState = savedInstanceState.getInt("mState");
        	
    		if(mState >= STATE_WAITING_USER)
    		{
    			mFiles = savedInstanceState.getStringArrayList("mFiles");
    			refreshFileList();
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
			outState.putStringArrayList("mFiles", mFiles);
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
	}

	public void refreshFileList()
	{
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.device_name); 
        
        setListAdapter(adapter);
        
        if (mFiles.size() > 0) {
            for (String device : mFiles) {
                adapter.add(device);
            }
        }
	}
	
	public void onListItemClick (ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);

        String info = ((TextView) v).getText().toString();
        
        //TODO: Some sort of progress dialog? for now just exit the actvity
        
        try {
	        Message msg = Message.obtain(null,
	                RepRapConnectionService.MSG_COMMAND);
	        msg.replyTo = mMessenger;
	        msg.obj = "M23 " + info.toLowerCase();
	        mServiceMessenger.send(msg);

	        msg = Message.obtain(null,
	                RepRapConnectionService.MSG_COMMAND);
	        msg.replyTo = mMessenger;
	        msg.obj = "M24";
	        mServiceMessenger.send(msg);
        }
        catch(RemoteException e)
        {
        
        }
        
        finish();
	}
    
}
