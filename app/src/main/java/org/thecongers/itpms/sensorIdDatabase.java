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

package org.thecongers.itpms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;


public class SensorIdDatabase extends SQLiteAssetHelper {
    public static final String TAG = "iTPMS_DB";
    private static final String DATABASE_NAME = "discoveredSensorID.db";
    private static final int DATABASE_VERSION = 1;

    public SensorIdDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setForcedUpgrade();
    }

    // Return all sensor IDs from the database
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
            c.moveToFirst();
            int count = c.getCount();
            if(count == 0){
                Log.d(TAG, "sensorIdExists: FALSE");
                c.close();
                db.close();
                return false;
            } else {
                Log.d(TAG, "sensorIdExists: TRUE");
                c.close();
                db.close();
                return true;
            }
        }
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
        db.close();
    }
}
