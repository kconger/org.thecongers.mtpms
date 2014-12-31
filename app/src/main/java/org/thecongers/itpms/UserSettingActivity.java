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
    protected static void setListPreferenceData(ListPreference lp) {
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