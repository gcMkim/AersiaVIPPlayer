package com.ryansailor.aersiavipplayer;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements 
	OnItemClickListener, OnMagicLinkParserReadyListener, OnComplexMediaPlayerListener, OnTouchListener, OnBufferingUpdateListener, OnAudioFocusChangeListener {

	/* Debug */
	public final static String TAG = "AersiaVIPPlayer";
	
	
	/* Media Player */
	private static ComplexMediaPlayer mp;
	private int trackCurrentTime;
	
	
	/* Play List */
	private class PlayList {
		
		private PLAYLISTID _id;
		private String _location;
		private String _name;
		
		protected PlayList(PLAYLISTID id, String location, String name) {
			_id = id;
			_location = location;
			_name = name;
		}
		
		protected PLAYLISTID getId() 	   { return _id; }
		protected String	 getLocation() { return _location; }
		protected String	 getName() 	   { return _name; }
	}
	
	private enum PLAYLISTID {
		VIP,
		MELLOW,
		EXILED,
		SOURCE
	}

	private PlayList currentPlayList;
	
	/* UI */
	private MenuItem playButton;
	private TextView trackTime;
	private ListView trackList;
	private TextView nowPlaying;
	private int selectedTrack;
	
	private SeekBar seekBarProgress;
	private final Handler seekHandler = new Handler();
	
	/* Notification */
	Notification notification;
	
	/* System Services */
	AudioManager audioManager;
	
	/* Data */
	String[] parsedMusicPaths;
	
	//headset button support	
    private ComponentName mRemoteControlResponder;
	
	
	/*
	 * 
	 * 
	 * CLASSES AND HANDLERS
	 * 
	 * 
	 */
	
	private class SelectionArrayAdapter extends ArrayAdapter<String> {
		private final Context context;
		private int resource;
		private int selection;
		
		public SelectionArrayAdapter(Context context, int resource) {
			super(context, R.layout.music_listing_item, resource);
			this.context = context;
			this.resource = resource;
			this.selection = -1;
		}
		
		public void setSelection(int pos) {
			selection = pos;
		}
		
		public String getSelection() {
			return this.getItem(selection);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			TextView rowView = (TextView) inflater.inflate(this.resource, parent, false);
			rowView.setText(this.getItem(position));
			if(position == selection) {
				rowView.setBackgroundColor(getResources().getColor(R.color.music_list_item_bg_sel));
				rowView.setTextColor(getResources().getColor(R.color.music_list_item_text_sel));
			} else {
				rowView.setBackgroundColor(getResources().getColor(R.color.music_list_item_bg));
				rowView.setTextColor(getResources().getColor(R.color.music_list_item_text));
			}
			return rowView;
		}
		
	}
	
	
	SelectionArrayAdapter musicListAdapter;
	
	private Handler trackTimeHandler = new Handler(Looper.getMainLooper());
	private final Runnable r = new Runnable() {
		public void run() {
			int cursecs = mp.getTrackCurrentTime()/1000;
			int totalsecs = mp.getTrackTotalTime()/1000;
			String Scursecs = ((cursecs%60) < 10) ? "0" + (cursecs%60) : "" + (cursecs%60);
			String Stotalsecs = ((totalsecs%60) < 10) ? "0" + (totalsecs%60) : "" + (totalsecs%60);
			String curtime = (cursecs/60) + ":" + Scursecs;
			String totaltime = (totalsecs/60) + ":" + Stotalsecs;
			trackTime.setText(curtime + "/" + totaltime);
			trackTimeHandler.postDelayed(r, 100);
		}
	};
	
	/*
	 * 
	 * 
	 * LIFECYCLE METHODS
	 * 
	 * 
	 */
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Build Media Player
		mp = new ComplexMediaPlayer();
		
		mp.setOnComplexMediaPlayerListener(this);
		mp.setOnBufferingUpdateListener(this);
		
		// Audio Manager 
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		
		if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			// TODO: If Audio Focus is not granted at startup
		}
		
		//headset button
		 mRemoteControlResponder = new ComponentName(getPackageName(),
	                MediaButtonIntentReceiver.class.getName());	
		 
		 audioManager.registerMediaButtonEventReceiver(mRemoteControlResponder);
		
		// UI Setup	
		trackTime = (TextView) findViewById(R.id.music_time);
		trackList = (ListView) findViewById(R.id.music_list);
		nowPlaying = (TextView) findViewById(R.id.trackname);
		selectedTrack = -1;
		
		seekBarProgress = (SeekBar) findViewById(R.id.seekbar);
		seekBarProgress.setMax(99);
		seekBarProgress.setOnTouchListener(this);
				
		// Song List Setup
		musicListAdapter = new SelectionArrayAdapter(this, R.layout.music_listing_item);
		trackList.setAdapter(musicListAdapter);
		trackList.setOnItemClickListener(this);
		
		// Get URL list
		startNewPlaylist("Now Playing VIP original", new PlayList(PLAYLISTID.VIP, "http://vip.aersia.net/mu/", "VIP"));
	}
	
	@Override
	public void finish() { // TODO: I want to remove this
		trackTimeHandler.removeCallbacks(r);
		super.finish();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		trackTimeHandler.removeCallbacks(r);
		audioManager.unregisterMediaButtonEventReceiver(
                mRemoteControlResponder);
		if(mp != null)
			mp.destroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		playButton = menu.findItem(R.id.action_play);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
	    switch (item.getItemId()) {
	    	case R.id.action_play:
	    		onPlayPress();
	    		return true;
	    	case R.id.action_next:
	    		onNextPress();
	    		return true;
	    	case R.id.action_prev:
	    		onPrevPress();
	    		return true;
	    	case R.id.playlist_vip:
	    		if(currentPlayList.getId() != PLAYLISTID.VIP) {
		    		startNewPlaylist("Now Playing VIP Original",
		    				new PlayList(PLAYLISTID.VIP,
		    				"http://vip.aersia.net/mu/",
		    				"VIP"));
	    		}
	    		return true;
	    	case R.id.playlist_mellow:
	    		if(currentPlayList.getId() != PLAYLISTID.MELLOW) {
	    			startNewPlaylist("Now Playing VIP Mellow",
	    					new PlayList(PLAYLISTID.MELLOW,
	    					"http://vip.aersia.net/mu/mellow/",
	    					"VIP Mellow"));
	    		}
	    		return true;
	    	case R.id.playlist_exiled:
	    		if(currentPlayList.getId() != PLAYLISTID.EXILED) {
	    			startNewPlaylist("Now Playing VIP Exiled",
	    					new PlayList(PLAYLISTID.EXILED,
	    					"http://vip.aersia.net/mu/exiled/",
	    					"VIP Exiled"));
	    		}
	    		return true;
	    	case R.id.playlist_source:
	    		if(currentPlayList.getId() != PLAYLISTID.SOURCE) {
	    			startNewPlaylist("Now Playing VIP Source",
	    					new PlayList(PLAYLISTID.SOURCE,
	    					"http://vip.aersia.net/mu/source/",
	    					"VIP Source"));
	    		}
	    		return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onStop() {
		super.onStop();
		trackTimeHandler.removeCallbacks(r);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		if(mp.isPlaying()) {
			trackTimeHandler.post(r);	
		}
		mp.restoreResources();
	}
	
	/*
	 * 
	 * MUSIC CONTROL METHODS
	 * 
	 * 
	 */
	
	public void setPlay() {
		trackTimeHandler.removeCallbacks(r);
		mp.play();
		setUIPlay();
		trackTimeHandler.post(r);
	}
	
	public void setPause() {
		trackTimeHandler.removeCallbacks(r);
		mp.pause();
		setUIPause();
	}
	
	public void onPlayPress() {
		primarySeekBarProgressUpdater();
		if(mp.isPlaying()) {
			setPause();
		} else {
			setPlay();
		}
	}
	
	public void onPrevPress() {
		trackTimeHandler.removeCallbacks(r);
		mp.playPrevious();
		updateUINowPlaying();
		setUIPlay();
	}
	
	public void onNextPress() {
		trackTimeHandler.removeCallbacks(r);
		mp.playNext();
		updateUINowPlaying();
		setUIPlay();
	}
	
	public static void playNextRandom() {
		if (mp != null) {
			mp.playRandom();
		}
	}
	
	/*
	 * 
	 * 
	 * UI LISTENERS
	 * 
	 * 
	 * 
	 */
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
		trackTimeHandler.removeCallbacks(r);
		mp.playThis(pos);
		setUIPlay();
	}
	
	@Override
	public void onMagicLinkParserReady(String[] results) {
		parsedMusicPaths = results;
		mp.loadURLs(results);
	}
	
	@Override
	public void onComplexMediaPlayerBeginStream() {
		updateUINowPlaying();
		trackTimeHandler.post(r);
		primarySeekBarProgressUpdater();
	}
	
	@Override
	public void onComplexMediaPlayerLoaded() {
		musicListAdapter.clear();
		musicListAdapter.addAll(mp.getMusicNames());
		
		// Select random music
		trackTimeHandler.removeCallbacks(r);
		mp.playRandom();
		setUIPlay();
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if(v.getId() == R.id.seekbar) {
			SeekBar sb = (SeekBar)v;
			int ms = (mp.getTrackTotalTime()/100) * sb.getProgress();
			mp.seekTo(ms);
		}
		return false;
	}
	
	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		seekBarProgress.setSecondaryProgress(percent);
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch(focusChange) {
			case AudioManager.AUDIOFOCUS_GAIN:
				// Restore resources and resume playback
				mp.restoreResources();
				mp.playThisAndSeek(selectedTrack,trackCurrentTime);
				break;
			case AudioManager.AUDIOFOCUS_LOSS:
				// Save state, release resources and stop playback
				trackCurrentTime = mp.getTrackCurrentTime();
				mp.releaseResources();
				setUIPause();
				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				// Save state and stop playback
				trackCurrentTime = mp.getTrackCurrentTime();
				mp.stop();
				setUIPause();
				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				// Lower volume
				if(mp.isPlaying()) {
					mp.setVolume(0.1f, 0.1f);
				}
				break;
		}
	}

	
	/*
	 * 
	 * 
	 * UI Methods
	 * 
	 * 
	 */
	
	private void primarySeekBarProgressUpdater() {
		seekBarProgress.setProgress((int)(((float)mp.getTrackCurrentTime()/mp.getTrackTotalTime())*100));
		if (mp.isPlaying()) {
			Runnable notification = new Runnable() {
				public void run() {
					primarySeekBarProgressUpdater();
				}
			};
			seekHandler.postDelayed(notification,1000);
		}
	}
	
	private void updateUINowPlaying() {

		// update selected and change color
		selectedTrack = mp.getNowPlayingIndex();
		musicListAdapter.setSelection(selectedTrack);
		musicListAdapter.notifyDataSetChanged();
		
		nowPlaying.setText(musicListAdapter.getSelection());
		
		// scroll to selected
		int currentPosition = trackList.getFirstVisiblePosition();
		int positionDifference = selectedTrack - currentPosition;
		// To prevent excess scrolling, skip some if track position different is large
		int delta = 80; // TODO: figure this out programmatically
		if(Math.abs(positionDifference) >= delta) {
			// If we move up, 
			if(positionDifference < 0) {
				trackList.smoothScrollByOffset(-10);
				trackList.setSelection(selectedTrack + delta);
			} else {
				trackList.smoothScrollByOffset(10);
				trackList.setSelection(selectedTrack - delta);
			}
		}
		trackList.smoothScrollToPositionFromTop(selectedTrack,0,1000);
	}
	
	private void setUIPlay() {
		playButton.setIcon(getResources().getDrawable(R.drawable.ic_action_pause));
	}
	
	private void setUIPause() {
		//on some handsets, the system will pull away and restore audio focus on startup
		//before playButton is set to the proper view.
		if (playButton != null) { 
			playButton.setIcon(getResources().getDrawable(R.drawable.ic_action_play));
		}
	}

	private void startNewPlaylist(String msg, PlayList pl) {
		mp.stop();
		currentPlayList = pl;
		// Get URL list
		MagicLinkParser mlp = new MagicLinkParser();
		mlp.setOnComplexMediaUpdateListener(this);
		mlp.parse(pl.getLocation());
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
		getActionBar().setTitle(pl.getName());
	}


/** Control methods **/
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		switch (keyCode) { 
		case KeyEvent.KEYCODE_HEADSETHOOK: 	playNextRandom();
		return true;
		default: return false;		
		} 

	}

	
}
