package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
//import android.widget.ArrayAdapter;
import android.widget.Toast;

//class LeDeviceListAdapter extends ArrayAdapter {
//    protected void addDevice(final BluetoothDevice device);
//}

public class ScannerActivity extends Activity {

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter bluetoothAdapter;
    private boolean mScanning;
    private Handler handler;
    //  private LeDeviceListAdapter leDeviceListAdapter;
    private BluetoothAdapter.LeScanCallback leScanCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	//setContentView(R.layout.scanning);

	bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	leScanCallback = new BluetoothAdapter.LeScanCallback() {
		
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
		    runOnUiThread(new Runnable() {
			    
			    @Override
			    public void run() {
				//				leDeviceListAdapter.addDevice(device);
				// leDeviceListAdapter.notifyDataSetChanged();
			    }
			});
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
	if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothAdapter.stopLeScan(leScanCallback);
                }
		}, SCAN_PERIOD);

            mScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

}
