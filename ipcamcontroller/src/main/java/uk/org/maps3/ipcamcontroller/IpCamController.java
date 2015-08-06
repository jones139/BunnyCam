package uk.org.maps3.ipcamcontroller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStream;

/**
 * Created by graham on 04/08/15.
 */
public class IpCamController {
    private String mIpAddr;  // ip address of camera
    private String mUname;   // camera user name
    private String mPasswd;   // camera password
    private int mCmdSet;   // Command set for ip camera 0 = mJPEG, 1=h264.
    private String TAG = "IpCamController";

    /**
     * Create an instance of IpCamController with specified ip adress, username, password and command set.
     *
     * @param ipAddr
     * @param uname
     * @param passwd
     * @param cmdSet
     */
    public IpCamController(String ipAddr, String uname, String passwd, int cmdSet) {
        mIpAddr = ipAddr;
        mUname = uname;
        mPasswd = passwd;
        mCmdSet = cmdSet;
    }

    /**
     * grab a still image from the camera
     *
     * @return the image data as a byte array.
     */
    public byte[] getImage() {
        return null;
    }

    /**
     * imageDownloader - based on http://javatechig.com/android/download-image-using-asynctask-in-android
     */
    private class imageDownloader extends AsyncTask {
        @Override
        protected Object doInBackground(Object[] params) {
            return downloadBitmap((String) params[0]);
        }

        @Override
        protected void onPreExecute() {
            Log.v(TAG, "onPreExecute()");
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            Bitmap result = (Bitmap) o;
            Log.v(TAG, "onPostExecute()");
        }


        private Bitmap downloadBitmap(String url) {
            final DefaultHttpClient client = new DefaultHttpClient();
            final HttpGet getRequest = new HttpGet(url);
            try {
                HttpResponse response = client.execute(getRequest);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    Log.w(TAG, "Error " + statusCode +
                            " while retrieving bitmap from " + url);
                    return null;
                }

                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream inputStream = null;
                    try {
                        // getting contents from the stream
                        inputStream = entity.getContent();
                        // decoding stream data back into image Bitmap that android understands
                        final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        return bitmap;
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        entity.consumeContent();
                    }
                }
            } catch (Exception e) {
                // You Could provide a more explicit error message for IOException
                getRequest.abort();
                Log.e(TAG, "Something went wrong while" +
                        " retrieving bitmap from " + url + e.toString());
            }
            return null;
        }
    }

}
