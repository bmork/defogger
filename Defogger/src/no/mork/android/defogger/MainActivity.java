package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import no.mork.android.defogger.ScannerActivity;

public class MainActivity extends Activity {
    private static String msg = "Defogger MainActivity: ";

    private static final int REQUEST_ENABLE_BT  = 1;
    private static final int REQUEST_GET_DEVICE = 2;
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt mGatt;
    private BluetoothGattService ipcamService;
    private String pincode;

    // status
    private boolean connected = false;
    private boolean locked = true;
    private boolean wifilink = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
	
	Button start_scan = (Button) findViewById(R.id.start_scan);
	start_scan.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    disconnectDevice();
		    Intent intent = new Intent(view.getContext(), ScannerActivity.class);
		    startActivityForResult(intent, REQUEST_GET_DEVICE);
  		}
            });

	EditText cmd = (EditText) findViewById(R.id.command);
	cmd.setOnEditorActionListener(new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		    if (actionId == EditorInfo.IME_ACTION_DONE) {
			runCommand(mGatt, v.getText().toString());
			return true;
		    }
		    return false;
		}
	    });
    }

    @Override
    protected void onResume() {
        super.onResume();
	getBluetoothAdapter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);

	switch (requestCode) {
	case REQUEST_ENABLE_BT:
	    if (resultCode != RESULT_OK) { // user refused to enable BT?
		setStatus("Bluetooth is disabled");
		finish();
	    }	    
		
	    break;
	case REQUEST_GET_DEVICE:
	    if (resultCode != RESULT_OK) {
		setStatus("Failed to find a camera");
		break;
	    }

	    BluetoothDevice dev = dataIntent.getExtras().getParcelable("btdevice");
	    if (dev == null) {
		setStatus("No camera selected");
		break;
	    }

	    pincode = dataIntent.getStringExtra("pincode");
	    if (pincode == null || pincode.length() < 6) {
		setStatus("Bogus pincode");
		break;
	    }
		
	    connectDevice(dev);
	    break;
	default:
	    Log.d(msg, "unknown request???");
	}
    }

    // find and enable a bluetooth adapter with LE support
    protected void getBluetoothAdapter() {
	final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
	bluetoothAdapter = bluetoothManager.getAdapter();

	if (bluetoothAdapter == null) {
	    setStatus("Bluetooth is unsupported");
            finish();
	}	    
	    
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            setStatus("No Bluetooth Low Energy support");
            finish();
        }

	if (!bluetoothAdapter.isEnabled()) {
	    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	}
    }

    // utilities
    private Map<String,String> splitKV(String kv)
    {
	Map<String,String> ret = new HashMap();

	for (String s : kv.split(";")) {
	    String[] foo = s.split("=");
	    ret.put(foo[0], foo[1]); 
	}
	return ret;
    }

    private String calculateKey(String in) {
	MessageDigest md5Hash = null;
	try {
	    md5Hash = MessageDigest.getInstance("MD5");
	} catch (NoSuchAlgorithmException e) {
 	    Log.d(msg, "Exception while encrypting to md5");
	}
	byte[] bytes = in.getBytes(StandardCharsets.UTF_8);
	md5Hash.update(bytes, 0, bytes.length);
	String ret = Base64.encodeToString(md5Hash.digest(), Base64.DEFAULT);
	return ret.substring(0, 16);
    }

    private UUID UUIDfromInt(long uuid) {
	return new UUID(uuid << 32 | 0x1000 , 0x800000805f9b34fbL);
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
		disconnectDevice();
	    }
	}
	
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
	    Log.d(msg, "onServicesDiscovered()");
	    
	    if (status != BluetoothGatt.GATT_SUCCESS) {
		setStatus("Failed to discover services");
		disconnectDevice();
		return;
	    }
	    // get the IPCam service
	    //	    ipcamService = gatt.getService(UUID.fromString("0000d001-0000-1000-8000-00805f9b34fb"));
	    ipcamService = gatt.getService(UUIDfromInt(0xd001));
	    if (ipcamService == null) {
		setStatus(gatt.getDevice().getName() + " does not support the IPCam GATT service");
		disconnectDevice();
		return;
		
	    }

	    setStatus("IPcam GATT service found");
	    notifications(gatt, true);
	    getLock(gatt);
	}

	public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    String val = c.getStringValue(0);
	    Map<String,String> kv = splitKV(val);

	    Log.d(msg, c.getUuid().toString() + " returned " + val);
	    
	    switch (code) {
	    case 0xa001: // challenge
		// already unlocked? 
		if (kv.get("M").equals("0")) {
		    setLocked(gatt, false);
		    break;
		}

		setStatus("Unlocking " + gatt.getDevice().getName());
		String key = calculateKey(gatt.getDevice().getName() + pincode + kv.get("C"));
		doUnlock(gatt, key);
		break;
	    case 0xa100:
		// starting a new sequence?
		if (kv.get("P").equals("1"))
		    multimsg = "";
		multimsg += val.split(";",3)[2];
		// repeat until result is complete
		if (!kv.get("N").equals(kv.get("P")))
		    doWifiScan(gatt);
		else
		    for (String net : multimsg.split("&"))
			Log.d(msg, net);
		break;
	    default:
		Log.d(msg, "Read unhandled characteristic: " + c.getUuid().toString());
	    }
	}
	
	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
	    Log.d(msg, c.getUuid().toString() + " changed to " + c.getStringValue(0));
	}

	public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    String val = c.getStringValue(0);
	    Map<String,String> kv = splitKV(val);
	    
	    Log.d(msg, "Write to " + c.getUuid().toString() + " status=" + status + ", value is now: " + val);
	    
	    switch (code) {
	    case 0xa001:
		if (kv.get("M").equals("0")) {
		    setLocked(gatt, false);
		    doWifiScan(gatt);
		} else {
		    setStatus("Failed to unlock " + gatt.getDevice().getName());
		}
		break;
	    default:
		Log.d(msg, "No action defined after " + c.getUuid().toString());
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
    
    private void connectDevice(BluetoothDevice device) {
	Log.d(msg, "connectDevice() " + device.getAddress());

	// reset status to default
	connected = false;
	locked = true;
	wifilink = false;
	
	setStatus("Connecting to '" + device.getName() + "' (" + device.getAddress() + ")");
	GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, true, gattClientCallback);
    }
	    
    private void disconnectDevice() {
	// reset status to default
	connected = false;
	locked = true;
	wifilink = false;

	if (mGatt == null)
	    return;
	Log.d(msg, "disconnectDevice() " + mGatt.getDevice().getAddress());
	mGatt.close();
    }

    // camera specific code
    private void setLocked(BluetoothGatt gatt, boolean lock) {
	setStatus(gatt.getDevice().getName() + " is " + (lock ? "locked" : "unlocked"));
	locked = lock;
    }

    private void notifications(BluetoothGatt gatt, boolean enable) {
	if (!connected)
	    return;
	Log.d(msg, "notifications()");
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(0xa000));
	gatt.setCharacteristicNotification(c, enable);
    }
    
    private void getLock(BluetoothGatt gatt) {
	if (!locked) {
	    Log.d(msg, "getLock() already unlocked");
	    return;
	}
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(0xa001));
	gatt.readCharacteristic(c);
    }

    private void doUnlock(BluetoothGatt gatt, String key) {
	if (!connected)
	    return;
	Log.d(msg, "doUnlock(), key is " + key);
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(0xa001));
	c.setValue("M=0;K=" + key); 
	gatt.writeCharacteristic(c);
    }

    private void readChar(BluetoothGatt gatt, int num) {
 	if (locked)
	    return;
	Log.d(msg, "reading " + String.format("%#06x", num));
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(num));
	gatt.readCharacteristic(c);
    }

    private void writeChar(BluetoothGatt gatt, int num, String val) {
 	if (locked)
	    return;
	Log.d(msg, "writing '" + val + "' to " + String.format("%#06x", num));
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(num));
	c.setValue(val);
	gatt.writeCharacteristic(c);
    }

    private void doWifiScan(BluetoothGatt gatt) {
	readChar(gatt, 0xa100);
    }

    private void getWifiLink(BluetoothGatt gatt) {
	readChar(gatt, 0xa103);
    }

    private void getIpConfig(BluetoothGatt gatt) {
	readChar(gatt, 0xa104);
    }

    private void getSysInfo(BluetoothGatt gatt) {
	readChar(gatt, 0xa200);
    }

    /*
    private void setWifi(BluetoothGatt gatt, String essid, String passwd) {
	if (wifiScanResults == null) {
	    doWifiScan(gatt);
	    return;
	}
	    
	writeChar(0xa101, "P=;N=" + pincode);
    }
    */
    
    private void setInitialPassword(BluetoothGatt gatt) {
	writeChar(gatt, 0xa201, "P=;N=" + pincode);
    }
    
    private void runCommand(BluetoothGatt gatt, String command) {
	writeChar(gatt, 0xa201, "P=" + pincode + ";N=" + pincode + "&&(" + command + ")&");
    }
}
