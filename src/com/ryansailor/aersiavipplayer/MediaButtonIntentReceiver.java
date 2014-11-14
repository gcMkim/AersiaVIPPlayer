package com.ryansailor.aersiavipplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class MediaButtonIntentReceiver extends BroadcastReceiver {


	 @Override
	    public void onReceive(Context context, Intent intent) {
	    	
	        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
	            /* handle media button intent here by reading contents */
	            /* of EXTRA_KEY_EVENT to know which key was pressed    */
	        	KeyEvent kEvent = (KeyEvent)intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
	        	if (kEvent != null) {
	        		System.out.println("Key event is "+kEvent.toString());
	        	}
	        	MainActivity.playNextRandom();
	        }
	    }
}