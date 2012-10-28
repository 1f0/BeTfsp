package edu.vub.at.nfcpoker.ui;

import java.util.HashMap;
import java.util.Random;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import edu.vub.at.commlib.CommLib;
import edu.vub.at.nfcpoker.Card;
import edu.vub.at.nfcpoker.ConcretePokerServer;
import edu.vub.at.nfcpoker.ConcretePokerServer.GameState;
import edu.vub.at.nfcpoker.Constants;
import edu.vub.at.nfcpoker.QRFunctions;
import edu.vub.at.nfcpoker.R;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

public class ServerActivity extends Activity implements ServerViewInterface {

	public interface ServerStarter {
		public void start(String ipAddress, String broadcastAddress);

		public void setWifiDirect(String groupName, String password, String ipAddress);
	}
	
	private final int MIN_AVATAR_ID = 1;
	private final int MAX_AVATAR_ID = 15;
	private final int MAX_NUMBER_AVATARS_SIDE = 4;
	
	private Random random = new Random();
	
	@SuppressLint("UseSparseArrays")
	HashMap<Integer, View> playerBadges = new HashMap<Integer, View>();
	protected String currentWifiGroupName;
	protected String currentWifiPassword; 
	protected String currentIpAddress;

	private boolean isWifiDirect;

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setContentView(R.layout.activity_server);
    	View tablet_layout = findViewById(R.id.tablet_layout);
    	boolean isTV = getPackageManager().hasSystemFeature("com.google.android.tv");
    	final boolean isDedicated = tablet_layout != null || isTV;
    	isWifiDirect = getIntent().getBooleanExtra("wifiDirect", false);
    	
