package org.thecongers.itpms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;


public class sensorIdDatabase extends SQLiteAssetHelper {
    public static final String TAG = "iTPMS_DB";
    private static final String DATABASE_NAME = "discoveredSensorID.db";
    private static final int DATABASE_VERSION = 1;

    public sensorIdDatabase(Context context) {

        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setForcedUpgrade();

    }

    public Cursor getAllSensorIDs() {

        Log.d(TAG, "Returning sensor IDs from DB");
        SQLiteDatabase db = this.getReadableDatabase();
        String sqlTables = "sensorIDs";
        Cursor c = db.query(sqlTables, new String[] {"_id", "sensorID"}, null, null, null, null, null);
        if (c != null) {
            c.moveToFirst();
        }

        return c;
    }

    // Check if sensor ID is in the DB
    public boolean sensorIdExists(String id) {

        Log.d(TAG, "Checking for sensor ID in DB");
        SQLiteDatabase db = this.getReadableDatabase();
        String sqlTables = "sensorIDs";

        Cursor c = db.query(sqlTables, new String[] {"_id", "sensorID"}, "sensorID=?", new String[] { id }, null, null, null);
        if (c != null) {
            Log.d(TAG, "sensorIdExists: cursor is not NULL");
            c.moveToFirst();
            c.close();
            db.close();
            return true;
        }

        Log.d(TAG, "sensorIdExists: cursor is NULL");
        db.close();
        return false;
    }

    // Add sensor ID to database
    void addID(String id) {

        Log.d(TAG, "Adding sensor ID to DB");
        SQLiteDatabase db = this.getWritableDatabase();
        String sqlTables = "sensorIDs";

        ContentValues values = new ContentValues();
        values.put("sensorID", id);

        // Inserting Row
        db.insert(sqlTables, null, values);
        db.close();

    }

    // Purge IDs
    void purgeID() {
        Log.d(TAG, "Purging all IDs from DB");
        SQLiteDatabase db = this.getWritableDatabase();
        String sqlTables = "sensorIDs";

        // Delete rows
        db.delete(sqlTables, null, null);
        db.execSQL("VACUUM");
        // Closing database connection
        db.close();

    }
}
