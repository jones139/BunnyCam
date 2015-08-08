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
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import fi.iki.elonen.NanoHTTPD;

/**
 * Based on example at:
 * http://stackoverflow.com/questions/14309256/using-nanohttpd-in-android
 * and
 * http://developer.android.com/guide/components/services.html#ExtendingService
 */
public class SdServer extends Service implements IpCamListener {
    private static final int CAMERA_ID = 0;  // Use the device's primary camera.
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    // Notification ID
    private int NOTIFICATION_ID = 1;
    private NotificationManager mNM;
    private final static String TAG = "SdServer";
    private Looper mServiceLooper;
    private HandlerThread thread;
    private PowerManager.WakeLock mWakeLock = null;
    private Camera mCamera;
    private Timer mPictureTimer = null;
    private boolean mCameraReady = false;
    private int mImageCapturePeriod;
    private boolean mUploadImages;
    private String mServerUrl;
    private WebServer mWebServer = null;
    private String mLatestImageFname = null;
    private byte[] mLatestImage = null;
    private boolean mUseIpCamera = false;
    private String mIpCameraUrl = "";
    private String mIpCameraUname = "";
    private String mIpCameraPasswd = "";
    private IpCamController mIpCamController = null;

    private Handler handler;

    private final IBinder mBinder = new SdBinder();

    /**
     * class to handle binding the MainApp activity to this service
     * so it can access sdData.
     */
    public class SdBinder extends Binder {
        SdServer getService() {
            Log.v(TAG, "SdServer.getService()");
            return SdServer.this;
        }
    }

