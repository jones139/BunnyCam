package uk.org.maps3.bunnycam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

interface IpCamListener {
    public void onGotImage(byte[] img);
}

/**
 * IpCamController will grab an image of an IP Camera on demand, carrying out the activity in the background to avoid locking
 * up the user interface.
 * Created by graham on 04/08/15.
 */
public class IpCamController {
    private String mIpAddr;  // ip address of camera
    private String mUname;   // camera user name
    private String mPasswd;   // camera password
    private int mCmdSet;   // Command set for ip camera 0 = mJPEG, 1=h264.
    private String TAG = "IpCamController";
    private IpCamListener mIpCamListener = null;   // the class containing the onGotImage() callback function.
    private byte[] mResult;
    private ImageDownloader mImageDownloader;

    /**
     * Create an instance of IpCamController with specified ip adress, username, password and command set.
     * Command set will be used to select different brands of IP camera, which use different commands to  obtain images,
     * but this is not implemented at present.
     * @TODO Implement cmdSet to use different iP Cameras.
     * @TODO Extend class to do more with the IP camera - move a pan/tilt camera etc.
     *
     * @param ipAddr
     * @param uname
     * @param passwd
     * @param cmdSet
     */
    public IpCamController(String ipAddr, String uname, String passwd, int cmdSet, IpCamListener listener) {
        mIpAddr = ipAddr;
        mUname = uname;
        mPasswd = passwd;
        mCmdSet = cmdSet;
        mIpCamListener = listener;
        mResult = null;
    }

    /**
     * grab a still image from the camera.  Returns a JPEG image as a byte array.
     *
     * @return the image data as a byte array.
     */
    public void getImage() {
        Log.v(TAG, "getImage() - mIpAddr = " + mIpAddr);
        ImageDownloader imageDownloader = new ImageDownloader();
        imageDownloader.execute(new String[]{mIpAddr + "?user=" + mUname + "&pwd=" + mPasswd});
    }

    /**
     * imageDownloader - based on http://javatechig.com/android/download-image-using-asynctask-in-android
     */
    private class ImageDownloader extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            Log.v(TAG, "doInBackground - calling downloadImage(" + params[0] + ")");
            //try {Thread.sleep(10000);} catch(Exception e) {Log.v(TAG,"Exception during sleep - "+e.toString());}
            Bitmap bitmap = (Bitmap) downloadImage((String) params[0]);
            Log.v(TAG, "doInBackground - returned from downloadImage()");
            return bitmap;
        }

        @Override
        protected void onPreExecute() {
            Log.v(TAG, "onPreExecute()");
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Log.v(TAG, "onPostExecute()");
            super.onPostExecute(bitmap);

            Log.v(TAG, "onPostExecute() - converting bitmap to byte array...");
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
                byte[] data = os.toByteArray();
                mIpCamListener.onGotImage(data);
            } catch (Exception e) {
                Log.e(TAG, "Error in onPostExecute()");
                e.printStackTrace();
            }
        }


        private Bitmap downloadImage(String url) {
            byte[] result = null;
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
                        Log.v(TAG, "entity=" + entity.toString());
                        // getting contents from the stream
                        inputStream = entity.getContent();
                        Log.v(TAG, "inputStrem=" + inputStream.toString());
                        // decoding stream data back into image Bitmap that android understands
                        final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        Log.v(TAG, "downloadImage() - Returning Bitmap");
                        return bitmap;

                        //inputStream.read(result);
                        //return result;
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
                        " retrieving bitmap from " + url + ": Error is: " + e.toString());
            }
            return null;
        }
    }

}
