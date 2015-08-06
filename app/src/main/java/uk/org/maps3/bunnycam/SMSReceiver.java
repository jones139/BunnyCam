package uk.org.maps3.bunnycam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Created by graham on 06/08/15.
 */
public class SMSReceiver extends BroadcastReceiver {
    /**
     * is the SMS Responder active?
     */
    boolean mActive;
    /**
     * Password required for location response
     */
    String mPassword;
    /**
     * Base text of location response message
     */
    String mMessageText;

    Context mContext = null;
    String smsNumber = null;
    String TAG = "SMSReceiver";

    /*
     * (non-Javadoc)
     *
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context contextArg, Intent intentArg) {
        //---get the SMS message passed in---
        Bundle bundle = intentArg.getExtras();
        SmsMessage[] msgs = null;
        mContext = contextArg;
        // retrieve settings
        getPrefs(contextArg);
        Log.d(TAG, "onReceive()");
        if (bundle == null) {
            //showToast("Empty SMS Message Received - Ignoring");
            Log.d(TAG, "onReceive() - Empty SMS Message - Ignoring");
        } else {
            //---retrieve the SMS message received---
            Object[] pdus = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdus.length];
            for (int i = 0; i < msgs.length; i++) {
                String str = "";
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                str += "SMS from " + msgs[i].getOriginatingAddress();
                str += " :";
                str += msgs[i].getMessageBody().toString();
                str += "\n";
                Log.d(TAG, "onReceive() - msg = " + str);
            }
            String msg0 = msgs[0].getMessageBody().toString();
            // Check if it is a location request.
            if (mActive && msg0.toUpperCase().contains(mPassword.toUpperCase())) {
                // Get the location using the LocationFinder.
                smsNumber = msgs[0].getOriginatingAddress();
            } else {
                Log.d(TAG, "onReceive() - inactive, or Message does "
                        + "not contain Password - Ignoring");
            }
        }
    }

    private void getPrefs(Context context) {
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        mActive = SP.getBoolean("respondToSms", false);
        mPassword = SP.getString("smsPasswd", "BunnyCam");
    }
}