    /**
     * Constructor for SdServer class - does not do much!
     */
    public SdServer() {
        super();
        Log.v(TAG, "SdServer Created");
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "sdServer.onBind()");
        return mBinder;
    }


    /**
     * onCreate() - called when services is created.  Starts message
     * handler process to listen for messages from other processes.
     */
    @Override
    public void onCreate() {
        // Handler will get associated with the current thread,
        // which is the main thread.
        handler = new Handler();
        super.onCreate();
        Log.v(TAG, "onCreate()");

        // Connect to the Camera
        mCamera = null;
        try {
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
           // int nCameras = Camera.getNumberOfCameras();
            //Log.v(TAG,"onCreate(): nCameras = "+nCameras);
            //mCamera = Camera.open(CAMERA_ID);
            mCamera = Camera.open();  // Just use default camera for compatibility with SDK8.
            Camera.Parameters params = mCamera.getParameters();
            Camera.Size previewSize = params.getPreviewSize();
            Log.v(TAG,"previewSize ="+previewSize.toString()+":- "+previewSize.width+","+previewSize.height);
            // Create a dummy surfaceView for the camera preview.
            SurfaceView sv = new SurfaceView(getApplicationContext());
            sv.getHolder().setFixedSize(previewSize.width,previewSize.height);
            Log.v(TAG,"Setting preview display - size="+sv.getHolder().toString());
            mCamera.setPreviewDisplay(sv.getHolder());
            mCameraReady = true;
        } catch (Exception e) {
            Log.e(TAG, "failed to open Camera");
            mCameraReady = false;
            e.printStackTrace();
        }

        //takePicture();

        // Create a wake lock, but don't use it until the service is started.
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");

    }

    /*
    Taken from http://stackoverflow.com/questions/18948251/not-able-to-call-runonuithread-in-a-thread-from-inside-of-a-service
     */
    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }


    private void takePicture() {
        Log.v(TAG, "takePicture()");
        if (mUseIpCamera) {
            Log.v(TAG, "takePicture() = Using IP Camera");
            mIpCamController = new IpCamController(mIpCameraUrl, mIpCameraUname, mIpCameraPasswd, 0, this);
            byte[] data = mIpCamController.getImage();
            Log.v(TAG, "takePicture - calling picturteCallBack()");
            saveImage(data);
        } else {
            if (mCamera != null) {
                // Only try to take a picture if we have finished taking the previous one.
                if (mCameraReady) {
                    mCamera.startPreview();
                    mCameraReady = false;
                    try {
                        Log.v(TAG, "Taking Picture");
                        mCamera.takePicture(null, null, pictureCallBack);
                    } catch (Exception e) {
                        Log.v(TAG, "Error Taking Picture");
                        e.printStackTrace();
                    }
                } else {
                    Log.v(TAG, "Camera Not Ready - waiting.");
                }
            } else {
                Log.v(TAG, "mCamera is Null!!!");
            }
        }
    }


    private Camera.PictureCallback pictureCallBack = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.v(TAG, "onPictureTaken()");
            mCameraReady = true;
            saveImage(data);
        }
    };

    /**
     * Save the byte array data as an image.
     *
     * @param data
     * @return
     */
    private int saveImage(byte[] data) {
        Log.v(TAG, "saveImage()");
        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions: ");
            return -1;
        } else {

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
                return -1;
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
                return -1;
            }
            mLatestImageFname = pictureFile.getName();
            mLatestImage = data;

            // Update the media library so the new image appears in gallery.
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(Uri.fromFile(pictureFile));
            sendBroadcast(intent);

            if (mUploadImages) {
                Log.v(TAG, "Uploading Image to Server...");
                if (pictureFile != null)
                    uploadImage(pictureFile.getName(), data);
                else
                    uploadImage("unknown", data);
            }
        }
        Log.v(TAG, "saveImage complete");
        return 0;

    }

    /**
     * onGotImage - called by IpCamController when an image from the IP Camera is received.
     *
     * @param img
     */
    public void onGotImage(byte[] img) {
        Log.v(TAG, "onGotImage");
    }


    /* uploadImage - based on http://androidexample.com/Upload_File_To_Server_-_Android_Example/index.php?view=article_discription&aid=83&aaid=106
     */
    private boolean uploadImage(String pictureFile, byte[] data) {
        Log.v(TAG, "uploadImage");
        ImageUploader imageUploader = new ImageUploader();
        Object[] params = {mLatestImageFname, mServerUrl, mLatestImage};
        imageUploader.execute(params);
        return true;
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
        //        Environment.DIRECTORY_PICTURES), "bunnyCam");
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "bunnyCam");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public File getDataStorageDir() {
        // Get the directory for the user's public pictures directory.
        File file =
                new File(Environment.getExternalStorageDirectory()
                        ,"BunnyCam");
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        return file;
    }


    /**
     * onStartCommand - start the web server and the message loop for
     * communications with other processes.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand() - intent Action = " + intent.getAction() + " intent data = " + intent.getDataString());
        // SMSReceiver may send us an 'Upload' intent to ask us to upload the latest image.  If not, we assume that this intent
        // is asking us to start the service.
        if (intent.getDataString().equals("Upload")) {
            Log.v(TAG, "onStartCommand() - Upload intent received");
            uploadImage(mLatestImageFname, mLatestImage);
            return 0;
        }

        Log.v(TAG, "onStartCommand() - SdServer service starting");


        // Update preferences.
        Log.v(TAG, "onStartCommand() - calling updatePrefs()");
        updatePrefs();

        // Display a notification icon in the status bar of the phone to
        // show the service is running.
        Log.v(TAG, "showing Notification");
        showNotification();


        // Start the web server
        startWebServer();

        // Apply the wake-lock to prevent CPU sleeping (very battery intensive!)
        if (mWakeLock!=null) {
            mWakeLock.acquire();
            Log.v(TAG,"Applied Wake Lock to prevent device sleeping");
        } else {
            Log.d(TAG,"mmm...mWakeLock is null, so not aquiring lock.  This shouldn't happen!");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy(): SdServer Service stopping");
        // Stop camera timer
        mPictureTimer.cancel();

        try {
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
            Log.v(TAG, "onDestroy() - released camera.");
        } catch (Exception e) {
            Log.v(TAG, "onDestroy() - Error releasing camera..");
            e.printStackTrace();

        }


        // Cancel the notification.
        Log.v(TAG, "onDestroy(): cancelling notification");
        mNM.cancel(NOTIFICATION_ID);
        // stop this service.
        Log.v(TAG, "onDestroy(): calling stopSelf()");
        stopSelf();
    }


    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        Log.v(TAG, "showNotification()");
        CharSequence text = "BunnyCam";
        Notification notification =
                new Notification(R.drawable.rabbit_silhouette, text,
                        System.currentTimeMillis());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        notification.setLatestEventInfo(this, "BunnyCam",
                text, contentIntent);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        mNM = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNM.notify(NOTIFICATION_ID, notification);
    }


    /**
     * updatePrefs() - update basic settings from the SharedPreferences
     * Modifies timers to reflect settings in case they have changed.
     * - defined in res/xml/prefs.xml
     */
    public void updatePrefs() {
        Log.v(TAG, "updatePrefs()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        String prefStr = SP.getString("imageCapturePeriod", "60");
        mImageCapturePeriod = (short) Integer.parseInt(prefStr) * 1000;
        Log.v(TAG, "updatePrefs() mImageCapturePeriod = " + mImageCapturePeriod);

        mUploadImages = SP.getBoolean("uploadImages", false);
        mServerUrl = SP.getString("serverUrl", "http://bunnycam.webhop.info/upload.php");

        mUseIpCamera = SP.getBoolean("useIpCamera", false);
        mIpCameraUrl = SP.getString("ipCameraUrl", "http://192.168.1.27/getsnap.cgi");
        mIpCameraUname = SP.getString("ipCameraUname", "guest");
        mIpCameraPasswd = SP.getString("ipCameraPasswd", "guest");

        // start timer to take pictures at regular intervals.
        if (mPictureTimer != null) {
            mPictureTimer.cancel();
            mPictureTimer = null;
        }
        mPictureTimer = new Timer();
        mPictureTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                takePicture();
            }
        }, 0, mImageCapturePeriod);

    }

    /**
     * Start the web server (on port 8080)
     */
    protected void startWebServer() {
        Log.v(TAG,"startWebServer()");
        if (mWebServer == null) {
            mWebServer = new WebServer();
            try {
                mWebServer.start();
            } catch(IOException ioe) {
                Log.w(TAG, "startWebServer(): Error: "+ioe.toString());
            }
            Log.w(TAG, "startWebServer(): Web server initialized.");
        } else {
            Log.v(TAG, "startWebServer(): server already running???");
        }
    }

    /**
     * Stop the web server - FIXME - doesn't seem to do anything!
     */
    protected void stopWebServer() {
        Log.v(TAG,"stopWebServer()");
        if (mWebServer!=null) {
            mWebServer.stop();
            if (mWebServer.isAlive()) {
                Log.v(TAG,"stopWebServer() - server still alive???");
            } else {
                Log.v(TAG,"stopWebServer() - server died ok");
            }
            mWebServer = null;
        }
    }


    /**
     * Class describing the seizure detector web server - appears on port
     * 8080.
     */
    private class WebServer extends NanoHTTPD {
        private String TAG = "WebServer";

        public WebServer() {
            // Set the port to listen on (8080)
            super(8080);
        }

        @Override
        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            Log.v(TAG, "WebServer.serve() - uri=" + uri + " Method=" + method.toString());
            String answer = "Error - you should not see this message! - Something wrong in WebServer.serve()";

            Iterator it = parameters.keySet().iterator();
            while (it.hasNext()) {
                Object key = it.next();
                Object value = parameters.get(key);
                //Log.v(TAG,"Request parameters - key="+key+" value="+value);
            }

            if (uri.equals("/")) uri = "/index.html";
            switch (uri) {
                case "/data":
                    //Log.v(TAG,"WebServer.serve() - Returning data");
                    try {
                        answer = "data";
                    } catch (Exception ex) {
                        Log.v(TAG, "Error Creating Data Object - " + ex.toString());
                        answer = "Error Creating Data Object";
                    }
                    break;

                case "/settings":
                    //Log.v(TAG,"WebServer.serve() - Returning settings");
                    try {
                        answer = "settings";
                    } catch (Exception ex) {
                        Log.v(TAG, "Error Creating Data Object - " + ex.toString());
                        answer = "Error Creating Data Object";
                    }
                    break;
                case "/latest":
                    try {
                        return serveImage("");
                    } catch (Exception ex) {
                        Log.v(TAG, "Error Creating Data Object - " + ex.toString());
                        answer = "Error Creating Data Object";
                    }
                    break;
                default:
                    if (uri.startsWith("/index.html") ||
                            uri.startsWith("/favicon.ico") ||
                            uri.startsWith("/js/") ||
                            uri.startsWith("/css/") ||
                            uri.startsWith("/img/")) {
                        //Log.v(TAG,"Serving File");
                        return serveFile(uri);
                    } else if (uri.startsWith("/logs")) {
                        Log.v(TAG, "WebServer.serve() - serving data logs - uri=" + uri);
                        NanoHTTPD.Response resp = serveLogFile(uri);
                        Log.v(TAG, "WebServer.serve() - response = " + resp.toString());
                        return resp;
                    } else {
                        Log.v(TAG, "WebServer.serve() - Unknown uri -" +
                                uri);
                        answer = "Unknown URI: ";
                    }
            }

            return new NanoHTTPD.Response(answer);
        }

        /**
         * Return an image from the bunnyCam images folder
         */
        NanoHTTPD.Response serveImage(String uri) {
            NanoHTTPD.Response res;
            InputStream ip = new ByteArrayInputStream(mLatestImage);
            try {
                String mimeStr = "image/jpeg";
                res = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,
                        mimeStr, ip);
                res.addHeader("Content-Length", "" + mLatestImage.length);
            } catch (Exception ex) {
                Log.v(TAG, "serveFile(): Error Serving IMage - " + ex.toString());
                res = new NanoHTTPD.Response("serveImage(): Error Opening file " + uri);
            }
            return (res);
        }


        /**
         * Return a file from the external storage folder
         */
        NanoHTTPD.Response serveLogFile(String uri) {
            NanoHTTPD.Response res;
            InputStream ip = null;
            String uripart;
            Log.v(TAG, "serveLogFile(" + uri + ")");
            try {
                if (ip != null) ip.close();
                String storageDir = getDataStorageDir().toString();
                StringTokenizer uriParts = new StringTokenizer(uri, "/");
                Log.v(TAG, "serveExternalFile - number of tokens = " + uriParts.countTokens());
                while (uriParts.hasMoreTokens()) {
                    uripart = uriParts.nextToken();
                    Log.v(TAG, "uripart=" + uripart);
                }

                // If we have only given a "/logs" URI, return a list of
                // available files.
                // Re-start the StringTokenizer from the start.
                uriParts = new StringTokenizer(uri, "/");
                Log.v(TAG, "serveExternalFile - number of tokens = "
                        + uriParts.countTokens());
                if (uriParts.countTokens() == 1) {
                    Log.v(TAG, "Returning list of files");

                    File dirs = getDataStorageDir();
                    try {
                        JSONObject jsonObj = new JSONObject();
                        if (dirs.exists()) {
                            String[] fileList = dirs.list();
                            JSONArray arr = new JSONArray();
                            for (int i = 0; i < fileList.length; i++)
                                arr.put(fileList[i]);
                            jsonObj.put("logFileList", arr);
                        }
                        res = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,
                                "text/html", jsonObj.toString());
                    } catch (Exception ex) {
                        res = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,
                                "text/html", "ERROR - " + ex.toString());
                    }
                    return res;
                }

                uripart = uriParts.nextToken();  // This will just be /logs
                uripart = uriParts.nextToken();  // this is the requested file.
                String fname = storageDir + "/" + uripart;
                Log.v(TAG, "serveLogFile - uri=" + uri + ", fname=" + fname);
                ip = new FileInputStream(fname);
                String mimeStr = "text/html";
                res = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,
                        mimeStr, ip);
                res.addHeader("Content-Length", "" + ip.available());
            } catch (IOException ex) {
                Log.v(TAG, "serveLogFile(): Error Opening File - " + ex.toString());
                res = new NanoHTTPD.Response("serveLogFile(): Error Opening file " + uri);
            }
            return (res);
        }

        /**
         * Return a file from the apps /assets folder
         */
        NanoHTTPD.Response serveFile(String uri) {
            NanoHTTPD.Response res;
            InputStream ip = null;
            try {
                if (ip != null) ip.close();
                String assetPath = "www";
                String fname = assetPath + uri;
                //Log.v(TAG,"serveFile - uri="+uri+", fname="+fname);
                AssetManager assetManager = getResources().getAssets();
                ip = assetManager.open(fname);
                String mimeStr = "text/html";
                res = new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,
                        mimeStr, ip);
                res.addHeader("Content-Length", "" + ip.available());
            } catch (IOException ex) {
                Log.v(TAG, "serveFile(): Error Opening File - " + ex.toString());
                res = new NanoHTTPD.Response("serveFile(): Error Opening file " + uri);
            }
            return (res);
        }
    }

}
