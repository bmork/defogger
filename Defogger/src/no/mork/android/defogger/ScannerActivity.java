package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

// lots of nice examples: https://www.programcreek.com/java-api-examples/index.php?api=android.bluetooth.le.ScanCallback

public class ScannerActivity extends Activity implements Runnable {
    private static String msg = "Defogger Scanning: ";
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private BluetoothLeScanner btScanner;
    private boolean mScanning;
    private ScanCallback leScanCallback;
    private ScanListAdapter scanlistAdapter;

    private class BtleScanCallback extends ScanCallback {
	
        private ScanListAdapter mScanResults;

	BtleScanCallback(ScanListAdapter scanResults) {
            mScanResults = scanResults;
        }

	@Override
	public void onScanResult(int callbackType, ScanResult result) {
	    Log.d(msg, "onScanResult(): " + callbackType);
	    super.onScanResult(callbackType, result);
	    addScanResult(result);
	}

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
	    Log.d(msg, "onBatchScanResults()");
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(msg, "Failed with code " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
	    Log.d(msg, "adding " + device.toString());
	    mScanResults.add(device);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_scanner);

        ListView listView = (ListView) findViewById(R.id.scanlist_view);
	scanlistAdapter = new ScanListAdapter(this, R.layout.scanitem, R.id.scanitem);
	listView.setAdapter(scanlistAdapter);
	
	leScanCallback = new BtleScanCallback(scanlistAdapter);
	
	CharSequence text = "Hello toast!";

	Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
	Intent intent = new Intent();
	intent.putExtra("scan_ret", "This data is returned when scan activity is finished.");
	setResult(RESULT_OK, intent);
    }

    @Override
    protected void onResume() {
	super.onResume();
	startScan();
	//finish();
    }

    @Override
    public void run() {
	stopScan();
    }

    public void returnScanResult(BluetoothDevice device) {
	Log.d(msg, "returnScanResult()");
 	Intent intent = new Intent();
	intent.putExtra("scan_ret", device.toString());
	setResult(RESULT_OK, intent);
	finish();
    }
    
    private void startScan() {
	btScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();

        ScanSettings settings = new ScanSettings.Builder()
	    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
	    //.setCallbackType(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
	    .build();

	// Note: Filtering does not work.  Must filter in the callback an
        List<ScanFilter> filters = new ArrayList<>();
	//	filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("0000d001-0000-1000-8000-00805f9b34fb")).build());
	filters.add(new ScanFilter.Builder().build());

	Log.d(msg, "entered scanForCamera()");
	if (btScanner == null) {
	    Log.d(msg, "getBluetoothLeScanner() returned NULL");
	}
	
	btScanner.startScan(filters, settings, leScanCallback);

	Handler mHandler = new Handler();
	mHandler.postDelayed(this, SCAN_PERIOD);
	mScanning = true;
	Log.d(msg, "started scanning");
    }

    private void stopScan() {
	btScanner.stopScan(leScanCallback);
        leScanCallback = null;
	mScanning = false;
	Log.d(msg, "stopped scanning");
    }


}
