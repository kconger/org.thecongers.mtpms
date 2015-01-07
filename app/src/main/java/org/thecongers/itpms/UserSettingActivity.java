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

import android.database.Cursor;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

public class UserSettingActivity extends PreferenceActivity {
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new UserSettingActivityFragment()).commit();

    }
	
	public static class UserSettingActivityFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.user_settings);

            // Dynamically populate sensorID lists
            final ListPreference listFrontIDPreference = (ListPreference) findPreference("prefFrontID");
            final ListPreference listRearIDPreference = (ListPreference) findPreference("prefRearID");
            setListPreferenceData(listFrontIDPreference);
            setListPreferenceData(listRearIDPreference);
        }
    }

    // Dynamically populate the sensor ID list preferences
    private static void setListPreferenceData(ListPreference lp) {
        SensorIdDatabase sensorDB;
        sensorDB = MainActivity.sensorDB;

        CharSequence[] entries = null;
        CharSequence[] entryValues = null;

        Cursor sensorIDs = sensorDB.getAllSensorIDs();
        if (sensorIDs != null) {
            entries = new CharSequence[sensorIDs.getCount()];
            entryValues = new CharSequence[sensorIDs.getCount()];
            sensorIDs.moveToFirst();
            int position = 0;
            while (!sensorIDs.isAfterLast()) {
                entries[position] = sensorIDs.getString(1);
                entryValues[position] = sensorIDs.getString(1);
                sensorIDs.moveToNext();
                position = position + 1;
            }
            sensorIDs.close();
        }

        lp.setEntries(entries);
        lp.setEntryValues(entryValues);
    }
}