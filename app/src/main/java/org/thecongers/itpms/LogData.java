package org.thecongers.itpms;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


public class LogData {
    static PrintWriter outFile = null;
    public static final String TAG = "iTPMS_Log";

    private static void initialize()
    {
        try {
            File root = new File(Environment.getExternalStorageDirectory() + "/itpms/");
            if(!root.exists()) {
                root.mkdirs();
            } else {
                Log.d(TAG, "Directory already exists");
            }
            if( root.canWrite() )
            {
                Log.d(TAG,"Initialize Logging");
                // Get current time in UTC
                Calendar cal = Calendar.getInstance();
                Date date = cal.getTime();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                String curdatetime = formatter.format(date);

                File gpxfile = new File( root, "iTPMS-" + curdatetime + ".csv" );
                FileWriter gpxwriter = new FileWriter( gpxfile );
                outFile = new PrintWriter( gpxwriter );
                outFile.write( "\nDate,Wheel,Pressure(psi),Temperature(Celsius),Voltage\n" );
            }
        } catch (IOException e) {
            Log.d(TAG, "Could not write to file: " + e.getMessage());
        }
    }

    public static void write( String wheel, String pressure, String temperature, String voltage )
    {
        if( outFile == null )
            initialize();

        // Get current time in UTC
        Calendar cal = Calendar.getInstance();
        Date date = cal.getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String curdatetime = formatter.format(date);

        outFile.write( curdatetime + "," + wheel + "," + pressure + "," + temperature + "," + voltage + "\n" );
        outFile.flush();
    }

    public static void shutdown()
    {
        if( outFile != null )
            outFile.close();
    }
}
