package com.isonprojects.beagleremotedisplay;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.SynchronousQueue;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String ACTION_USB_PERMISSION =
    	    "com.android.example.USB_PERMISSION";
	
	
	private static int DATA_SIZE = 4096;
	private static int AUDIO_BUFFER_SIZE = 4096 * 4;
	private static int DATA_HEADER_SIZE = 4;
	private static int DATA_PACKET_SIZE = 5000;
	private static int DATA_AUDIO = 1;
	private static int DATA_VIDEO = 2;
	
	private final char KEYCODES[] = {
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,'\t',' ','-','=','[',
			']','\\','\\',';','\'','`',',','.','/',0,0,0,0,0,0,0
	};
	
	public static SynchronousQueue<Point> mousePoints = new SynchronousQueue<Point>();
	public static SynchronousQueue<byte[]> audioData = new SynchronousQueue<byte[]>();
	public static SynchronousQueue<byte[]> videoData = new SynchronousQueue<byte[]>();
	
	private Thread mouseThread;
	private Thread audioThread;
	private AudioTrack audioTrack;
	private PendingIntent mPermissionIntent;
	private UsbManager manager = null;
	private UsbAccessory accessory = null;
	ParcelFileDescriptor mFileDescriptor = null;
    FileInputStream mInputStream = null;
    FileOutputStream mOutputStream = null;
    FileDescriptor fd = null;
	
	private TextView textView = null;
	private Button connectButton;
	private Button keyboardButton;
	private Button leftButton;
	private Button rightButton;
	private ImageView imageView;
	private LinearLayout linearLayout;

	private boolean stopRequested = false;
	float downx, downy, upx, upy;
	
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
   	 
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                	accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(accessory != null){
                        	mFileDescriptor = manager.openAccessory(accessory);
                            if (mFileDescriptor != null) {
                            	textView.setText("Connected");
                                fd = mFileDescriptor.getFileDescriptor();
                                mInputStream = new FileInputStream(fd);
                                mOutputStream = new FileOutputStream(fd);
                                
                                audioThread.start();
                                mouseThread.start();
                				
                				new Thread(){
                					public void run(){
                						byte buffer[] = new byte[DATA_PACKET_SIZE];
                						int count = 1;
                						while(!stopRequested){
                							try {
                								mInputStream.read(buffer, 0, DATA_PACKET_SIZE);
                	        					System.out.println("Adding: " + count);
                	        					
                	        					byte[] data = new byte[DATA_SIZE];
                	        					System.arraycopy(buffer, DATA_HEADER_SIZE, data, 0, DATA_SIZE);
                	        					
                	        					
                	        					if ((int)buffer[0] == DATA_AUDIO){
                	        						audioData.put(data);
                	        					}
                	        					else if ((int)buffer[0] == DATA_VIDEO){
                	        						videoData.put(buffer);
                	        					}
                	        					
                								System.out.println("Add: " + count);
                								//fileOutputStream.write(buffer);
                								count++;
                							} catch (InterruptedException e) {
                								// TODO Auto-generated catch block
                								e.printStackTrace();
                								
                							} catch (IOException e) {
                								// TODO Auto-generated catch block
                								e.printStackTrace();
                							}
                						}
                					}
                				}.start();
                                
                                AsyncTask<FileOutputStream, Integer, Integer> task = new AsyncTask<FileOutputStream, Integer, Integer>() {

									@Override
									protected Integer doInBackground(
											FileOutputStream... params) {
										byte data[] = {3,1,0,0,0,0,0,0};
										data[2] = (byte)((GlobalAttributes.DISPLAYHEIGHT & 0xFF00) >> 8);
										data[3] = (byte)(GlobalAttributes.DISPLAYHEIGHT & 0xFF);
										
										data[4] = (byte)((GlobalAttributes.DISPLAYWIDTH & 0xFF00) >> 8);
										data[5] = (byte)(GlobalAttributes.DISPLAYWIDTH & 0xFF);
										
										try{
											params[0].write(data);
										}
										catch(Exception ex){
											return -1;
										}
										
										return 0;
									}
                                	
									@Override
									protected void onPostExecute(Integer result) {
										
									}
								};

								task.execute(mOutputStream);
                            }
                        }
                    }
                    else {
                        //Log.d(TAG, "permission denied for accessory " + accessory);
                    	Toast.makeText(getApplicationContext(), "Permission Denied!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
        textView = (TextView)this.findViewById(R.id.textView);
        connectButton = (Button)this.findViewById(R.id.connectButton);
        keyboardButton = (Button)this.findViewById(R.id.keyboardButton);
        leftButton = (Button)this.findViewById(R.id.leftButton);
        rightButton = (Button)this.findViewById(R.id.rightButton);
        
        imageView = (ImageView)this.findViewById(R.id.imageView1);
        linearLayout = (LinearLayout)this.findViewById(R.id.linearLayout);
        
        mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
    	IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    	registerReceiver(mUsbReceiver, filter);
    	
    	audioTrack = new  AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE, AudioTrack.MODE_STREAM);
    	
    	mouseThread = new Thread(){
        	public void run(){
        		stopRequested = false;
        		byte data[] = new byte[8];
        		
        		while (!stopRequested){
        			try {
        				int i = 0;
        				data[i++] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
        				data[i++] = (byte)getResources().getInteger(R.integer.MOUSEMOVE);
        				
        				Point point = MainActivity.mousePoints.take();
        				
        				data[i+1] = (byte) (point.x);// - lastPosition.x);
        				data[i+3] = (byte) (point.y);// - lastPosition.y);
        				data[i+0] = point.x < 0.0 ? (byte)1 : (byte)0; 
        				data[i+2] = point.y < 0.0 ? (byte)1 : (byte)0;
        				data[i+1] = (byte) Math.abs(point.x);
        				data[i+3] = (byte) Math.abs(point.y);
        				
        				i += 2;
        				sendMouseData(data);
        			} 
        			catch (InterruptedException e) {
        				System.out.println("Mouse Point Fetching Interrupted");
        			}
        		}
        	}
        };
        
        audioThread = new Thread(){
        	public void run(){
        		stopRequested = false;
        		int offset = 0;
        		int count = 0;
        		
        		audioTrack.play();
        		while (!stopRequested){
        			try {
        				byte[] data = MainActivity.audioData.take();
        				if (data != null){
        					System.out.println("Playing: " + count);
        					audioTrack.write(data, offset, data.length);
        					offset += data.length;
        					offset %= AUDIO_BUFFER_SIZE;
        					//handler.sendEmptyMessage(0);
        					System.out.println("Played: " + count);
        					count++;
        				}
        			} 
        			catch (InterruptedException e) {
        				System.out.println("Mouse Point Fetching Interrupted");
        			}
        		}
        		audioTrack.stop();
        		audioTrack.release();
        	}
        };
        
        
        leftButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				byte[] data = {0,0,0,0,0,0,0,0};
				data[0] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
				data[1] = (byte)getResources().getInteger(R.integer.MOUSELEFT);
				
				sendMouseData(data);
				
			}
		});
        
        rightButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				byte[] data = {0,0,0,0,0,0,0,0};
				data[0] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
				data[1] = (byte)getResources().getInteger(R.integer.MOUSERIGHT);
				
				sendMouseData(data);
			}
		});
        
        keyboardButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				InputMethodManager inputMethodManager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			    inputMethodManager.toggleSoftInputFromWindow(linearLayout.getApplicationWindowToken(), InputMethodManager.SHOW_FORCED, 0); 
				
			}
		});
        
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	GlobalAttributes.DISPLAYHEIGHT = imageView.getHeight();
                GlobalAttributes.DISPLAYWIDTH = imageView.getWidth();
                
            	textView.setText("connecting");
            	
            	manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            	UsbAccessory[] accessoryList = manager.getAccessoryList();
            	Toast.makeText(getApplicationContext(), "" + accessoryList.length, Toast.LENGTH_LONG).show();
            	manager.requestPermission(accessoryList[0], mPermissionIntent);
            	
            }
        });
        
        imageView.setOnTouchListener(new OnTouchListener() {			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int eid = event.getAction();
				switch (eid){
					case MotionEvent.ACTION_DOWN:
						downx = event.getX();
						downy = event.getY();
						break;
						
					case MotionEvent.ACTION_MOVE:
					case MotionEvent.ACTION_UP:
						upx = event.getX();
						upy = event.getY();
						
						//Toast.makeText(getApplicationContext(), "x=" + upx + "y=" + upy, Toast.LENGTH_SHORT).show();
						
						int x = (int)Math.ceil((double)(upx - downx));
						int y = (int)Math.ceil((double)(upy - downy));
						
						if (Math.abs(x) > 0) downx = upx;
						if (Math.abs(y) > 0) downy = upy;
						
						if ((Math.abs(x) > 0 || Math.abs(y) > 0) && mousePoints.size() < 1000);
						{
							try{
								mousePoints.add(new Point(x, y));
							}
							catch(Exception ex){
								
							}
						}
							
						break;
					
					default:
						break;
				}	
					
				return true;
			}
		});
	}
	
	@Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	
    	Toast.makeText(getApplicationContext(), "" + (char)event.getUnicodeChar(), Toast.LENGTH_SHORT).show();
    	
    	if (event.getUnicodeChar() >= 'A' && event.getUnicodeChar() <= 'Z'){
    		sendKeyboardData(event.getUnicodeChar() - 'A' + 4);
    	}
    	else if(event.getUnicodeChar() >= 'a' && event.getUnicodeChar() <= 'z'){
    		sendKeyboardData(event.getUnicodeChar() - 'a' + 4);
    	}
    	else if(event.getUnicodeChar() >= '1' && event.getUnicodeChar() <= '9'){
    		sendKeyboardData(event.getUnicodeChar() - '0' + 30);
    	}
    	else if(event.getUnicodeChar() == '0'){
    		sendKeyboardData(event.getUnicodeChar() - '0' + 39);
    	}
    	else{
    		for (int i=0;i<KEYCODES.length; i++){
    			if (KEYCODES[i] == event.getUnicodeChar()){
    				sendKeyboardData(i);
    				break;
    			}
    		}
    	}
    	//sendKeyboardData();
    	
    	return super.onKeyUp(keyCode, event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	// TODO Auto-generated method stub
    	return super.onKeyDown(keyCode, event);
    }
	
	private void sendMouseData(byte data[]){
    	byte buffer[] = {0,0,0,0,0,0,0,0};

		if (data.length < buffer.length){
			//System.arraycopy(data, 0, buffer, 0, data.length);
		}
		
		try{
        	try {
        		mOutputStream.write(data);
        		
			} catch (Exception e1) {
				e1.printStackTrace();
			}
    	}
    	catch (Exception ex){
    	}
    }
    
    private void sendKeyboardData(int keyIndex){
    	byte buffer[] = {0,0,0,0,0,0,0,0};
    	buffer[0] = (byte)getResources().getInteger(R.integer.KEYBOARDCONTROL);
    	buffer[2] = (byte)keyIndex;

		Toast.makeText(getApplicationContext(), "Receiver", Toast.LENGTH_SHORT).show();
		
		try{
        	try {
        		mOutputStream.write(buffer);
        		
			} catch (Exception e1) {
				textView.setText("Error again");
				Toast.makeText(getApplicationContext(), "Error again", Toast.LENGTH_SHORT).show();
				e1.printStackTrace();
			}
    	}
    	catch (Exception ex){
    		textView.setText("Moha bipod" + ex.getMessage());
    	}

    }

    @Override
    protected void onStop() {
    	try {
			mInputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        try {
			mOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        try {
			mFileDescriptor.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        stopRequested = true;
        
    	super.onStop();
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
