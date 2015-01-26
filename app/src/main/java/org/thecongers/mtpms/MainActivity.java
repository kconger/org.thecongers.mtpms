/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.thecongers.mtpms;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
 
public class MainActivity extends ActionBarActivity {

    private SharedPreferences sharedPrefs;
    private NotificationManager notificationManager;

    private View root;
    private TextView txtOutput;
    private TextView txtFrontPressure;
    private TextView txtFrontTemperature;
    private TextView txtFrontVoltage;
    private TextView txtRearPressure;
    private TextView txtRearTemperature;
    private TextView txtRearVoltage;

    private Drawable background;
    private Drawable redBackground;
    private Drawable backgroundDark;
    private Drawable redBackgroundDark;
    private Drawable txtOutBackground;
    private Drawable txtOutBackgroundDark;

    private BluetoothAdapter btAdapter = null;
    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "mTPMS";
    private final int RECEIVE_MESSAGE = 1;		// Status for Handler
    private static final int SETTINGS_RESULT = 1;
    private String address;
    private int frontStatus = 0;
    private int rearStatus = 0;
    private boolean itsDark = false;
    private long darkTimer = 0;
    private long lightTimer = 0;

    static SensorIdDatabase sensorDB;
    private LogData logger = null;
    private Handler sensorMessages;
    private ConnectThread btConnectThread;

    @SuppressLint("HandlerLeak")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        txtFrontPressure = (TextView) findViewById(R.id.txtFrontPressure);
        txtFrontTemperature = (TextView) findViewById(R.id.txtFrontTemperature);
        txtFrontVoltage = (TextView) findViewById(R.id.txtFrontVoltage);
        txtRearPressure = (TextView) findViewById(R.id.txtRearPressure);
        txtRearTemperature = (TextView) findViewById(R.id.txtRearTemperature);
        txtRearVoltage = (TextView) findViewById(R.id.txtRearVoltage);
        txtOutput = (TextView) findViewById(R.id.txtOutput);
        View myView = findViewById(R.id.appLayout);
        root = myView.getRootView();

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Watch for Bluetooth Changes
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(btReceiver, filter1);
        this.registerReceiver(btReceiver, filter2);
        this.registerReceiver(btReceiver, filter3);

        sensorDB = new SensorIdDatabase(this);

        // Backgrounds
        background = this.getResources().getDrawable(R.drawable.rectangle_bordered);
        redBackground = this.getResources().getDrawable(R.drawable.rectangle_bordered_red);
        backgroundDark = this.getResources().getDrawable(R.drawable.rectangle_bordered_dark);
        redBackgroundDark = this.getResources().getDrawable(R.drawable.rectangle_bordered_red_dark);
        txtOutBackground = this.getResources().getDrawable(R.drawable.rectangle);
        txtOutBackgroundDark = this.getResources().getDrawable(R.drawable.rectangle_dark);

        // Update textViews with select units
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

        txtFrontPressure.setText("--- " + pressureUnit);
        txtFrontTemperature.setText("--- " + temperatureUnit);
        txtFrontVoltage.setText("--- V");
        txtRearPressure.setText("--- " + pressureUnit);
        txtRearTemperature.setText("--- " + temperatureUnit);
        txtRearVoltage.setText("--- V");
        txtOutput.setBackground(txtOutBackground);
        txtOutput.setTextColor(getResources().getColor(android.R.color.black));

        // Check if there are sensor to wheel mappings
        if (sharedPrefs.getString("prefFrontID", "").equals("") && sharedPrefs.getString("prefRearID", "").equals("")) {
            new AlertDialog.Builder(this).setTitle(R.string.alert_setup_title).setMessage(R.string.alert_setup_message).setNeutralButton(R.string.alert_setup_button, null).show();
        }

