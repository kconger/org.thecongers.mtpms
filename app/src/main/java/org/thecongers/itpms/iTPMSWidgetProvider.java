package org.thecongers.itpms;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class iTPMSWidgetProvider extends AppWidgetProvider {
	public static final String TAG = "iTPMS";
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		try {
			updateWidgetContent(context, appWidgetManager);
		} catch (Exception e) {
	        Log.d(TAG, "Failed", e);
	    }
	}
	public static void updateWidgetContent(Context context, AppWidgetManager appWidgetManager) {
		RemoteViews remoteView = new RemoteViews(context.getPackageName(), R.layout.itpms_appwidget_layout);
		// TODO: DO something
		
		// Launch app when widget is pressed
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                intent, 0);

        remoteView.setOnClickPendingIntent(R.id.full_widget, pendingIntent);
	 
		ComponentName iTPMSWidget = new ComponentName(context, iTPMSWidgetProvider.class);
		appWidgetManager.updateAppWidget(iTPMSWidget, remoteView);
		
	}

}
