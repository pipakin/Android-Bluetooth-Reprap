package com.hermit.btreprap;

import com.hermit.btreprap.free.R;
import com.hermit.btreprap.DeviceActivity.IncomingHandler;
import com.hermit.btreprap.service.RepRapConnectionService;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

public class LogActivity extends Activity {
	public static class IncomingHandler extends Handler
	{
		private LogActivity mActivity = null;
		
		public void Attach(LogActivity activity)
		{
			mActivity = activity;
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
                	if(mActivity != null) mActivity.onCommand(command);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

	}
	
	private IncomingHandler mHandler;
    private Messenger mMessenger;
    private Messenger mServiceMessenger;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log_layout);
		TextView vw = (TextView)findViewById(R.id.textLog);
		vw.setText("");
		mHandler = new IncomingHandler();
	    mMessenger = new Messenger(mHandler);
	    mHandler.Attach(this);

        mServiceMessenger = getIntent().getParcelableExtra("Messenger");
        
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
	
	public void onCommand(Bundle command) {
		TextView vw = (TextView)findViewById(R.id.textLog);
		
		String text = command.getString("Command") + "\n" + command.getString("Response");
		vw.append(text);
	}
}
