package edu.vub.at.nfcpoker.ui;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import edu.vub.at.commlib.CommLib;
import edu.vub.at.commlib.CommLibConnectionInfo;
import edu.vub.at.nfcpoker.ConcretePokerServer;
import edu.vub.at.nfcpoker.Constants;
import edu.vub.at.nfcpoker.QRFunctions;
import edu.vub.at.nfcpoker.R;
import edu.vub.at.nfcpoker.settings.Settings;
import edu.vub.at.nfcpoker.ui.ServerActivity.ServerStarter;

public class Splash extends Activity {
	private static final boolean LODE = false;

	// Shared globals
	public static final String WEPOKER_WEBSITE = "http://wepoker.info";

	// Connectivity state
	private BroadcastReceiver wifiWatcher;
	
	// Discovery
	private volatile DiscoveryAsyncTask discoveryTask;
	private volatile Timer client_startClientServerTimer;
	private volatile Dialog client_startClientServerAsk;
	
	// UI
	public static Handler messageHandler;
	private boolean isTablet = false;
	private int startClientServerTimerTimeout = 10000;
	private int startClientServerTimerTimeout2 = 30000;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);

		// Settings
		Settings.loadSettings(this);
		
		View tablet_layout = findViewById(R.id.tablet_layout);
		if (tablet_layout != null)
			isTablet = true;
		
		Button server = (Button) findViewById(R.id.server);
		if (server != null)
			server.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startServer();
				}
			});

		// UI
		messageHandler = new IncomingHandler(this);
		
		
		if (LODE) {
			Intent i = new Intent(this, ClientActivity.class);
			i.putExtra("isDedicated", false);
			startActivity(i);
			return;
		}
				
		final DiscoveryAsyncTask.DiscoveryCompletionListener dcl = new DiscoveryAsyncTask.DiscoveryCompletionListener() {
			@Override
			public void onDiscovered(CommLibConnectionInfo result) {
				if (client_startClientServerTimer != null) {
					client_startClientServerTimer.cancel();
					client_startClientServerTimer = null;
					if (client_startClientServerAsk != null) {
						client_startClientServerAsk.dismiss();
						client_startClientServerAsk = null;
					}
				}
				int port = 0;
				try {
					Integer.parseInt(result.getPort());
				} catch (Exception e) { }
				startClient(result.getAddress(), port, result.isDedicated(), false, null, null, null);
			}
		};
		
		if (!isTablet) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			
			//TODO: Only try Wifi-direct if available and no wifi is found.
			discoveryTask = new DiscoveryAsyncTask(this, dcl);
			discoveryTask.execute();
			
			// If there is no server responding after 10 seconds, ask the user to start one without a dedicated table
			scheduleAskStartClientServer(startClientServerTimerTimeout);
		} else {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			final Button disc = (Button) findViewById(R.id.discover_button);
			disc.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					startDiscovery(dcl);
					disc.setEnabled(false);
				}
			});			
		}
	}
	
	public boolean isWifiDirectSupported() {
		return getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
	}
	
	public boolean isNFCSupported() {
		return false;
	}

	@Override
	public void onResume() {
		super.onResume();
//		registerWifiWatcher();
//		mWifiDirectManager.registerReceiver();
	}
	
	@Override
	public void onPause() {
		super.onPause();
//		unregisterReceiver(wifiWatcher); wifiWatcher = null;
//		mWifiDirectManager.unregisterReceiver();
		// TODO pause discovery 
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Settings.saveSettings(this);
	}
	
	// Connectivity
	private class ConnectionChangeReceiver extends BroadcastReceiver {
		public void onReceive(Context context, Intent intent ) {
			Log.d("wePoker - Splash", "My IP Address changed!");
		}
	}
	
	private void registerWifiWatcher() {
		if (wifiWatcher != null) return;
		wifiWatcher = new ConnectionChangeReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		registerReceiver(wifiWatcher, intentFilter);
	}
	
	// UI
	static class IncomingHandler extends Handler {
		private final WeakReference<Context> mCtx;

		IncomingHandler(Context ctx) {
			mCtx = new WeakReference<Context>(ctx);
		}

		@Override
		public void handleMessage(Message msg) {
			String txt;
			Context ctx = mCtx.get();
			if (ctx != null) {
				switch(msg.what) {
				case UIMessage.MESSAGE_TOAST:
					txt = msg.getData().getString("message");
					if (txt == null) return;
					Toast.makeText(ctx, txt, Toast.LENGTH_SHORT).show();
					break;
				case UIMessage.MESSAGE_DISCOVERY_FAILED:
					// TODO
					break;
				}
			}
		}
	}
	
	public void startClient(
			String ip, int port, boolean isDedicated,
			boolean isServer, String broadcast, String wifiName, String wifiPassword) {
		Intent i = new Intent(this, ClientActivity.class);
		i.putExtra(Constants.INTENT_SERVER_IP, ip);
		i.putExtra(Constants.INTENT_PORT, port);
		i.putExtra(Constants.INTENT_IS_DEDICATED, isDedicated);
		i.putExtra(Constants.INTENT_IS_SERVER, isServer);
		i.putExtra(Constants.INTENT_BROADCAST, broadcast);
		i.putExtra(Constants.INTENT_WIFI_NAME, wifiName);
		i.putExtra(Constants.INTENT_WIFI_PASSWORD, wifiPassword);
		startActivity(i);
		finish();
	}
	    
	
	// Discovery
	private void scheduleAskStartClientServer(int timeout) {
		client_startClientServerTimer = new Timer();
		client_startClientServerTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						askStartClientServer();
					}
				});
			}
		}, timeout);
	}
	
	public void startDiscovery(DiscoveryAsyncTask.DiscoveryCompletionListener dcl) {
		if (discoveryTask != null)
			discoveryTask.cancel(true);
		
		discoveryTask = new DiscoveryAsyncTask(Splash.this, dcl);
		discoveryTask.execute();
	}
	
	public void restartDiscovery() {
		if (discoveryTask == null)
			return;
		
		startDiscovery(discoveryTask.dcl);
	}
	
	private void askStartClientServer() {
		final Activity act = this;
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which){
				case DialogInterface.BUTTON_POSITIVE:
					if (client_startClientServerTimer != null) {
						client_startClientServerTimer.cancel();
						client_startClientServerTimer = null;
						new Thread() {
							@Override
							public void run() {
								WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
								boolean isWifiEnabled = wm.isWifiEnabled();
								boolean isConnected = wm.getConnectionInfo().getNetworkId() != -1;
								if (!(isWifiEnabled && isConnected)) {
						    		ServerStarter startServer = new ServerStarter() {
						    			private String wifiGroupName;
						    			private String wifiPassword;
						    			private String ipAddress;
						    			@Override
						    			public void start(String ipAddress, String broadcastAddress) {
						    				this.ipAddress = ipAddress;
						    				if (discoveryTask != null) {
						    					discoveryTask.cancel(true);
						    					discoveryTask = null;
						    				}
						    				startClient(ipAddress, CommLib.SERVER_PORT, false,
						    							true, broadcastAddress, wifiGroupName, wifiPassword);
						    			}

						    			@Override
						    			public void setWifiDirect(String groupName, String password, final String ipAddress) {
						    				// TODO setup NFC tag.
						    				this.wifiGroupName = groupName;
						    				this.wifiPassword = password;
						    				this.ipAddress = ipAddress;
						    				runOnUiThread(new Runnable() {
						    					@Override
						    					public void run() {
						    						QRFunctions.showWifiConnectionDialog(act, wifiGroupName, wifiPassword, ipAddress, false);
						    					}
						    				});
						    			}
						    		};
						    		new WifiDirectManager.Creator(act, startServer).run();
						    	} else {

						    		String ipAddress = CommLib.getIpAddress(Splash.this);
						    		String broadcastAddress = CommLib.getBroadcastAddress(Splash.this);
				    				startClient(ipAddress, CommLib.SERVER_PORT, false,
				    						true, broadcastAddress,
			    							wm.getConnectionInfo().getSSID(),
			    							"********");
						    	}
							}
						}.start();
					}
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					scheduleAskStartClientServer(startClientServerTimerTimeout2);
					break;
				}
				client_startClientServerAsk = null;
			}
		};
		
		DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface arg0) {
				scheduleAskStartClientServer(startClientServerTimerTimeout2);
				client_startClientServerAsk = null;
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		client_startClientServerAsk =
				builder.setMessage("No Ambient-Poker game discovered.\nDo you wish to start one?")
				.setOnCancelListener(onCancelListener)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener).create();
		client_startClientServerAsk.show();
	}

	protected void startServer() {
		if (discoveryTask != null) {
			discoveryTask.cancel(true);
			discoveryTask = null;
		}
		
		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
		wm.setWifiEnabled(true);
		WifiInfo connInfo = wm.getConnectionInfo();
		boolean enabled = wm.isWifiEnabled();
		boolean connected = connInfo != null && connInfo.getNetworkId() != -1;
		
		Intent i = new Intent(this, ServerActivity.class);
		i.putExtra("wifiDirect", isWifiDirectSupported() && !(enabled && connected));
		startActivity(i);
		finish();
	}

}

