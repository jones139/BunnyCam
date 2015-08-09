package uk.org.maps3.bunnycam;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity
{
    static final String TAG = "MainActivity";
    SdServer mSdServer;
    boolean mBound = false;
    private Menu mOptionsMenu;
    private Intent sdServerIntent;
    private String versionName = "unknown";
    private Timer mUiTimer;

    final Handler serverStatusHandler = new Handler();
    Messenger messenger = new Messenger(new ResponseHandler());

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Initialise the User Interface
        setContentView(R.layout.activity_main);

	/* Force display of overflow menu - from stackoverflow
	 * "how to force use of..."
	 */
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField =
                    ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField!=null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config,false);
            }
        } catch (Exception e) {
            Log.v(TAG,"menubar fiddle exception: "+e.toString());
        }


        Button getImageButton = (Button) findViewById(R.id.getImageButton);
        getImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "getImageButton.onClick()");
                if (mSdServer != null) {
                    mSdServer.takePicture();
                }
            }
        });

        Button viewImagesButton = (Button) findViewById(R.id.viewImagesButton);
        viewImagesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                startActivity(intent);
            }
        });

        final Button findCameraButton = (Button) findViewById(R.id.findCameraButton);
        findCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "findCameraButton.onClickListener()");
                findCameraButton.setText("Searching for Camera....");
                findCameraButton.setEnabled(false);
                IpCamController ipcc = new IpCamController("null", "guest", "guest", 0, new IpCamListener() {
                    @Override
                    public void onGotImage(byte[] img, String msg) {
                        Log.v(TAG, "onGotImage() - msg is : " + msg);
                        makeToast("Camera found at Address " + msg);
                        findCameraButton.setText("Find Camera");
                        findCameraButton.setEnabled(true);
                        SharedPreferences SP = PreferenceManager
                                .getDefaultSharedPreferences(getBaseContext());
                        SharedPreferences.Editor editor = SP.edit();
                        //mIpCameraUrl = SP.getString("ipCameraUrl", "http://192.168.1.27/snapshot.cgi");
                        editor.putString("ipCameraUrl", "http://" + msg + "/snapshot.cgi");
                        editor.apply();
                        editor.commit();
                        mSdServer.updatePrefs();
                    }
                });
                ipcc.findCamera();
            }
        });

        Button b;
        b = (Button) findViewById(R.id.leftButton);
        b.setOnClickListener(new CameraPanTiltOnClickListener());
        b = (Button) findViewById(R.id.upButton);
        b.setOnClickListener(new CameraPanTiltOnClickListener());
        b = (Button) findViewById(R.id.downButton);
        b.setOnClickListener(new CameraPanTiltOnClickListener());
        b = (Button) findViewById(R.id.rightButton);
        b.setOnClickListener(new CameraPanTiltOnClickListener());

        mUiTimer = new Timer();
    }

    /**
     * Create Action Bar
     */
    @Override
    public boolean onCreateOptionsMenu (Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        mOptionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "Option " + item.getItemId()+" selected");
        switch (item.getItemId()) {
            case R.id.action_start_stop:
                // Respond to the start/stop server menu item.
                Log.v(TAG,"action_sart_stop");
                if (mBound) {
                    Log.v(TAG,"Stopping Server");
                    unbindFromServer();
                    stopServer();
                } else {
                    Log.v(TAG,"Starting Server");
                    startServer();
                    // and bind to it so we can see its data
                    bindToServer();
                }
                return true;
            case R.id.action_settings:
                Log.v(TAG,"action_settings");
                try {
                    Intent prefsIntent = new Intent(
                            MainActivity.this,
                            PrefActivity.class);
                    this.startActivity(prefsIntent);
                } catch (Exception ex) {
                    Log.v(TAG,"exception starting settings activity "+ex.toString());
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG,"onStart()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        int uiTimerPeriod = SP.getInt("uiTimerPeriod", 5000);
        // start timer to refresh user interface every 5 seconds
        mUiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateUI();
            }
        }, 0, uiTimerPeriod);



        // From http://stackoverflow.com/questions/4471025/
        //         how-can-you-get-the-manifest-version-number-
        //         from-the-apps-layout-xml-variable
        final PackageManager packageManager = getPackageManager();
        if (packageManager != null) {
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
                versionName = packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.v(TAG,"failed to find versionName");
                versionName = null;
            }
        }
        Log.v(TAG,"App Version = "+versionName);

        if (!isServerRunning()) {
            Log.v(TAG,"Server not Running - Starting Server");
            startServer();
        } else {
            Log.v(TAG,"Server Already Running OK");
        }
        // and bind to it so we can see its data
        bindToServer();

    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindFromServer();
        mUiTimer.purge();
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SdServer.SdBinder binder = (SdServer.SdBinder) service;
            mSdServer = binder.getService();
            mBound = true;
            if (mSdServer!=null) {
                Log.v(TAG,"onServiceConnected() - Asking server to update its settings");
                mSdServer.updatePrefs();
            }
            else {
                Log.v(TAG,"onServiceConnected() - mSdServer is null - this is wrong!");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.v(TAG,"onServiceDisonnected()");
            mBound = false;
        }
    };


    /**
     * bind to an already running server.
     */
    private void bindToServer() {
        Log.v(TAG,"bindToServer() - binding to SdServer");
        Intent intent = new Intent(this,SdServer.class);
        bindService(intent,mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * unbind from server
     */
    private void unbindFromServer() {
        // unbind this activity from the service if it is bound.
        if (mBound) {
            Log.v(TAG, "unbindFromServer() - unbinding");
            try {
                unbindService(mConnection);
                mBound = false;
            } catch (Exception ex) {
                Log.e(TAG,"unbindFromServer() - error unbinding service - "+ex.toString());
            }
        } else {
            Log.v(TAG,"unbindFromServer() - not bound to server - ignoring");
        }
    }

    /**
     * Start the SdServer service
     */
    private void startServer() {
        // Start the server
        sdServerIntent = new Intent(MainActivity.this,SdServer.class);
        sdServerIntent.setData(Uri.parse("Start"));
        getApplicationContext().startService(sdServerIntent);

        // Change the action bar icon to show the option to stop the service.
        if (mOptionsMenu!=null) {
            Log.v(TAG,"Changing menu icons");
            MenuItem menuItem = mOptionsMenu.findItem(R.id.action_start_stop);
            menuItem.setIcon(R.drawable.stop_server);
            menuItem.setTitle("Stop Server");
        } else {
            Log.v(TAG,"mOptionsMenu is null - not changing icons!");
        }
    }

    /**
     * Stop the SdServer service
     */
    private void stopServer() {
        Log.v(TAG,"stopping Server...");

        // then send an Intent to stop the service.
        sdServerIntent = new Intent(MainActivity.this,SdServer.class);
        sdServerIntent.setData(Uri.parse("Stop"));
        getApplicationContext().stopService(sdServerIntent);

        // Change the action bar icon to show the option to start the service.
        if (mOptionsMenu!=null) {
            Log.v(TAG,"Changing action bar icons");
            mOptionsMenu.findItem(R.id.action_start_stop).setIcon(R.drawable.start_server);
            mOptionsMenu.findItem(R.id.action_start_stop).setTitle("Start Server");
        } else {
            Log.v(TAG,"mOptionsMenu is null, not changing icons!");
        }

    }

    /**
     * Based on http://stackoverflow.com/questions/7440473/android-how-to-check-if-the-intent-service-is-still-running-or-has-stopped-running
     */
    public boolean isServerRunning() {
        //Log.v(TAG,"isServerRunning()................");
        ActivityManager manager =
                (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            //Log.v(TAG,"Service: "+service.service.getClassName());
            if ("uk.org.maps3.bunnycam.SdServer"
                    .equals(service.service.getClassName())) {
                //Log.v(TAG,"Yes!");
                return true;
            }
        }
        //Log.v(TAG,"No!");
        return false;
    }



    /*
     * updateServerStatus - called by the uiTimer timer periodically.
     * requests the ui to be updated by calling serverStatusRunnable.
     */
    private void updateUI() {
        //Log.v(TAG,"updateUI");
        serverStatusHandler.post(uiRunnable);
    }

    /*
     * serverStatusRunnable - called by updateServerStatus - updates the
     * user interface to reflect the current status received from the server.
     */
    final Runnable uiRunnable = new Runnable() {
        public void run() {
            Log.v(TAG, "uiRunnable()");
            if (mSdServer != null) {
                if (mSdServer.mLatestImage != null) {
                    ImageView imageView = (ImageView) findViewById(R.id.imageView);
                    Bitmap bm = BitmapFactory.decodeByteArray(mSdServer.mLatestImage, 0, mSdServer.mLatestImage.length);
                    imageView.setImageBitmap(bm);
                }
            }
        }
    };


    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void makeToast(String msg) {
        Log.d(TAG, "makeToast - msg=" + msg);
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    class CameraPanTiltOnClickListener implements View.OnClickListener, IpCamListener {
        @Override
        public void onClick(View v) {
            //Log.v(TAG,"CameraPanTitlOnClicListener.onClick()");
            switch (v.getId()) {
                case R.id.leftButton:
                    Log.v(TAG, "LeftButton");
                    break;
                case R.id.upButton:
                    Log.v(TAG, "UpButton");
                    break;
                case R.id.downButton:
                    Log.v(TAG, "DownButton");
                    break;
                case R.id.rightButton:
                    Log.v(TAG, "RightButton");
                    break;
                default:
                    Log.w(TAG, "Unknown Button Clicked!!!!");
            }
        }

        @Override
        public void onGotImage(byte[] img, String msg) {
            Log.v(TAG, "cameraPanTiltOnClickListener.onGotImage()");
        }
    }

    class ResponseHandler extends Handler {
        @Override public void handleMessage(Message message) {
            Log.v(TAG,"Message="+message.toString());
        }
    }


}
