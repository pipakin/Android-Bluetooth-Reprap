package com.hermit.btreprap;

import com.hermit.btreprap.service.RepRapConnectionService;

import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.TabHost;

public class BluetoothRepRap extends TabActivity {
	
	private static final int STATE_START = 1;
	private static final int STATE_GETTING_CONNECTION = 1;
	private static final int STATE_CONNECTED = 2;
	
	private int mState;
	private Boolean tabsCreated = false;
	Intent sdIntent;
	
	private void createTabs()
	{
		Resources res = getResources(); // Resource object to get Drawables
        TabHost tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Resusable TabSpec for each tab
        Intent intent;  // Reusable Intent for each tab

        // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent().setClass(this, DeviceActivity.class);
        intent.putExtra("Messenger", mServiceMessenger);

        // Initialize a TabSpec for each tab and add it to the TabHost
        spec = tabHost.newTabSpec("device").setIndicator("Device",
                          res.getDrawable(R.drawable.ic_tab_device))
                      .setContent(intent);
        tabHost.addTab(spec);

        // Do the same for the other tabs
        intent = new Intent().setClass(this, ManualActivity.class);
        intent.putExtra("Messenger", mServiceMessenger);
        spec = tabHost.newTabSpec("manual").setIndicator("Print",
                          res.getDrawable(R.drawable.ic_tab_device))
                      .setContent(intent);
        tabHost.addTab(spec);
        
        intent = new Intent().setClass(this, LogActivity.class);
        intent.putExtra("Messenger", mServiceMessenger);
        spec = tabHost.newTabSpec("log").setIndicator("Log",
                          res.getDrawable(R.drawable.ic_tab_device))
                      .setContent(intent);
        tabHost.addTab(spec);
	}

    private Messenger mServiceMessenger;
    
    
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	    	mServiceMessenger = new Messenger(service);
	    	if(mState == STATE_CONNECTED)
	    	{
	    		createTabs();
	    	}
			if(mState == STATE_START)
			{
				startActivityForResult(sdIntent, 0);
			}
	    }
	
	    public void onServiceDisconnected(ComponentName className) {
	    }
	};
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	sdIntent = new Intent().setClass(this, SelectDeviceActivity.class);
        setContentView(R.layout.main);
        
        if(savedInstanceState == null)
        {
        	mState = STATE_START;
        }else{
        	mState = savedInstanceState.getInt("mState");
        }
        
        bindService(new Intent(this, RepRapConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		       
		unbindService(mConnection);
	}
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putInt("mState", mState);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if(resultCode == RESULT_CANCELED)
    	{
    		finish();
    		return;
    	}
    	
    	createTabs();
    }
    
    @Override
    public void onStart() {
        super.onStart();
    }
}