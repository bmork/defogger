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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.List;
import java.util.UUID;

import no.mork.android.defogger.ScannerActivity;

public class MainActivity extends Activity implements GattClientActionListener {
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



    // Gatt connection

    private class GattClientCallback extends BluetoothGattCallback {
	private GattClientActionListener mClientActionListener;

	public GattClientCallback(GattClientActionListener clientActionListener) {
	    mClientActionListener = clientActionListener;
	}

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

	    s = gatt.getService(ipcamService);
	    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
		Log.d(msg, "ipcam char: " + c.getUuid().toString());
 	    }
	}
    }
    
    private void connectDevice(BluetoothDevice device) {
	Log.d(msg, "connectDevice() " + device.getAddress());
        GattClientCallback gattClientCallback = new GattClientCallback(this);
        mGatt = device.connectGatt(this, true, gattClientCallback);
    }


    // abstract GattClientActionListener methods

    @Override
    public void log(String m) {
	Log.d(msg, m);
    }

    @Override
    public void logError(String m) {
	Log.d(msg, "Error: " + m);
    }

    @Override
    public void setConnected(boolean connected) {
	Log.d(msg, "setConnected()");
    }

    @Override
    public void initializeTime() {
	Log.d(msg, "initializeTime()");
    }

    @Override
    public void initializeEcho() {
	Log.d(msg, "initializeEcho()");
    }

    @Override
    public void disconnectGattServer() {
	Log.d(msg, "disconnectGattServer()");
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

}
