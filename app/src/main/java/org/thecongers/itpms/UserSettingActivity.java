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

            final ListPreference listFrontIDPreference = (ListPreference) findPreference("prefFrontID");
            final ListPreference listRearIDPreference = (ListPreference) findPreference("prefRearID");

            // THIS IS REQUIRED IF YOU DON'T HAVE 'entries' and 'entryValues' in your XML
            setListPreferenceData(listFrontIDPreference);
            setListPreferenceData(listRearIDPreference);
        }
    }

    protected static void setListPreferenceData(ListPreference lp) {

        sensorIdDatabase sensorDB;
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
                entryValues[position] = String.valueOf(position);
                sensorIDs.moveToNext();
                position = position + 1;

            }
            sensorIDs.close();
        }

        //CharSequence[] entries = { MainActivity.frontID, MainActivity.rearID };
        //CharSequence[] entryValues = {"1" , "2"};
        lp.setEntries(entries);
        //lp.setDefaultValue("1");
        lp.setEntryValues(entryValues);
    }

}
