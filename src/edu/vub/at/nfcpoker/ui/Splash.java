package edu.vub.at.nfcpoker.ui;

import java.io.IOException;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import edu.vub.at.commlib.CommLib;
import edu.vub.at.commlib.CommLibConnectionInfo;
import edu.vub.at.nfcpoker.R;
import edu.vub.at.nfcpoker.TableThing;
import edu.vub.at.nfcpoker.comm.PokerServer;
import edu.vub.nfc.thing.EmptyRecord;
import edu.vub.nfc.thing.Thing;
import edu.vub.nfc.thing.ThingActivity;
import edu.vub.nfc.thing.listener.ThingSavedListener;

public class Splash extends ThingActivity<TableThing> {

    public class DiscoveryAsyncTask extends AsyncTask<Void, Void, CommLibConnectionInfo> {

		@Override
		protected CommLibConnectionInfo doInBackground(Void... arg0) {
			WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
			MulticastLock ml = wm.createMulticastLock("edu.vub.at.nfcpoker");
			ml.acquire();
			try {
				return CommLib.discover(PokerServer.class);
			} catch (IOException e) {
				Log.d("Discovery", "Could not start discovery", e);
				return null;
			} finally {
				ml.release();
			}
		}

		@Override
		protected void onPostExecute(CommLibConnectionInfo result) {
			super.onPostExecute(result);
			if (result != null) {
				Intent i = new Intent(Splash.this, ClientActivity.class);
				i.putExtra("ip", ((TableThing) lastScannedTag_).ip_);
				i.putExtra("port", ((TableThing) lastScannedTag_).port_);
				startActivity(i);
			} else {
				Toast.makeText(Splash.this, "Could not discover hosts", Toast.LENGTH_SHORT).show();
			}
		}
	}

	// TO BE CONFIGURED (TODO)
	public static boolean IS_SERVER = true;
	public static boolean IS_LODE = false;

	// Connectivity state
	public static String UUID;
	public static String NETWORK_GROUP;
	
	private Object lastScannedTag_;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Settings
        UUID = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        NETWORK_GROUP = "TODO-FROM-NFC";
        
        Button beamButton = (Button) findViewById(R.id.beamInviteButton);
        beamButton.setEnabled(false);
        beamButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				((TableThing) lastScannedTag_).broadcast();
			}
		});
        
        Button discover = (Button) findViewById(R.id.discover);
        discover.setEnabled(false);
        discover.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new DiscoveryAsyncTask().execute();
			}
		});

        if (IS_LODE) {
        	Intent intent = new Intent(this, ClientActivity.class);
        	startActivity(intent);
        } else {
        	Button server = (Button) findViewById(R.id.server);
        	if (server != null)
        		server.setOnClickListener(new OnClickListener() {

        			@Override
        			public void onClick(View v) {
        				startServer();
        			}
        		});
        }
        
        final Splash theActivity = this;
        Button nfcButton = (Button) findViewById(R.id.nfc);
        nfcButton.setEnabled(false);
        nfcButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String iptext = ((EditText) findViewById(R.id.iptext)).getText().toString();
				Integer port = Integer.parseInt(((EditText) findViewById(R.id.porttext)).getText().toString());
				TableThing tableThing = new TableThing(theActivity, iptext, port.intValue());
				if (lastScannedTag_ instanceof EmptyRecord) {
					((EmptyRecord) lastScannedTag_).initialize(tableThing, new ThingSavedListener<TableThing>() {
						@Override
						public void signal(TableThing savedTableThing) {
							lastScannedTag_ = savedTableThing;
							Toast.makeText(theActivity, "NFC tag written successfully", Toast.LENGTH_SHORT).show();
						}
					});
				} else {
					TableThing scannedTableThing = (TableThing) lastScannedTag_;
					scannedTableThing.ip_ = iptext;
					scannedTableThing.port_ = port;
					scannedTableThing.saveAsync(new ThingSavedListener<TableThing>() {
						@Override
						public void signal(TableThing savedTableThing) {
							Toast.makeText(theActivity, "NFC tag written successfully", Toast.LENGTH_SHORT).show();
						}
					});
				}
			}
		});
	}
	

	// NFC

	@Override
	public void whenDiscovered(TableThing tableThing) {
		super.whenDiscovered(tableThing);
		Button nfcButton = (Button) findViewById(R.id.nfc);
		nfcButton.setEnabled(true);
		lastScannedTag_ = tableThing;
		((Button) findViewById(R.id.discover)).setEnabled(true);
		((Button) findViewById(R.id.beamInviteButton)).setEnabled(true);
	 }
	 	
	 @Override
	 public void whenDiscovered(EmptyRecord r) {
		 super.whenDiscovered(r);
		 Button nfcButton = (Button) findViewById(R.id.nfc);
		 nfcButton.setEnabled(true);
		 lastScannedTag_ = r;
		 ((Button) findViewById(R.id.discover)).setEnabled(false);
		 ((Button) findViewById(R.id.beamInviteButton)).setEnabled(false);
	 }
	
    protected void startServer() {
		Intent i = new Intent(this, ServerActivity.class);
		startActivity(i);
		finish();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_splash, menu);
        return true;
    }

	@Override
	public Class<? extends Thing> getThingType() {
		return TableThing.class;
	}
}
