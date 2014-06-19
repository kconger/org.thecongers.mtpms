package org.thecongers.itpms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import org.thecongers.itpms.R;
import org.thecongers.itpms.UserSettingActivity;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
 
public class MainActivity extends Activity {
  final int RECIEVE_MESSAGE = 1;		// Status for Handler
  private static final int SETTINGS_RESULT = 1;
  private SharedPreferences sharedPrefs;
  private NotificationManager notificationManager;
  private ObjectAnimator colorFade;
  
  private BluetoothAdapter btAdapter = null;
  private BluetoothSocket btSocket = null;
  private ConnectedThread mConnectedThread;
  private Set<BluetoothDevice>pairedDevices;
   
  // SPP UUID service
  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
 
  private static final String TAG = "iTPMS";
  private String address;
  private String svgFUILive;
  private String svgRUILive;
  TextView txtOutput;
  Handler h;
   
  /** Called when the activity is first created. */
  @SuppressLint("HandlerLeak")
@Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    setContentView(R.layout.activity_main);
    // Keep screen on
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    
    String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
    String pressureUnit = "psi";
	if (pressureFormat.contains("1")) {
		// KPa
		pressureUnit = "KPa";
	} else if (pressureFormat.contains("2")){
		// Kg-f
		pressureUnit = "Kg-f";
	} else if (pressureFormat.contains("3")){
		// Bar
		pressureUnit = "Bar";
	}
	String temperatureUnit = "C";
	if (sharedPrefs.getString("preftempf", "0").contains("0")) {
		// F
		temperatureUnit = "F";
	}
    String svgUI = readRawTextFile(this, R.raw.ui);
    svgFUILive = svgUI.replaceAll("PP", "--");
    svgFUILive = svgFUILive.replaceAll("TTT", "---");
    svgFUILive = svgFUILive.replaceAll("VVV", "---");
    svgFUILive = svgFUILive.replaceAll("PSI", pressureUnit);
    svgFUILive = svgFUILive.replaceAll("TU", temperatureUnit);
    final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
    imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    
    svgRUILive = svgUI.replaceAll("PP", "--");
    svgRUILive = svgRUILive.replaceAll("TTT", "---");
    svgRUILive = svgRUILive.replaceAll("VVV", "---");
    svgRUILive = svgRUILive.replaceAll("PSI", pressureUnit);
    svgRUILive = svgRUILive.replaceAll("TU", temperatureUnit);
    final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
    imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    try
    {
       SVG svgF = SVG.getFromString(svgFUILive);
       SVG svgR = SVG.getFromString(svgRUILive);
       Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
       Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
       imageView.setImageDrawable(drawableF);
       imageView2.setImageDrawable(drawableR);
    }
    catch(SVGParseException e)
    {}
   
    txtOutput = (TextView) findViewById(R.id.txtOutput);
    
