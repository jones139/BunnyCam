package uk.org.maps3.bunnycam;

/**
 * Created by graham on 30/07/15.
 */
/*
  Pebble_sd - a simple accelerometer based seizure detector that runs on a
  Pebble smart watch (http://getpebble.com).

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015.

  This file is part of pebble_sd.

  Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * Based on example at:
 * http://stackoverflow.com/questions/14309256/using-nanohttpd-in-android
 * and
 * http://developer.android.com/guide/components/services.html#ExtendingService
 */
public class SdServerTemplate extends Service
{
    // Notification ID
    private int NOTIFICATION_ID = 1;
    private NotificationManager mNM;
    private final static String TAG = "SdServer";
    private Looper mServiceLooper;
    private HandlerThread thread;
    private PowerManager.WakeLock mWakeLock = null;

    private final IBinder mBinder = new SdBinder();

    /**
     * class to handle binding the MainApp activity to this service
     * so it can access sdData.
     */
    public class SdBinder extends Binder {
        SdServerTemplate getService() {
            Log.v(TAG, "SdServer.getService()");
            return SdServerTemplate.this;
        }
    }

    /**
     * Constructor for SdServer class - does not do much!
     */
    public SdServerTemplate() {
        super();
        Log.v(TAG, "SdServer Created");
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG,"sdServer.onBind()");
        return mBinder;
    }



    /**
     * onCreate() - called when services is created.  Starts message
     * handler process to listen for messages from other processes.
     */
    @Override
    public void onCreate() {
        Log.v(TAG,"onCreate()");
    }

    /**
     * onStartCommand - start the web server and the message loop for
     * communications with other processes.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG,"onStartCommand() - SdServer service starting");

        // Update preferences.
        Log.v(TAG,"onStartCommand() - calling updatePrefs()");
        updatePrefs();

        // Display a notification icon in the status bar of the phone to
        // show the service is running.
        Log.v(TAG,"showing Notification");
        showNotification();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG,"onDestroy(): SdServer Service stopping");
        // Cancel the notification.
        Log.v(TAG,"onDestroy(): cancelling notification");
        mNM.cancel(NOTIFICATION_ID);
        // stop this service.
        Log.v(TAG,"onDestroy(): calling stopSelf()");
        stopSelf();
    }



    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        Log.v(TAG,"showNotification()");
        CharSequence text = "OpenSeizureDetector Server Running";
        Notification notification =
                new Notification(R.drawable.star_of_life_24x24, text,
                        System.currentTimeMillis());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        notification.setLatestEventInfo(this, "OpenSeizureDetector Server",
                text, contentIntent);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        mNM = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        mNM.notify(NOTIFICATION_ID, notification);
    }



    /**
     * updatePrefs() - update basic settings from the SharedPreferences
     * - defined in res/xml/prefs.xml
     */
    public void updatePrefs() {
        Log.v(TAG,"updatePrefs()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
    }


}
