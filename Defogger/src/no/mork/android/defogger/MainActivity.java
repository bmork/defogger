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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.ArrayDeque;
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
    private ArrayDeque<BluetoothGattCharacteristic> readQ;
    private ArrayDeque<BluetoothGattCharacteristic> writeQ;

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

	Button tmp = (Button) findViewById(R.id.wificonfig);
	tmp.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    getWifiConfig(mGatt);
  		}
            });

	tmp = (Button) findViewById(R.id.ipconfig);
	tmp.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    getIpConfig(mGatt);
  		}
            });

	tmp = (Button) findViewById(R.id.sysinfo);
	tmp.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    getSysInfo(mGatt);
  		}
            });
	tmp = (Button) findViewById(R.id.setup);
	tmp.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View view) {
		    getWifiLink(mGatt);
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
    private Map<String,String> splitKV(String kv, String splitter)
    {
	Map<String,String> ret = new HashMap();

	for (String s : kv.split(splitter)) {
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
	    Map<String,String> kv = splitKV(val, ";");

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
		    selectNetwork(multimsg.split("&"));
		break;
	    case 0xa101: // wificonfig
		displayWifiConfig(kv);
		break;
	    case 0xa103: // wifilink
		wifilink = kv.get("S").equals("1");
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

	    runQueues(gatt);
	}
	
	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
	    Log.d(msg, c.getUuid().toString() + " changed to " + c.getStringValue(0));
	}

	public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
	    int code = (int)(c.getUuid().getMostSignificantBits() >> 32);
	    String val = c.getStringValue(0);
	    Map<String,String> kv = splitKV(val, ";");
	    
	    Log.d(msg, "Write to " + c.getUuid().toString() + " status=" + status + ", value is now: " + val);
	    
	    switch (code) {
	    case 0xa001:
		if (kv.get("M").equals("0"))
		    setLocked(gatt, false);
		else
		    setStatus("Unlocking failed - Wrong PIN Code?");
		break;
	    default:
		Log.d(msg, "No action defined after " + c.getUuid().toString());
	    }
	    runQueues(gatt);
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
		    // TextView status = (TextView) findViewById(R.id.statustext);
		    //status.setText(text);
		}
	    });
    }

    private void displayIpConfig(Map<String,String> kv) {
	Log.d(msg, "displayIpConfig()");
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView t = (TextView) findViewById(R.id.ipaddress);
		    t.setText(kv.get("I"));
		    t = (TextView) findViewById(R.id.netmask);
		    t.setText(kv.get("N"));
		    t = (TextView)findViewById(R.id.gateway);
		    t.setText(kv.get("G"));
		    t = (TextView)findViewById(R.id.dns);
		    t.setText(kv.get("D"));
		}
	    });
    }

    private void displaySysInfo(Map<String,String> kv) {
	Log.d(msg, "displaySysInfo()");
	java.text.DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
	
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView t = (TextView) findViewById(R.id.sysname);
		    t.setText(kv.get("N"));
		    t = (TextView) findViewById(R.id.systime);
		    t.setText(dateFormat.format(new Date(1000 * Integer.parseInt(kv.get("T"))))); // milliseconds....
		    t = (TextView)findViewById(R.id.version);
		    t.setText("FW Ver: " + kv.get("F") + ", HW Ver: " + kv.get("H") + ", MyDlink Ver: " + kv.get("V"));
		    t = (TextView)findViewById(R.id.macaddress);
		    t.setText(kv.get("M"));
		}
	    });
    }

    private class NetAdapter extends ArrayAdapter<String> {
	private int res;
	
	public NetAdapter(Context context, int resource, String[] networks) {
	    super(context, resource, networks);
	    res = resource;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
	    //L=I=aaaa7,M=0,C=4,S=4,E=2,P=100
	    // Get the data item for this position
	    Map<String,String> net = splitKV(getItem(position).substring(2), ",");
	    
	    // Check if an existing view is being reused, otherwise inflate the view
	    if (convertView == null) {
		convertView = LayoutInflater.from(getContext()).inflate(res, parent, false);
	    }
	    
	    // Lookup view for data population
	    TextView ssid = (TextView) convertView.findViewById(R.id.ssid);
	    TextView channel = (TextView) convertView.findViewById(R.id.channel);
	    TextView key_mgmt = (TextView) convertView.findViewById(R.id.key_mgmt);
	    TextView proto = (TextView) convertView.findViewById(R.id.proto);
	    TextView rssi = (TextView) convertView.findViewById(R.id.rssi);

	    // Populate the data into the template view using the data object
	    ssid.setText(net.get("I"));
	    channel.setText(net.get("C"));
	    key_mgmt.setText(net.get("S"));
	    proto.setText(net.get("E"));
	    rssi.setText(net.get("P"));

	    // Return the completed view to render on screen
	    return convertView;
	}
    }
    
    private void selectNetwork(String[] networks) {
	Context ctx = this;
	Log.d(msg, "displayWifiConfig()");
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    ArrayAdapter<String> itemsAdapter = new NetAdapter(ctx, R.layout.netitem, networks);
		    ListView listView = (ListView) findViewById(R.id.networks);
		    listView.setAdapter(itemsAdapter);
		}
	    });
    }
	
    private void connectDevice(BluetoothDevice device) {
	Log.d(msg, "connectDevice() " + device.getAddress());

	// create queues
	readQ = new ArrayDeque();
	writeQ = new ArrayDeque();
	
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
	if (lock == locked)
	    return;
	
	locked = lock;
 	setStatus(gatt.getDevice().getName() + " is " + (lock ? "locked" : "unlocked"));
	if (locked)
	    return;

	/* collect current config after unlocking */
	getWifiConfig(gatt);
	getWifiLink(gatt);
	getIpConfig(gatt);
	getSysInfo(gatt);
	doWifiScan(gatt);
    }

    private void notifications(BluetoothGatt gatt, boolean enable) {
	if (!connected)
	    return;
	Log.d(msg, "notifications()");
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(0xa000));
	if (!gatt.setCharacteristicNotification(c, enable))
	    Log.d(msg, "failed to enable notifications");
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

    private BluetoothGattCharacteristic runQueues(BluetoothGatt gatt) {
	BluetoothGattCharacteristic c = readQ.peekFirst();
	if (c != null && gatt.readCharacteristic(c))
	    return readQ.removeFirst();
	c = writeQ.peekFirst();
	if (c != null && gatt.writeCharacteristic(c))
	    return writeQ.removeFirst();
	return null;
    }

    private void readChar(BluetoothGatt gatt, int num) {
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(num));
 	if (locked) {
	    Log.d(msg, "camera is locked");
	    readQ.offer(c);
	    return;
	}

	Log.d(msg, "reading " + String.format("%#06x", num));
	if (!gatt.readCharacteristic(c))
	    readQ.offer(c);
    }

    private void writeChar(BluetoothGatt gatt, int num, String val) {
	BluetoothGattCharacteristic c = ipcamService.getCharacteristic(UUIDfromInt(num));
	c.setValue(val);
 	if (locked) {
	    Log.d(msg, "camera is locked");
	    writeQ.offer(c);
 	    return;
	}
	Log.d(msg, "writing '" + val + "' to " + String.format("%#06x", num));
	if (!gatt.writeCharacteristic(c))
	    writeQ.offer(c);
    }

    private void doWifiScan(BluetoothGatt gatt) {
	readChar(gatt, 0xa100);
    }

    private void getWifiConfig(BluetoothGatt gatt) {
	readChar(gatt, 0xa101);
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
