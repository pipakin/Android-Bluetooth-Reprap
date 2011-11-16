package com.hermit.btreprap.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class RepRapFilePushThread extends Thread {
	
	private final Messenger mServerMessenger;
	private final String mFileName;
	private final InputStream mInStream;
	private final OutputStream mOutStream;
	private final int mPushType;

	public RepRapFilePushThread(Messenger serverMessenger, String fileName, InputStream input, OutputStream output, int pushType)
	{
		mServerMessenger = serverMessenger;
		mFileName = fileName;
		mInStream = input;
		mOutStream = output;
		mPushType = pushType;
	}
	
	private void sendProgress(int progress, int total, String message)
	{
		Message msg = Message.obtain(null, RepRapConnectionService.MSG_SEND_PROGRESS);
		msg.arg1 = progress;
		msg.arg2 = total;
		msg.obj = message;
		
		try {
			mServerMessenger.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void cancel()
	{
		
	}
	
	private String writeCommand(byte[] outBytes, int count) throws IOException
	{
		boolean done = false;
		String response = "";
		byte[] buffer = new byte[1024];
		
		int realcount = count;
		if(realcount == 0)
			realcount = outBytes.length;
		
		mOutStream.write(outBytes, 0, realcount);
		
		//read response until "ok" line is recieved...
		while(!done)
		{
			// Read from the InputStream
            int bytes = mInStream.read(buffer );
            response += new String(buffer, 0, bytes);
            
            if(response.startsWith("ok") || (response.contains("\nok") && response.endsWith("\n")))
    		{
            	done = true;
            }
		}
		
		return response.replace("\nok", "").trim();
	}
	
	public void run()
	{
		if(mPushType == RepRapConnectionService.PUSH_PRINT)
		{
			sendCommands("Printing");
			sendProgress(100, 100, "Done");
		}else{
			Map<String, Integer> codecs = detectHighSpeed();
			
			if(codecs != null && codecs.containsKey("RAW"))
			{
				highSpeedXfer(codecs.get("RAW"));
			}else{
				lowSpeedXfer();
			}
		}
	}
	
	private void lowSpeedXfer()
	{
		Log.i("BTRR", "Initiating slow transfer...");
		sendProgress(0, 100, "Uploading (Slow)");
		
		try {
		    String calcedFileName = mFileName.toLowerCase().replace(".gcode", "").replace(".g", "");
		    if(calcedFileName.length() > 8)
		    {
		    	calcedFileName = calcedFileName.substring(0, 8);
		    }
		    
		    calcedFileName += ".g";
		    
		    Log.i("BTRR", "sending file: " + calcedFileName);
		    
		    writeCommand(("M28 " + calcedFileName + "\n").getBytes(), 0);

		    sendCommands("Uploading (Slow)");
		    
		    writeCommand("M29\n".getBytes(), 0);
			sendProgress((int)100, (int)100, "Uploading (Slow)");

		}
		catch (IOException e) {
		    //You'll need to add proper error handling here
			Log.e("BTRR", "Error reading file", e);
		}
	}
	
	private void sendCommands(String message)
	{
		//open the file
		File sdcard = Environment.getExternalStorageDirectory();

		//Get the text file
		File file = new File(sdcard,mFileName);

		try {
		    BufferedReader br = new BufferedReader(new FileReader(file));
		    String line;
		    
		    long len = file.length();
		    long curPos = 0;
		    while ((line = br.readLine()) != null) {
				
				byte[] outBytes = (line + "\n").getBytes();
				curPos += outBytes.length;
				
				if(!line.startsWith(";") && line.trim() != "")
				{
					writeCommand(outBytes, 0);
				}
				
				sendProgress((int)curPos, (int)len + 1, message);
		    }
		}
		catch (IOException e) {
		    //You'll need to add proper error handling here
			Log.e("BTRR", "Error reading file", e);
		}
	}
	
	private Map<String, Integer> detectHighSpeed()
	{
		sendProgress(0, 100, "Detecting Speed");
		Map<String, Integer> codecs = new HashMap<String, Integer>();
		
		String capabilities;
		String[] codecDescriptors;
		try {
			capabilities = writeCommand("M31\n".getBytes(),0);
			Log.i("BTRR", "capabilities: " + capabilities);
			codecDescriptors = capabilities.split(",");
		} catch (IOException e1) {
			Log.e("BTRR", "Error getting capabilities", e1);
			return null;
		}
		
		for(String codec : codecDescriptors)
		{
			String[] props = codec.split(":");
			
			if(props.length > 1)
			{
				codecs.put(props[0], Integer.parseInt(props[1]));
			}
		}
		
		return codecs;
	}
	
	private void highSpeedXfer(int chunkSize)
	{
		Log.i("BTRR", "Initiating fast transfer at " + chunkSize + " chunk size...");

		//open the file
		File sdcard = Environment.getExternalStorageDirectory();

		//Get the text file
		File file = new File(sdcard,mFileName);
		
		try {
			FileInputStream fr = new FileInputStream(file);
		    
		    String calcedFileName = mFileName.toLowerCase().replace(".gcode", "").replace(".g", "");
		    if(calcedFileName.length() > 8)
		    {
		    	calcedFileName = calcedFileName.substring(0, 8);
		    }
		    
		    calcedFileName += ".g";
		    
		    Log.i("BTRR", "sending file: " + calcedFileName);
		    
		    String fileCheck = writeCommand(("M30 RAW " + calcedFileName + "\n").getBytes(), 0);
		    Log.i("BTRR", "file check: " + fileCheck);
			
		    long len = file.length();
		    long curPos = 0;
		    byte[] buffer = new byte[chunkSize + 2];
		    int rlen = 0;
		    while ((rlen = fr.read(buffer, 1, chunkSize)) >= 0) {
				
				curPos += rlen;
				
				buffer[0] = 0;
				buffer[rlen + 1] = 0;
				writeCommand(buffer, rlen + 2);
								
				sendProgress((int)curPos, (int)len + 1, "Uploading (Fast)");
		    }
		    
	    	buffer[0] = 0;
	    	buffer[1] = 0;
		    writeCommand(buffer, 2);

			sendProgress((int)len + 1, (int)len + 1, "Uploading (Fast)");

		}
		catch (IOException e) {
		    Log.e("BTRR", "Error reading file", e);
		}
	}
}
