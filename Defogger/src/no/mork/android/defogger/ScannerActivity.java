package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;


// lots of nice examples: https://www.programcreek.com/java-api-examples/index.php?api=android.bluetooth.le.ScanCallback

public class ScannerActivity extends Activity {
    private static String msg = "Defogger Scanning: ";
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter bluetoothAdapter;
    private boolean mScanning;
    private Handler handler;
    private ScanCallback leScanCallback;
    private BluetoothLeScanner btScanner;
    private ScanListAdapter scanlistAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_scanner);

        ListView listView = (ListView) findViewById(R.id.scanlist_view);
	scanlistAdapter = new ScanListAdapter(this, R.layout.scanitem, R.id.scanitem);
	listView.setAdapter(scanlistAdapter);
	scanlistAdapter.addDevice("foo");
	
	bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	btScanner = bluetoothAdapter.getBluetoothLeScanner();

	if (btScanner == null) {
	    Log.d(msg, "getBluetoothLeScanner() returned NULL");
	}
	leScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
		    Log.d(msg, "onScanResult()");
		    super.onScanResult(callbackType, result);
		    scanlistAdapter.addDevice(result.getDevice().getAddress());
		}
	    };
	
	CharSequence text = "Hello toast!";

	Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
	Intent intent = new Intent();
	intent.putExtra("scan_ret", "This data is returned when scan activity is finished.");
	setResult(RESULT_OK, intent);
    }

    @Override
    protected void onResume() {
	super.onResume();
	//scanForCamera(true);
	//finish();
    }

    protected void scanForCamera(final boolean enable) {
	mScanning = enable;
	Log.d(msg, "entered scanForCamera()");
	if (btScanner == null) {
	    return;
	}
 	if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    btScanner.stopScan(leScanCallback);
                }
		}, SCAN_PERIOD);

	    Log.d(msg, "starting scan()");
            btScanner.startScan(leScanCallback);
	    Log.d(msg, "scan started()");
        } else {
            btScanner.stopScan(leScanCallback);
        }
    }
}
