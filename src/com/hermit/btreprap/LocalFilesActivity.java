package com.hermit.btreprap;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import com.hermit.btreprap.SDFiles.IncomingHandler;
import com.hermit.btreprap.service.RepRapConnectionService;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class LocalFilesActivity extends ListActivity {
	
	private static final int STATE_START = 0;
	private static final int STATE_WAITING_USER = 1;
	private static final int STATE_PUSHING_FILE = 2;
	
    public static class IncomingHandler extends Handler
	{
		private LocalFilesActivity mActivity = null;
		private int mLastProgress = 0;
		private int mLastTotal = 0;
		private String mLastMessage = "Uploading";
		
		public void Attach(LocalFilesActivity activity)
		{
			mActivity = activity;
			
			if(mActivity.mState == STATE_PUSHING_FILE && mLastProgress != 0)
			{
				mActivity.onProgress(mLastProgress, mLastTotal, mLastMessage);
			}
		}
		
		public void Detach()
		{
			mActivity = null;
		}
		
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RepRapConnectionService.MSG_SEND_PROGRESS:
                	mLastProgress = msg.arg1;
                	mLastTotal = msg.arg2;
                	mLastMessage = (String)msg.obj;
                	if(mActivity != null) mActivity.onProgress(mLastProgress, mLastTotal, mLastMessage);
                	break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
    
    private void onProgress(int progress, int total, String message)
    {
    	mProgress = progress;
    	
		//update progress dialog
    	mProgressDialog.setMax(total);
        mProgressDialog.setProgress(progress);
        mProgressDialog.setTitle(message);

    	if(progress == total)
    	{
    		if(mProgressDialog != null)
    		{
    			mProgressDialog.dismiss();
    			mProgressDialog = null;
    		}
    		finish();
    	}
    }
	
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private IncomingHandler mHandler;
    private Messenger mMessenger;
    private Messenger mServiceMessenger;
    
    private int mState;
    private int mProgress;
    private ProgressDialog mProgressDialog;
    
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
            mProgress = 0;
        }else{
        	mState = savedInstanceState.getInt("mState");
        	mProgress = savedInstanceState.getInt("mProgress");
        }

		if(mState <= STATE_WAITING_USER)
		{
			refreshFileList();
		}
		
		if(mState == STATE_PUSHING_FILE)
		{
	        mProgressDialog = ProgressDialog.show(this, "Uploading", "Please wait...", false);
	        mProgressDialog.setProgress(0);
		}

        mHandler.Attach(this);
    	bindService(new Intent(this, RepRapConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putInt("mState", mState);
		outState.putInt("mProgress", mProgress);
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
		
		if(mProgressDialog != null)
		{
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
	}

	public void refreshFileList()
	{
		//get local files
		File dir = Environment.getExternalStorageDirectory();

		String[] files = dir.list();
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, com.hermit.btreprap.free.R.layout.device_name); 
        
        setListAdapter(adapter);
        
        if (files.length > 0) {
            for (String file : files) {
            	if(file.toLowerCase().endsWith(".g") || file.toLowerCase().endsWith(".gcode"))
            	{
            		adapter.add(file);
            	}
            }
        }
        
        mState = STATE_WAITING_USER;
	}
	
	public void onListItemClick (ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);

        final String info = ((TextView) v).getText().toString();
        
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Transferring");
        mProgressDialog.setMessage("Please wait...");
        mProgressDialog.setMax(100);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setProgress(0);

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle("Print/Save");
        
        dialog.setButton(AlertDialog.BUTTON1,"Print", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
		        mProgressDialog.show();
                
		        try {
			        Message msg = Message.obtain(null,
			                RepRapConnectionService.MSG_SEND_FILE);
			        msg.replyTo = mMessenger;
			        msg.obj = info;
			        msg.arg1 = RepRapConnectionService.PUSH_PRINT;
			        mServiceMessenger.send(msg);
			        
			        mState = STATE_PUSHING_FILE;
		        }
		        catch(RemoteException e)
		        {
		        
		        }
				
			}
		});        
        
        dialog.setButton(AlertDialog.BUTTON2, "Save to SD", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
		        mProgressDialog.show();
                
		        try {
			        Message msg = Message.obtain(null,
			                RepRapConnectionService.MSG_SEND_FILE);
			        msg.replyTo = mMessenger;
			        msg.obj = info;
			        msg.arg1 = RepRapConnectionService.PUSH_SAVE;
			        mServiceMessenger.send(msg);
			        
			        mState = STATE_PUSHING_FILE;
		        }
		        catch(RemoteException e)
		        {
		        
		        }
				
			}
		});
        
        dialog.show();
        
	}

}
