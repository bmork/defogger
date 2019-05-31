package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
    private UUID ipcamService = UUID.fromString("0000d001-0000-1000-8000-00805f9b34fb");

    private static final int REQUEST_ENABLE_BT = 0x1042;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt mGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
	
	Button start_scan = (Button) findViewById(R.id.start_scan);
	//      button2 = (Button) findViewById(R.id.button2);
	
	start_scan.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    disconnectDevice();
		    Intent intent = new Intent(view.getContext(), ScannerActivity.class);
		    startActivityForResult(intent, R.id.hello_text);
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
	BluetoothDevice dev;

        super.onActivityResult(requestCode, resultCode, dataIntent);
	switch (requestCode) {
	case REQUEST_ENABLE_BT:
	    if (resultCode != RESULT_OK) { // user refused to enable BT?
		// logError("BT disabled.");
		finish();
	    }	    
		
	    break;
	default:
	    dev = dataIntent.getExtras().getParcelable("btdevice");

	    TextView hello_text = (TextView) findViewById(requestCode);
	    //	    String messageReturn = resultCode == RESULT_OK ? dataIntent.getStringExtra("scan_ret") : "not OK";

	    String messageReturn = "got: " + dev.getAddress() + " - " + dev.getName();
	    hello_text.setText(messageReturn);
	    connectDevice(dev);
	}
    }

    // find and enable a bluetooth adapter with LE support
    protected void getBluetoothAdapter() {
	final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
	bluetoothAdapter = bluetoothManager.getAdapter();

 	
	// Bluetooth is not supported?
	if (bluetoothAdapter == null) {
	    // logError("BT unsupported.");
            finish();
	}	    
	    
       // Check low energy support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Get a newer device
            // logError("No LE Support.");
            finish();
        }

	// Request user permission to enable Bluetooth.
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

    // Gatt connection

    private class GattClientCallback extends BluetoothGattCallback {
	private String multimsg;

	public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
	    Log.d(msg, "onConnectionStateChange() " + status + " " + newState);
 	    gatt.discoverServices();
	}
	
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
	    List<BluetoothGattService> serviceList = gatt.getServices();
	    BluetoothGattService s;
	    
	    for (BluetoothGattService service : serviceList) {
		Log.d(msg, service.getUuid().toString());
 	    }

	    // FIXME: bail out if not found
	    //	    s = getIPCamService();

	    // build a map of code to Characteristic
	    //	    Map<BluetoothGattCharacteristic, > cmap = new HashMap();
	    // for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
	    //		int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    //	Log.d(msg, "ipcam char: " + c.getUuid().toString() + String.format(" - %#06x", code));

		//		cmap.put(c, code);
	    //}

	    notifications(true);
	    getLock();
	}

	public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    Map<String,String> kv = splitKV(c.getStringValue(0));

	    Log.d(msg, c.getUuid().toString() + " read " + c.getStringValue(0));
	    
	    switch (code) {
	    case 0xa001:
		EditText pincode = (EditText) findViewById(R.id.pincode);

		Log.d(msg, "pincode is " + pincode.getText());

		String hashit = gatt.getDevice().getName() + pincode.getText() + kv.get("C");
		Log.d(msg, "hashit string is " + hashit);
		String key = calculateKey(hashit);

		doUnlock(key);
		break;
	    case 0xa100:
		multimsg += c.getStringValue(0).split(";",3)[2];
		// repeat until result is complete
		if (!kv.get("N").equals(kv.get("P")))
		    doWifiScan();
		else
		    for (String net : multimsg.split("&")) {
			Log.d(msg, net);
		    }
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
	    Map<String,String> kv = splitKV(c.getStringValue(0));

	    Log.d(msg, "Write to " + c.getUuid().toString() + " status=" + status + ", value is now: " + c.getStringValue(0));

	    switch (code) {
	    case 0xa001:
		multimsg = "";
		doWifiScan();
		break;
	    default:
		Log.d(msg, "No action defined after " + c.getUuid().toString());
	    }
	}
    }
    
    private void connectDevice(BluetoothDevice device) {
	Log.d(msg, "connectDevice() " + device.getAddress());
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, true, gattClientCallback);
    }

    private void disconnectDevice() {
	if (mGatt == null)
	    return;
	Log.d(msg, "disconnectDevice() " + mGatt.getDevice().getAddress());
	mGatt.close();
    }


    // camera specific code
    private BluetoothGattService getIPCamService() {
	// FIXME: bail out if not found
	return mGatt.getService(ipcamService);
    }

    private void notifications(boolean enable) {
    	    BluetoothGattCharacteristic a000 = getIPCamService().getCharacteristic(UUID.fromString("0000a000-0000-1000-8000-00805f9b34fb"));
	    mGatt.setCharacteristicNotification(a000, enable);
    }
    
    private void getLock() {
	BluetoothGattCharacteristic a001 = getIPCamService().getCharacteristic(UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb"));
	mGatt.readCharacteristic(a001);
    }

    private void doUnlock(String key) {
	Log.d(msg, "doUnlock(), key is " + key);
	BluetoothGattCharacteristic a001 = getIPCamService().getCharacteristic(UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb"));
	a001.setValue("M=0;K=" + key); 
	mGatt.writeCharacteristic(a001);
    }

    private void doWifiScan() {
	Log.d(msg, "doWifiScan()");
	BluetoothGattCharacteristic a100 = getIPCamService().getCharacteristic(UUID.fromString("0000a100-0000-1000-8000-00805f9b34fb"));
	mGatt.readCharacteristic(a100);
    }
}
