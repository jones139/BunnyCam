package uk.org.maps3.bunnycam;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by graham on 08/08/15.
 */
public class ImageUploader extends AsyncTask<Object, Void, Boolean> {
    private String TAG = "ImageUploader";

    @Override
    protected Boolean doInBackground(Object... params) {
        final String fileName = (String) params[0];
        final String serverUrl = (String) params[1];
        final byte[] data = (byte[]) params[2];
        Log.v(TAG, "doInBackground(" + fileName + ",data," + serverUrl);
        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        int serverResponseCode = 0;
        try {
            URL url = new URL(serverUrl);

            // Open a HTTP  connection to  the URL
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true); // Allow Inputs
            conn.setDoOutput(true); // Allow Outputs
            conn.setUseCaches(false); // Don't use a Cached Copy
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", fileName);

            dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                    + fileName + "\"" + lineEnd);

            dos.writeBytes(lineEnd);

            dos.write(data, 0, data.length);

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes("SUBMIT=true");
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Responses from the server (code and message)
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();
            Log.v(TAG, "server response = " + serverResponseMessage);

            Log.i("uploadFile", "HTTP Response is : "
                    + serverResponseMessage + ": " + serverResponseCode);
            // Get the server response

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            //close the streams //
            dos.flush();
            dos.close();

            String serverResponseText = sb.toString();
            Log.v(TAG, "serverResponseText = " + serverResponseText + ".");
            if (serverResponseCode == 200) {

                return true;
            } else {
                return false;
            }
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            Log.e(TAG, "error: " + ex.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Upload file to server Exception : "
                    + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