    h = new Handler() {
    	public void handleMessage(android.os.Message msg) {
    		switch (msg.what) {
            case RECIEVE_MESSAGE:	
            	// Message recieved
            	Log.d(TAG, "Serial Message Recieved");
            	
            	if ( msg.arg1 == 13 ){
            		byte[] readBuf = (byte[]) msg.obj;
                	// Convert to hex
                	String[] hexData = new String[13];
                	StringBuilder sbhex = new StringBuilder();
                	Log.d(TAG, "Message length: " + msg.arg1);
                    for(int i = 0; i < msg.arg1; i++)
                    {
                    	hexData[i]=String.format("%02X", readBuf[i]);
                    	sbhex.append(hexData[i]);
                    }
                    // Get sensor position
                    String position = hexData[3];
                    // Get sensor ID
                    StringBuilder sensorID = new StringBuilder();
                    sensorID.append(hexData[4]);
                    sensorID.append(hexData[5]);
                    sensorID.append(hexData[6]);
                    sensorID.append(hexData[7]);
                    
                    try{
                    	// Get temperature
                    	int tempC = Integer.parseInt(hexData[8],16) - 50;
                    	double temp = tempC;
                    	String temperatureUnit = "C";
                        // Get tire pressure
                        int psi = Integer.parseInt(hexData[9],16);
                        double pressure = psi;
                        String pressureUnit = "psi";
                        // Get battery voltage
                        int battery = Integer.parseInt(hexData[10],16) / 50;
                        double voltage = battery;
                        // Get pressure thresholds
                        int fLowPressure = Integer.parseInt(sharedPrefs.getString("prefFrontLowPressure", "30"));
                        int fHighPressure = Integer.parseInt(sharedPrefs.getString("prefFrontHighPressure", "46"));
                        int rLowPressure = Integer.parseInt(sharedPrefs.getString("prefRearLowPressure", "30"));
                        int rHighPressure = Integer.parseInt(sharedPrefs.getString("prefRearHighPressure", "46"));
                    	if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                    		// F
                    		double tempF = (9.0/5.0) * tempC + 32.0;
                    		temp = tempF;
                    		temperatureUnit = "F";
                    	}
                    	int formattedTemperature = (int)(temp + 0.5d);
                    	String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
                    	if (pressureFormat.contains("1")) {
                    		// KPa
                    		pressure = psi * 6.894757293168361;
                    		pressureUnit = "KPa";
                    	} else if (pressureFormat.contains("2")){
                    		// Kg-f
                    		pressure = psi * 0.070306957965539;
                    		pressureUnit = "Kg-f";
                    	} else if (pressureFormat.contains("3")){
                    		// Bar
                    		pressure = psi * 0.0689475729;
                    		pressureUnit = "Bar";
                    	}
                    	int formattedPressure = (int)(pressure + 0.5d);
                        // Get checksum
                        String checksum = hexData[11];
                        Log.d(TAG, "Sensor ID: " + sensorID.toString() + ", Sensor Position: " + position + ", Temperature(" + temperatureUnit + "): " + String.valueOf(temp) + ", Pressure(" + pressureUnit + "): " + String.valueOf(pressure) + ", Voltage: " + String.valueOf(voltage) + ", Checksum: " + checksum +", Data: " + sbhex.toString() +  ", Bytes:" + msg.arg1);
                        if (sensorID.toString().contains("1E9D533E")){
                        	Log.d(TAG, "Matched");
                        	int notificationID = 0;
                        	if (psi <= fLowPressure){
                            	// Send notification
                                Notify("iTPMS", "Low front tire pressure!", notificationID);
                                txtOutput.setText("Low front tire pressure!");
                                // Fade background in and out
                                colorFade = ObjectAnimator.ofObject(imageView, "backgroundColor", new ArgbEvaluator(), 0xffffffff, android.graphics.Color.RED);
                                colorFade.setDuration(3000);
                                colorFade.setRepeatCount(ValueAnimator.INFINITE);
                                colorFade.setRepeatMode(ValueAnimator.REVERSE);
                                colorFade.setAutoCancel(true);
                                colorFade.start();
                            } else if (psi >= fHighPressure) {
                            	// Send notification
                                Notify("iTPMS", "High front tire pressure!", notificationID);
                                txtOutput.setText("High front tire pressure!"); 
                                // Fade background in and out
                                colorFade = ObjectAnimator.ofObject(imageView, "backgroundColor", new ArgbEvaluator(), 0xffffffff, android.graphics.Color.RED);
                                colorFade.setDuration(3000);
                                colorFade.setRepeatCount(ValueAnimator.INFINITE);
                                colorFade.setRepeatMode(ValueAnimator.REVERSE);
                                colorFade.setAutoCancel(true);
                                colorFade.start();
                            } else {
                            	if (notificationManager != null){
                            		notificationManager.cancel(notificationID);
                            	}
                            	if (colorFade != null){
                            		colorFade.cancel();
                            	}
                            }
                        	String svgFUI = readRawTextFile(MainActivity.this, R.raw.ui);
                            svgFUILive = svgFUI.replaceAll("PP", String.valueOf(formattedPressure));
                            svgFUILive = svgFUILive.replaceAll("TTT", String.valueOf(formattedTemperature));
                            svgFUILive = svgFUILive.replaceAll("VVV", String.format( "%.2f", voltage ));
                            svgFUILive = svgFUILive.replaceAll("PSI", pressureUnit);
                            svgFUILive = svgFUILive.replaceAll("TU", temperatureUnit);
                            try
                            {
                               SVG svg = SVG.getFromString(svgFUILive);
                               svg.setDocumentViewBox(0, 0, svg.getDocumentWidth(),
                                       svg.getDocumentHeight());
                               Drawable drawable = new PictureDrawable(svg.renderToPicture());
                               imageView.setImageDrawable(drawable);
                            }
                            catch(SVGParseException e)
                            {}
                        	
                        } else if (sensorID.toString().contains("1E9D4899")){
                        	Log.d(TAG, "Matched");
                        	int notificationID = 1;
                        	if (psi <= rLowPressure){
                            	// Send notification
                                Notify("iTPMS", "Low rear tire pressure!", notificationID);
                                txtOutput.setText("Low rear tire pressure!");
                                // Fade background in and out
                                colorFade = ObjectAnimator.ofObject(imageView2, "backgroundColor", new ArgbEvaluator(), 0xffffffff, android.graphics.Color.RED);
                                colorFade.setDuration(3000);
                                colorFade.setRepeatCount(ValueAnimator.INFINITE);
                                colorFade.setRepeatMode(ValueAnimator.REVERSE);
                                colorFade.setAutoCancel(true);
                                colorFade.start();
                            } else if (psi >= rHighPressure){
                            	// Send notification
                                Notify("iTPMS", "High rear tire pressure!", notificationID);
                                txtOutput.setText("High rear tire pressure!");
                                // Fade background in and out
                                colorFade = ObjectAnimator.ofObject(imageView2, "backgroundColor", new ArgbEvaluator(), 0xffffffff, android.graphics.Color.RED);
                                colorFade.setDuration(3000);
                                colorFade.setRepeatCount(ValueAnimator.INFINITE);
                                colorFade.setRepeatMode(ValueAnimator.REVERSE);
                                colorFade.setAutoCancel(true);
                                colorFade.start();
                            } else {
                            	if (notificationManager != null){
                            		notificationManager.cancel(notificationID);
                            	}
                            	if (colorFade != null){
                            		colorFade.cancel();
                            	}
                            }
                        	String svgRUI = readRawTextFile(MainActivity.this, R.raw.ui);
                            svgRUILive = svgRUI.replaceAll("PP", String.valueOf(formattedPressure));
                            svgRUILive = svgRUILive.replaceAll("TTT", String.valueOf(formattedTemperature));
                            svgRUILive = svgRUILive.replaceAll("VVV", String.format( "%.2f", voltage ));
                            svgRUILive = svgRUILive.replaceAll("PSI", pressureUnit);
                            svgRUILive = svgRUILive.replaceAll("TU", temperatureUnit);
                            try
                            {
                               SVG svg = SVG.getFromString(svgRUILive);
                               svg.setDocumentViewBox(0, 0, svg.getDocumentWidth(),
                                       svg.getDocumentHeight());
                               Drawable drawable = new PictureDrawable(svg.renderToPicture());
                               imageView2.setImageDrawable(drawable);
                            }
                            catch(SVGParseException e)
                            {}
                        	
                        }
                    }
                    catch(NumberFormatException e){
                    	Log.d(TAG, "Malformed message, unexpected value");
                    }
            	} else {
            		Log.d(TAG, "Malformed message, message length: " + msg.arg1);
            	}  	
                
                break;
    		}
        };
	};
     
    btAdapter = BluetoothAdapter.getDefaultAdapter();		// get Bluetooth adapter
    checkBTState();
    pairedDevices = btAdapter.getBondedDevices();
    // If there are paired devices
	   if (pairedDevices.size() > 0) {
		   // Loop through paired devices
		   for (BluetoothDevice device : pairedDevices) {
			   if (device.getName().contains("iTPMS")) {
				   address = device.getAddress();
				   Log.d(TAG, "Paired iTPMS found: " + device.getName() + " " + device.getAddress());
				   
			   }
		   }
		   if (address == null) {
			   Toast.makeText(MainActivity.this,
   					"No previously paired iTPMS found!",
   					Toast.LENGTH_LONG).show();
		   }
	   }
	   if (address != null){
	    	// Set up a pointer to the remote node using it's address.
	        BluetoothDevice device = btAdapter.getRemoteDevice(address);
	       
	        // Two things are needed to make a connection:
	        //   A MAC address, which we got above.
	        //   A Service ID or UUID.  In this case we are using the
	        //     UUID for SPP.
	        
	    	try {
	    		btSocket = createBluetoothSocket(device);
	    	} catch (IOException e) {
	    		errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
	    	}
	        
	        /*try {
	          btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
	        } catch (IOException e) {
	          errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
	        }*/
	       
	        // Discovery is resource intensive.  Make sure it isn't going on
	        // when you attempt to connect and pass your message.
	        btAdapter.cancelDiscovery();
	       
	        // Establish the connection.  This will block until it connects.
	        Log.d(TAG, "...Connecting...");
	        try {
	          btSocket.connect();
	          Log.d(TAG, "....Connection ok...");
	          Toast.makeText(MainActivity.this,
	        		  "Connected to: " + device.getName() + " " + device.getAddress(),
	        		  Toast.LENGTH_LONG).show();
	        } catch (IOException e) {
	          try {
	            btSocket.close();
	          } catch (IOException e2) {
	            errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
	          }
	        }
	         
	        // Create a data stream so we can talk to server.
	        Log.d(TAG, "...Create Socket...");
	       
	        mConnectedThread = new ConnectedThread(btSocket);
	        mConnectedThread.start();
	    	
	    } else {
	    	Toast.makeText(MainActivity.this,
					"No iTPMS paired!",Toast.LENGTH_LONG).show();
	    }
  }
  
  private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
      if(Build.VERSION.SDK_INT >= 10){
          try {
              final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
              return (BluetoothSocket) m.invoke(device, MY_UUID);
          } catch (Exception e) {
              Log.e(TAG, "Could not create Insecure RFComm Connection",e);
          }
      }
      return  device.createRfcommSocketToServiceRecord(MY_UUID);
  }
   
  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "...onResume...");
    
  }

  @Override
  public void onPause() {
    super.onPause();
 
    Log.d(TAG, "...In onPause()...");
  /*
    try     {
      btSocket.close();
    } catch (IOException e2) {
      errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
    }
    */
  }

  private void checkBTState() {
    // Check for Bluetooth support and then check to make sure it is turned on
    // Emulator doesn't support Bluetooth and will return null
    if(btAdapter==null) { 
      errorExit("Fatal Error", "Bluetooth not supported");
    } else {
      if (btAdapter.isEnabled()) {
        Log.d(TAG, "Bluetooth is on");
      } else {
        //Prompt user to turn on Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, 1);
      }
    }
  }
 
  private void errorExit(String title, String message){
    Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
    finish();
  }
 
  private class ConnectedThread extends Thread {
	    private final InputStream mmInStream;
	 
	    public ConnectedThread(BluetoothSocket socket) {
	        InputStream tmpIn = null;
	 
	        // Get the input and output streams, using temp objects because
	        // member streams are final
	        try {
	            tmpIn = socket.getInputStream();
	        } catch (IOException e) { }
	 
	        mmInStream = tmpIn;
	    }
	 
	    public void run() {
	        byte[] buffer = new byte[256];  // buffer store for the stream
	        int bytes; // bytes returned from read()

	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	        	try {
	                // Read from the InputStream
	                bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
	            } catch (IOException e) {
	                break;
	            }
	        }
	    }
	 
	}
  //Runs when settings are updated
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
  	super.onActivityResult(requestCode, resultCode, data);
      if(requestCode==SETTINGS_RESULT)
      {
      	updateUserSettings();
      }
  }
  
  // Update UI when settings are updated
  private void updateUserSettings() 
  {
  	// Update
  }
  //Draw options menu
  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
		// Inflate the menu
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
  }
  
  // When settings menu is selected
  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
  	switch (item.getItemId()) {
          case R.id.action_settings:
              // Settings Menu was selected
          	Intent i = new Intent(getApplicationContext(), UserSettingActivity.class);
              startActivityForResult(i, SETTINGS_RESULT);
              return true;
          default:
              return super.onOptionsItemSelected(item);
  	}
  }
  //
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      setContentView(R.layout.activity_main);
      final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
      final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
      String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
      String pressureUnit = "psi";
      if (pressureFormat.contains("1")) {
    	  // KPa
    	  pressureUnit = "KPa";
      } else if (pressureFormat.contains("2")){
    		// Kg-f
    		pressureUnit = "Kg-f";
      } else if (pressureFormat.contains("3")){
  		// Bar
  		pressureUnit = "Bar";
      }
      String temperatureUnit = "C";
      if (sharedPrefs.getString("preftempf", "0").contains("0")) {
    	// F
  		temperatureUnit = "F";
      }
      String svgUI = readRawTextFile(this, R.raw.ui);
      if (svgFUILive == null){
          svgFUILive = svgUI.replaceAll("PP", "--");
          svgFUILive = svgFUILive.replaceAll("TTT", "---");
          svgFUILive = svgFUILive.replaceAll("VVV", "---");
          svgFUILive = svgFUILive.replaceAll("PSI", pressureUnit);
          svgFUILive = svgFUILive.replaceAll("TU", temperatureUnit); 	  
      } else if (svgRUILive == null) {
    	  svgRUILive = svgUI.replaceAll("PP", "--");
          svgRUILive = svgRUILive.replaceAll("TTT", "---");
          svgRUILive = svgRUILive.replaceAll("VVV", "---");
          svgRUILive = svgRUILive.replaceAll("PSI", pressureUnit);
          svgRUILive = svgRUILive.replaceAll("TU", temperatureUnit);
      }   
      try
      {
         SVG svgF = SVG.getFromString(svgFUILive);
         SVG svgR = SVG.getFromString(svgRUILive);
         Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
         Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
         imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
         imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
         imageView.setImageDrawable(drawableF);
         imageView2.setImageDrawable(drawableR);
      }
      catch(SVGParseException e)
      {}
      
  }
  //Read raw text file
  public static String readRawTextFile(Context ctx, int resId)
  {
	    InputStream inputStream = ctx.getResources().openRawResource(resId);

	    InputStreamReader inputreader = new InputStreamReader(inputStream);
	    BufferedReader buffreader = new BufferedReader(inputreader);
	    String line;
	    StringBuilder text = new StringBuilder();

	    try {
	        while (( line = buffreader.readLine()) != null) {
	            text.append(line);
	            text.append('\n');
	        }
	    } catch (IOException e) {
	        return null;
	    }
	    return text.toString();
	}