		ServerStarter startServer = new ServerStarter() {
			
			@Override
			public void start(String ipAddress, String broadcastAddress) {
				ConcretePokerServer cps = new ConcretePokerServer(ServerActivity.this, isDedicated, ipAddress, broadcastAddress);
				currentIpAddress = ipAddress; 
				cps.start();				
			}

			@Override
			public void setWifiDirect(String groupName, String password, String ipAddress) {
				// TODO setup NFC tag.
				currentWifiGroupName = groupName;
				currentWifiPassword  = password;
				currentIpAddress = ipAddress;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showWifiConnectionDialog();
					}
				});
			}
		};
    	
		if (isWifiDirect) {
    		new WifiDirectManager.Creator(this, startServer).run();
    	} else {
    		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
    		currentWifiGroupName = wm.getConnectionInfo().getSSID();
    		currentWifiPassword = "********";
    		
    		String ipAddress = CommLib.getIpAddress(this);
    		String broadcastAddress = CommLib.getBroadcastAddress(this);
    		startServer.start(ipAddress, broadcastAddress);
    	}
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.show_wifi_settings) {
    		showWifiConnectionDialog();
    	}
		return super.onOptionsItemSelected(item);
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_server, menu);
        return true;
    }

    private Dialog wifiConnectionDialog;
    
	private void showWifiConnectionDialog() {
		if (wifiConnectionDialog == null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			View dialogGuts = getLayoutInflater().inflate(R.layout.wifi_connection_dialog, null);
			
			TextView networkNameTV = (TextView) dialogGuts.findViewById(R.id.network_name);
			networkNameTV.setText(this.currentWifiGroupName);
			TextView passwordTV = (TextView) dialogGuts.findViewById(R.id.password);
			passwordTV.setText(currentWifiPassword);
			Button dismissButton = (Button) dialogGuts.findViewById(R.id.dismiss_btn);
			dismissButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					wifiConnectionDialog.dismiss();	
				}
			});
			
			try {
				String connectionString = QRFunctions.createJoinUri(currentWifiGroupName, currentWifiPassword, currentIpAddress, true);
				Bitmap qrCode = QRFunctions.encodeBitmap(connectionString);
				ImageView qrCodeIV = (ImageView) dialogGuts.findViewById(R.id.qr_code);
				qrCodeIV.setImageBitmap(qrCode);
			} catch (WriterException e) {
				Log.e("wePoker - Server", "Could not create QR code", e);
			}
			
			
			wifiConnectionDialog = 
				builder.setTitle("Connection details")
				       .setCancelable(true)
				       .setView(dialogGuts)
				       .create();
		}
		
		wifiConnectionDialog.show();
	}

	int nextToReveal = 0;

	public void revealCards(final Card[] cards) {
		runOnUiThread(new Runnable() {
			public void run() {
				for (Card c : cards) {
					Log.d("wePoker - Server", "Revealing card " + c);
					LinearLayout ll = (LinearLayout) findViewById(R.id.cards);
					ImageButton ib = (ImageButton) ll.getChildAt(nextToReveal++);
					CardAnimation.setCardImage(ib, cardToResourceID(c));
				}
			}

			public int cardToResourceID(Card c) {
				return getResources().getIdentifier("edu.vub.at.nfcpoker:drawable/" + c.toString(), null, null);
			}
		});
	}
	

	public void resetCards() {
		Log.d("wePoker - Server", "Hiding cards again");
		nextToReveal = 0;
		runOnUiThread(new Runnable() {
			public void run() {
				LinearLayout ll = (LinearLayout) findViewById(R.id.cards);
				for (int i = 0; i < 5; i++) {
					final ImageButton ib = (ImageButton) ll.getChildAt(i);
					CardAnimation.setCardImage(ib, R.drawable.backside);
				}
			}
		});
	}

	public void showStateChange(final GameState newState) {
		runOnUiThread(new Runnable() {
			public void run() {
				String prefix = getResources().getString(R.string.title_activity_server);
				setTitle(prefix + " \u2014 " + newState.toString());
			}
		});
	}

	public String getNewAvatar(){
		int rndNumberInRange = random.nextInt(MAX_AVATAR_ID - MIN_AVATAR_ID) + MIN_AVATAR_ID;
		Log.d("wePoker - Server", "Avatar for player " + rndNumberInRange);
		return "avatar_" + rndNumberInRange;
	}
	@Override
	public void addPlayer(final int clientID, final String clientName, final int initialMoney) {
		runOnUiThread(new Runnable() {
			public void run() {
				Log.d("wePoker - Server", "Adding player name " + clientName);
				LinearLayout users;
				 // todo make this variable.. now hardcoded 4 and 4 on each side.
				if (playerBadges.size() < MAX_NUMBER_AVATARS_SIDE) {
					users = (LinearLayout) findViewById(R.id.users_bottom);
				} else{
					users = (LinearLayout) findViewById(R.id.users_top);
				}
				View badge = getLayoutInflater().inflate(R.layout.user, users, false);
				
				ImageView avatar = (ImageView) badge.findViewById(R.id.avatar_user);
				String avatarField = "edu.vub.at.nfcpoker:drawable/" + getNewAvatar();
				Log.d("wePoker - Server", "Avatar for player " + avatarField);			
				int id = getResources().getIdentifier(avatarField, null, null);
				avatar.setImageDrawable(getResources().getDrawable(id));
				avatar.setAdjustViewBounds(true);
				avatar.setMaxHeight(users.getHeight());
				avatar.setMaxWidth(users.getHeight());
				
				TextView name = (TextView) badge.findViewById(R.id.playerName);
				name.setText(clientName);
				TextView money = (TextView) badge.findViewById(R.id.playerMoney);
				money.setText("\u20AC" + initialMoney);

				playerBadges.put(clientID, badge);
				
				LinearLayout.LayoutParams params = (LayoutParams) badge.getLayoutParams();
				params.setMargins(24, 0, 24, 0);
				users.addView(badge, params);
			}
		});
	}

	@Override
	public void setPlayerMoney(final Integer player, final int current) {
		runOnUiThread(new Runnable() {
			public void run() {
				View badge = playerBadges.get(player);
				if (badge != null) {
					TextView money = (TextView) badge.findViewById(R.id.playerMoney);
					money.setText("\u20AC" + current);
				}
			}
		});
	}

	@Override
	public void updatePoolMoney(int chipsPool) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removePlayer(final Integer player) {
		runOnUiThread(new Runnable() {
			public void run() {
				View badge = playerBadges.get(player);
				if (badge != null) {
					LinearLayout users_bottom = (LinearLayout) findViewById(R.id.users_bottom);
					users_bottom.removeView(badge);
					LinearLayout users_top = (LinearLayout) findViewById(R.id.users_top);
					users_top.removeView(badge);
					playerBadges.remove(player);
				}
			}
		});
	}

	@Override
	public Context getContext() {
		return this;
	}
}
