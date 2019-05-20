package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;  
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;


// lots of nice examples: https://www.programcreek.com/java-api-examples/index.php?api=android.bluetooth.le.ScanCallback

public class ScannerActivity extends Activity {

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter bluetoothAdapter;
    private boolean mScanning;
    private Handler handler;
    private ScanCallback leScanCallback;
    private BluetoothLeScanner btScanner;

    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_scanner);

        recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new ScanListAdapter(myDataset);
        recyclerView.setAdapter(mAdapter);
 
	bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	btScanner = bluetoothAdapter.getBluetoothLeScanner();

	leScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
		    super.onScanResult(callbackType, result);
		    mAdapter.addDevice(result.getDevice().getAddress());
		}
	    };
	
	CharSequence text = "Hello toast!";
	
	Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
	Intent intent = new Intent();
	intent.putExtra("scan_ret", "This data is returned when scan activity is finished.");
	setResult(RESULT_OK, intent);
	finish();
    }

    protected void scanForCamera(final boolean enable) {
	mScanning = enable;
 	if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    btScanner.stopScan(leScanCallback);
                }
		}, SCAN_PERIOD);

            btScanner.startScan(leScanCallback);
        } else {
            btScanner.stopScan(leScanCallback);
        }
    }
}