        sensorMessages = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        // Message received
                        Log.d(TAG, "Serial Message Received, Length: " + msg.arg1);
                        // Check to see if message is the correct size
                        if (msg.arg1 == 13) {
                            byte[] readBuf = (byte[]) msg.obj;
                            // Convert to hex
                            String[] hexData = new String[13];
                            StringBuilder sbhex = new StringBuilder();
                            for (int i = 0; i < msg.arg1; i++) {
                                hexData[i] = String.format("%02X", readBuf[i]);
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

                            // Check if sensor ID is new
                            boolean checkID = sensorDB.sensorIdExists(sensorID.toString());
                            if (!checkID) {
                                // Add sensor ID to db
                                sensorDB.addID(sensorID.toString());
                                Toast.makeText(MainActivity.this,
                                        getResources().getString(R.string.toast_newSensor) + " " + sensorID.toString(),
                                        Toast.LENGTH_LONG).show();
                            }
                            // Only parse message if there is one or more sensor mappings
                            String prefFrontID = sharedPrefs.getString("prefFrontID", "");
                            String prefRearID = sharedPrefs.getString("prefRearID", "");
                            if (!prefFrontID.equals("") || !prefRearID.equals("")) {
                                try {
                                    // Get temperature
                                    int tempC = Integer.parseInt(hexData[8], 16) - 50;
                                    double temp = tempC;
                                    String temperatureUnit = "C";
                                    // Get tire pressure
                                    int psi = Integer.parseInt(hexData[9], 16);
                                    double pressure = psi;
                                    String pressureUnit = "psi";
                                    // Get battery voltage
                                    double voltage = Integer.parseInt(hexData[10], 16) / 50;
                                    // Get pressure thresholds
                                    int fLowPressure = Integer.parseInt(sharedPrefs.getString("prefFrontLowPressure", "30"));
                                    int fHighPressure = Integer.parseInt(sharedPrefs.getString("prefFrontHighPressure", "46"));
                                    int rLowPressure = Integer.parseInt(sharedPrefs.getString("prefRearLowPressure", "30"));
                                    int rHighPressure = Integer.parseInt(sharedPrefs.getString("prefRearHighPressure", "46"));
                                    if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                                        // F
                                        temp = (9.0 / 5.0) * tempC + 32.0;
                                        temperatureUnit = "F";
                                    }
                                    int formattedTemperature = (int) (temp + 0.5d);
                                    String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
                                    if (pressureFormat.contains("1")) {
                                        // KPa
                                        pressure = psi * 6.894757293168361;
                                        pressureUnit = "KPa";
                                    } else if (pressureFormat.contains("2")) {
                                        // Kg-f
                                        pressure = psi * 0.070306957965539;
                                        pressureUnit = "Kg-f";
                                    } else if (pressureFormat.contains("3")) {
                                        // Bar
                                        pressure = psi * 0.0689475729;
                                        pressureUnit = "Bar";
                                    }
                                    int formattedPressure = (int) (pressure + 0.5d);
                                    // Get checksum
                                    String checksum = hexData[11];
                                    Log.d(TAG, "Sensor ID: " + sensorID.toString() + ", Sensor Position: " + position + ", Temperature(" + temperatureUnit + "): " + String.valueOf(temp) + ", Pressure(" + pressureUnit + "): " + String.valueOf(pressure) + ", Voltage: " + String.valueOf(voltage) + ", Checksum: " + checksum + ", Data: " + sbhex.toString() + ", Bytes:" + msg.arg1);

                                    if (sensorID.toString().equals(prefFrontID)) {
                                        Log.d(TAG, "Front ID matched");
                                        // Check for data logging enabled
                                        if (sharedPrefs.getBoolean("prefDataLogging", false)) {
                                            // Log data
                                            if (logger == null) {
                                                logger = new LogData();
                                            }
                                            logger.write("front", String.valueOf(psi), String.valueOf(tempC), String.valueOf(voltage));
                                        }
                                        final LinearLayout  layoutFront = (LinearLayout) findViewById(R.id.layoutFront);
                                        if (psi <= fLowPressure) {
                                            frontStatus = 1;
                                            // Set background to red
                                            if (itsDark){
                                                layoutFront.setBackground(redBackgroundDark);
                                            } else {
                                                layoutFront.setBackground(redBackground);
                                            }
                                        } else if (psi >= fHighPressure) {
                                            frontStatus = 2;
                                            // Set background to red
                                            if (itsDark){
                                                layoutFront.setBackground(redBackgroundDark);
                                            } else {
                                                layoutFront.setBackground(redBackground);
                                            }
                                        } else {
                                            frontStatus = 0;
                                            // Reset background
                                            if (itsDark){
                                                layoutFront.setBackground(backgroundDark);
                                            } else {
                                                layoutFront.setBackground(background);
                                            }
                                        }

                                        txtFrontPressure.setText(String.valueOf(formattedPressure) + " " + pressureUnit);
                                        txtFrontTemperature.setText(String.valueOf(formattedTemperature) + " " + temperatureUnit);
                                        txtFrontVoltage.setText(String.format("%.2f", voltage) + " V");
                                    } else if (sensorID.toString().equals(prefRearID)) {
                                        Log.d(TAG, "Rear ID matched");
                                        // Check for data logging enabled
                                        if (sharedPrefs.getBoolean("prefDataLogging", false)) {
                                            // Log data
                                            if (logger == null) {
                                                logger = new LogData();
                                            }
                                            logger.write("rear", String.valueOf(psi), String.valueOf(tempC), String.valueOf(voltage));
                                        }
                                        final LinearLayout  layoutRear = (LinearLayout) findViewById(R.id.layoutRear);
                                        if (psi <= rLowPressure) {
                                            rearStatus = 1;
                                            // Set background to red
                                            if (itsDark){
                                                layoutRear.setBackground(redBackgroundDark);
                                            } else {
                                                layoutRear.setBackground(redBackground);
                                            }
                                        } else if (psi >= rHighPressure) {
                                            // Set background to red
                                            if (itsDark){
                                                layoutRear.setBackground(redBackgroundDark);
                                            } else {
                                                layoutRear.setBackground(redBackground);
                                            }
                                        } else {
                                            rearStatus = 0;
                                            // Reset background
                                            if (itsDark){
                                                layoutRear.setBackground(backgroundDark);
                                            } else {
                                                layoutRear.setBackground(background);
                                            }
                                        }
                                        txtRearPressure.setText(String.valueOf(formattedPressure) + " " + pressureUnit);
                                        txtRearTemperature.setText(String.valueOf(formattedTemperature) + " " + temperatureUnit);
                                        txtRearVoltage.setText(String.format("%.2f", voltage) + " V");
                                    }
                                    // Update txtOutput box and send notification
                                    if ((frontStatus == 0) && (rearStatus == 0)){
                                        txtOutput.setText("");
                                        if (notificationManager != null ){
                                            notificationManager.cancel(0);
                                        }
                                    } else if ((frontStatus == 1) && (rearStatus == 0)){
                                        txtOutput.setText(getResources().getString(R.string.alert_lowFrontPressure));
                                        Notify(getResources().getString(R.string.alert_lowFrontPressure));
                                    } else if ((frontStatus == 2) && (rearStatus == 0)){
                                        txtOutput.setText(getResources().getString(R.string.alert_highFrontPressure));
                                        Notify(getResources().getString(R.string.alert_highFrontPressure));
                                    } else if ((rearStatus == 1) && (frontStatus == 0)){
                                        txtOutput.setText(getResources().getString(R.string.alert_lowRearPressure));
                                        Notify(getResources().getString(R.string.alert_lowRearPressure));
                                    } else if ((rearStatus == 2) && (frontStatus == 0)){
                                        txtOutput.setText(getResources().getString(R.string.alert_highRearPressure));
                                        Notify(getResources().getString(R.string.alert_highRearPressure));
                                    } else if ((frontStatus == 1) && (rearStatus == 1)){
                                        txtOutput.setText(getResources().getString(R.string.alert_lowFrontLowRearPressure));
                                        Notify(getResources().getString(R.string.alert_lowFrontLowRearPressure));
                                    } else if ((frontStatus == 2) && (rearStatus == 2)){
                                        txtOutput.setText(getResources().getString(R.string.alert_highFrontHighRearPressure));
                                        Notify(getResources().getString(R.string.alert_highFrontHighRearPressure));
                                    } else if ((frontStatus == 1) && (rearStatus == 2)){
                                        txtOutput.setText(getResources().getString(R.string.alert_lowFrontHighRearPressure));
                                        Notify(getResources().getString(R.string.alert_lowFrontHighRearPressure));
                                    } else if ((frontStatus == 2) && (rearStatus == 1)){
                                        txtOutput.setText(getResources().getString(R.string.alert_highFrontLowRearPressure));
                                        Notify(getResources().getString(R.string.alert_highFrontLowRearPressure));
                                    }
                                    if (!itsDark) {
                                        txtOutput.setBackground(txtOutBackground);
                                        txtOutput.setTextColor(getResources().getColor(android.R.color.black));
                                        txtRearPressure.setTextColor(getResources().getColor(android.R.color.black));
                                        txtRearTemperature.setTextColor(getResources().getColor(android.R.color.black));
                                        txtRearVoltage.setTextColor(getResources().getColor(android.R.color.black));
                                    } else {
                                        txtOutput.setBackground(txtOutBackgroundDark);
                                        txtOutput.setTextColor(getResources().getColor(android.R.color.white));
                                        txtRearPressure.setTextColor(getResources().getColor(android.R.color.white));
                                        txtRearTemperature.setTextColor(getResources().getColor(android.R.color.white));
                                        txtRearVoltage.setTextColor(getResources().getColor(android.R.color.white));
                                    }
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "Malformed message, unexpected value");
                                }
                            }
                        } else {
                            Log.d(TAG, "Malformed message, message length: " + msg.arg1);
                        }
                        break;
                }
            }
        };
        // Light Sensor Stuff
        SensorManager sensorManager
                = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor
                = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null){
            Log.d(TAG,"Light sensor not found");
        }else {
            float max = lightSensor.getMaximumRange();
            sensorManager.registerListener(lightSensorEventListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG,"Light Sensor Max Value: " + max);
        }
        // Try to connect to iTPMSystem
        btConnect();

    }

    // Called when screen rotates or size changes
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);

        // Redraw Screen
        final LinearLayout  layoutFront = (LinearLayout) findViewById(R.id.layoutFront);
        final LinearLayout  layoutRear = (LinearLayout) findViewById(R.id.layoutRear);
        // Restore Text
        CharSequence currentTxtFrontPressure = txtFrontPressure.getText();
        txtFrontPressure = (TextView) findViewById(R.id.txtFrontPressure);
        txtFrontPressure.setText(currentTxtFrontPressure);

        CharSequence currentTxtFrontTemperature = txtFrontTemperature.getText();
        txtFrontTemperature = (TextView) findViewById(R.id.txtFrontTemperature);
        txtFrontTemperature.setText(currentTxtFrontTemperature);

        CharSequence currentTxtFrontVoltage = txtFrontVoltage.getText();
        txtFrontVoltage = (TextView) findViewById(R.id.txtFrontVoltage);
        txtFrontVoltage.setText(currentTxtFrontVoltage);

        CharSequence currentTxtRearPressure = txtRearPressure.getText();
        txtRearPressure = (TextView) findViewById(R.id.txtRearPressure);
        txtRearPressure.setText(currentTxtRearPressure);

        CharSequence currentTxtRearTemperature = txtRearTemperature.getText();
        txtRearTemperature = (TextView) findViewById(R.id.txtRearTemperature);
        txtRearTemperature.setText(currentTxtRearTemperature);

        CharSequence currentTxtRearVoltage = txtRearVoltage.getText();
        txtRearVoltage = (TextView) findViewById(R.id.txtRearVoltage);
        txtRearVoltage.setText(currentTxtRearVoltage);

        CharSequence currentTxt = txtOutput.getText();
        txtOutput = (TextView) findViewById(R.id.txtOutput);
        txtOutput.setText(currentTxt);

        // Restore Colors
        if (!itsDark){
            if (frontStatus > 0) {
                layoutFront.setBackground(redBackground);
            } else {
                layoutFront.setBackground(background);
            }
            if (rearStatus > 0) {
                layoutRear.setBackground(redBackground);
            } else {
                layoutRear.setBackground(background);
            }
            txtOutput.setBackground(txtOutBackground);
            txtFrontPressure.setTextColor(getResources().getColor(android.R.color.black));
            txtFrontTemperature.setTextColor(getResources().getColor(android.R.color.black));
            txtFrontVoltage.setTextColor(getResources().getColor(android.R.color.black));
            txtRearPressure.setTextColor(getResources().getColor(android.R.color.black));
            txtRearTemperature.setTextColor(getResources().getColor(android.R.color.black));
            txtRearVoltage.setTextColor(getResources().getColor(android.R.color.black));
            txtOutput.setTextColor(getResources().getColor(android.R.color.black));

        } else {
            if (frontStatus > 0) {
                layoutFront.setBackground(redBackgroundDark);
            } else {
                layoutFront.setBackground(backgroundDark);
            }
            if (rearStatus > 0) {
                layoutRear.setBackground(redBackgroundDark);
            } else {
                layoutRear.setBackground(backgroundDark);
            }
            txtOutput.setBackground(txtOutBackgroundDark);
            txtFrontPressure.setTextColor(getResources().getColor(android.R.color.white));
            txtFrontTemperature.setTextColor(getResources().getColor(android.R.color.white));
            txtFrontVoltage.setTextColor(getResources().getColor(android.R.color.white));
            txtRearPressure.setTextColor(getResources().getColor(android.R.color.white));
            txtRearTemperature.setTextColor(getResources().getColor(android.R.color.white));
            txtRearVoltage.setTextColor(getResources().getColor(android.R.color.white));
            txtOutput.setTextColor(getResources().getColor(android.R.color.white));
        }
    }

    //Draw options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // When options menu item is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Settings Menu was selected
                Intent i = new Intent(getApplicationContext(), UserSettingActivity.class);
                startActivityForResult(i, SETTINGS_RESULT);
                return true;
            case R.id.action_sensorReset:
                // Sensor Reset Menu Item was selected
                sensorDB.purgeID();
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                settings.edit().remove("prefFrontID").apply();
                settings.edit().remove("prefRearID").apply();
                return true;
            case R.id.action_about:
                // About was selected
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getResources().getString(R.string.alert_about_title));
                builder.setMessage(readRawTextFile(this, R.raw.about));
                builder.setPositiveButton(getResources().getString(R.string.alert_about_button), null);
                builder.show();
                return true;
            case R.id.action_exit:
                // Exit menu item was selected
                if (logger != null){
                    logger.shutdown();
                }
                finish();
                System.exit(0);
            default:
                return super.onOptionsItemSelected(item);
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
        // Shutdown Logger
        if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
            logger.shutdown();
        }
    }

    // Connect to iTPMSystem
    private boolean btConnect() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        if(btAdapter!=null) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().contains("iTPMS")) {
                        address = device.getAddress();
                        Log.d(TAG, "Paired iTPMSystem found: " + device.getName() + " " + device.getAddress());
                    }
                }
                if (address == null) {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.toast_noPaired),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (address != null){
                // Set up a pointer to the remote node using it's address.
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                btConnectThread = new ConnectThread(device);
                btConnectThread.start();
            } else {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.toast_noPaired),
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        Log.d(TAG, "Bluetooth not supported");
        return false;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    // Listens for Bluetooth broadcasts
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if ((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if connected
                Log.d(TAG, "iTPMSystem Connected");
                btConnect();
            }
            else if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if disconnected
                Log.d(TAG, "iTPMSystem Disconnected");
            }
        }
    };

    // Check current Bluetooth state
    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        if(btAdapter==null) {
            Log.d(TAG, "Bluetooth not supported");
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

    // Bluetooth Connect Thread
    private class ConnectThread extends Thread {
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because btSocket is final
            BluetoothSocket tmp = null;
            btDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = createBluetoothSocket(device);
            } catch (IOException e) {
                Log.d(TAG,"Bluetooth socket create failed: " + e.getMessage() + ".");
            }
            btSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            btAdapter.cancelDiscovery();
            Log.d(TAG, "Connecting to the iTPMSystem...");
            try {
                // Connect the device through the socket. This will block until it succeeds or
                // throws an exception
                btSocket.connect();
                Log.d(TAG, "Connected to: " + btDevice.getName() + " " + btDevice.getAddress());
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                getResources().getString(R.string.toast_connectedTo) +
                                        " " + btDevice.getName() + " " + btDevice.getAddress(),
                                Toast.LENGTH_LONG).show();

                    }
                });
            } catch (IOException connectException) {
                // Unable to connect
                Log.d(TAG, "Unable to connect to the iTPMSystem...");
                try {
                    btSocket.close();
                } catch (IOException closeException) {
                    Log.d(TAG,"Unable to close socket during connection failure");
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            ConnectedThread btConnectedThread = new ConnectedThread(btSocket);
            btConnectedThread.start();
        }

        // Cancel an in-progress connection, and close the socket
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Unable to close Bluetooth socket");
            }
        }
    }

    // Connected bluetooth thread
    private class ConnectedThread extends Thread {
        private final InputStream btInStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpInput = null;

            // Get the input stream, using temp objects because member streams are final
            try {
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "IO Exception getting input stream");
            }
            btInStream = tmpInput;
        }

        public void run() {
            byte[] buffer = new byte[256];  // Buffer store for the stream
            int bytes; // Bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = btInStream.read(buffer); // Get number of bytes and message in "buffer"
                    sensorMessages.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget(); // Send to message queue Handler
                } catch (IOException e) {
                    Log.d(TAG, "IO Exception while reading stream");
                    btConnectThread.cancel();
                    break;
                }
            }
        }
    }

    // Read raw text file
    private static String readRawTextFile(Context ctx, int resId)
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
    private void Notify(String notificationMessage)
    {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, 0);

        String alertURI = sharedPrefs.getString("prefsound","content://settings/system/notification_sound");
        Uri soundURI = Uri.parse(alertURI);
        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getResources().getString(R.string.app_shortName))
                .setContentText(notificationMessage)
                .setSmallIcon(R.drawable.notification_icon)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_MAX)
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
        if (sharedPrefs.getBoolean("prefNotificationVibrate", false)) {
            notification.defaults|= Notification.DEFAULT_VIBRATE;
        }
        // Make alert repeat until read
        notification.flags |= Notification.FLAG_INSISTENT;

        // Send notification
        notificationManager.notify(0, notification);
    }

    // Listens for light sensor events
    private final SensorEventListener lightSensorEventListener
            = new SensorEventListener(){

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (sharedPrefs.getBoolean("prefAutoNightMode", false)) {
                int delay = (Integer.parseInt(sharedPrefs.getString("prefAutoNightModeDelay", "30")) * 1000);
                if(event.sensor.getType()==Sensor.TYPE_LIGHT){
                    float currentReading = event.values[0];
                    double darkThreshold = 20.0;  // Light level to determine darkness
                    if (currentReading < darkThreshold){
                        lightTimer = 0;
                        if (darkTimer == 0){
                            darkTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - darkTimer);
                            if ((duration >= delay) && (!itsDark)){
                                itsDark = true;
                                Log.d(TAG,"Its dark");
                                // Redraw Screen
                                final LinearLayout  layoutFront = (LinearLayout) findViewById(R.id.layoutFront);
                                final LinearLayout  layoutRear = (LinearLayout) findViewById(R.id.layoutRear);

                                root.setBackgroundColor(getResources().getColor(android.R.color.black));

                                if (frontStatus > 0) {
                                    layoutFront.setBackground(redBackgroundDark);
                                } else {
                                    layoutFront.setBackground(backgroundDark);
                                }
                                if (rearStatus > 0) {
                                    layoutRear.setBackground(redBackgroundDark);
                                } else {
                                    layoutRear.setBackground(backgroundDark);
                                }
                                txtOutput.setBackground(txtOutBackgroundDark);
                                txtFrontPressure.setTextColor(getResources().getColor(android.R.color.white));
                                txtFrontTemperature.setTextColor(getResources().getColor(android.R.color.white));
                                txtFrontVoltage.setTextColor(getResources().getColor(android.R.color.white));
                                txtRearPressure.setTextColor(getResources().getColor(android.R.color.white));
                                txtRearTemperature.setTextColor(getResources().getColor(android.R.color.white));
                                txtRearVoltage.setTextColor(getResources().getColor(android.R.color.white));
                                txtOutput.setTextColor(getResources().getColor(android.R.color.white));
                            }
                        }
                    } else {
                        darkTimer = 0;
                        if (lightTimer == 0){
                            lightTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - lightTimer);
                            if ((duration >= delay) && (itsDark)){
                                itsDark = false;
                                Log.d(TAG,"Its light");
                                // Redraw Screen
                                final LinearLayout  layoutFront = (LinearLayout) findViewById(R.id.layoutFront);
                                final LinearLayout  layoutRear = (LinearLayout) findViewById(R.id.layoutRear);

                                root.setBackgroundColor(getResources().getColor(android.R.color.white));
                                if (frontStatus > 0) {
                                    layoutFront.setBackground(redBackground);
                                } else {
                                    layoutFront.setBackground(background);
                                }
                                if (rearStatus > 0) {
                                    layoutRear.setBackground(redBackground);
                                } else {
                                    layoutRear.setBackground(background);
                                }
                                txtOutput.setBackground(txtOutBackground);
                                txtFrontPressure.setTextColor(getResources().getColor(android.R.color.black));
                                txtFrontTemperature.setTextColor(getResources().getColor(android.R.color.black));
                                txtFrontVoltage.setTextColor(getResources().getColor(android.R.color.black));
                                txtRearPressure.setTextColor(getResources().getColor(android.R.color.black));
                                txtRearTemperature.setTextColor(getResources().getColor(android.R.color.black));
                                txtRearVoltage.setTextColor(getResources().getColor(android.R.color.black));
                                txtOutput.setTextColor(getResources().getColor(android.R.color.black));
                            }
                        }
                    }
                }
            }
        }
    };
}