//Send Notification
  private void Notify(String notificationTitle, String notificationMessage, int notificationID) 
  {
  	  notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
  	  Intent notificationIntent = new Intent(this, MainActivity.class);
  	  PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
  	    	    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
  	  
  	  String alertURI = sharedPrefs.getString("prefsound","content://settings/system/notification_sound");
  	  Uri soundURI = Uri.parse(alertURI);
  	  // Build notification
  	  Notification.Builder builder = new Notification.Builder(this);
  	  builder.setContentTitle(notificationTitle)
        	.setContentText(notificationMessage)
        	.setSmallIcon(R.drawable.app_icon)
        	.setContentIntent(pendingIntent);
  	  // Check for LED enabled
  	  if (sharedPrefs.getBoolean("prefNotificationLED", true)) {
  		  builder.setLights(android.graphics.Color.RED, 1500, 1500);
  	  }
  	  // Check for sound enabled
  	  if (sharedPrefs.getBoolean("prefNotificationSound", true)) {
  		  builder.setSound(soundURI);
  	  }
  	  Notification notification = builder.build();
  	  // Check for vibration enabled
  	  if (sharedPrefs.getBoolean("prefNotificationVibrate", true)) {
  		  notification.defaults|= Notification.DEFAULT_VIBRATE;
  	  }
      // Make alert repeat until read
      notification.flags |= Notification.FLAG_INSISTENT;
      // Hide notification after its been selected
  	  notification.flags |= Notification.FLAG_AUTO_CANCEL;
  	  notification.priority = Notification.PRIORITY_MAX;
  	  // Send notification
  	  notificationManager.notify(notificationID, notification);
  }
}