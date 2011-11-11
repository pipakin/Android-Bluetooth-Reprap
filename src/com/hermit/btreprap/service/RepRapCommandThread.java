package com.hermit.btreprap.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class RepRapCommandThread extends Thread {
	private final Messenger mMessenger;
	private final String mCommand;
	private final InputStream mInStream;
	private final OutputStream mOutStream;

    byte[] buffer = new byte[1024];
	
	public RepRapCommandThread(Messenger messenger, String command, InputStream input, OutputStream output)
	{
		mMessenger = messenger;
		mCommand = command;
		mInStream = input;
		mOutStream = output;
	}
	
	public void run()
	{
		 try {
			Boolean done = false;
			String response = "";
			synchronized(mOutStream)
			{
				mOutStream.write((mCommand + "\n").getBytes());
				//read response until "ok" line is recieved...
				while(!done)
				{
					// Read from the InputStream
	                int bytes = mInStream.read(buffer);
	                response += new String(buffer, 0, bytes);
	                
	                if(response.startsWith("ok") || (response.contains("\nok") && response.endsWith("\n")))
	        		{
	                	done = true;
	                }
				}
			}

			Message msg = Message.obtain(null, RepRapConnectionService.MSG_COMMAND_RESPONSE);
        	Bundle data = new Bundle();
        	data.putString("Command", mCommand);
        	data.putString("Response", response);
        	msg.setData(data);
        	try {
				mMessenger.send(msg);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void cancel() {
		// TODO Auto-generated method stub
		
	}
}
