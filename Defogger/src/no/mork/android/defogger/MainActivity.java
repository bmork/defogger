/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */

package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.lang.StringBuilder;

//import no.mork.android.defogger.ScannerActivity;

public class MainActivity extends Activity {
    private static String msg = "Defogger MainActivity: ";

    private static final int REQUEST_ENABLE_BT  = 1;
    private static final int REQUEST_GET_DEVICE = 2;
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private String pincode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
	Log.d(msg, "onResume()");
        super.onResume();
	getBluetoothAdapter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);

	Log.d(msg, "onActivityResult() requestCode=" + requestCode);
	Log.d(msg, "Intent is " + dataIntent);

	switch (requestCode) {
	case IntentIntegrator.REQUEST_CODE:
	    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, dataIntent);
	    if (scanResult != null)
		handleQRScanResult(scanResult);
	    break;
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

	    device = dataIntent.getExtras().getParcelable("btdevice");
	    if (device == null) {
		pincode = null;
		setStatus("No camera selected");
		break;
	    }

	    String pincode = dataIntent.getStringExtra("pincode");
	    if (pincode == null || pincode.length() < 6) {
		pincode = null;
		device = null;
		setStatus("Bogus pincode");
		break;
	    }

	    startIpCamActivity();
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

    public void startIpCamActivity() {
	Intent intent = new Intent(this, IpCamActivity.class);
	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
	intent.putExtra("pincode", pincode);
	intent.putExtra("btdevice", device);
	startActivity(intent);
    }

    public void startScannerActivity(View view) {
	Intent intent = new Intent(view.getContext(), ScannerActivity.class);
	startActivityForResult(intent, REQUEST_GET_DEVICE);
    }

    public void startQRReaderActivity(View view) {
	IntentIntegrator integrator = new IntentIntegrator(this);
	integrator.setTitleByID(R.values.qrtitle);
	integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    private void handleQRScanResult(IntentResult res) {
	if (res.getContents() == null) {
	    setStatus("No QR code scanned");
	    return;
	}
	
	Log.d(msg, "QR scan resturned: " + res.toString());

	// DCS-8000LH,A3,12345678,B0C554AABBCC,DCS-8000LH-BBCC,123456
	String[] data = res.getContents().split(",");
	if (data.length != 6 || data[3].length() != 12 || data[5].length() != 6) {
	    setStatus("Unexpected QR scan result - wrong format");
	    return;
	}

	StringBuilder mac = new StringBuilder(data[3]);
	mac.insert(10, ':');
	mac.insert(8, ':');
	mac.insert(6, ':');
	mac.insert(4, ':');
	mac.insert(2, ':');

	if (!bluetoothAdapter.checkBluetoothAddress(mac.toString())) {
	    Log.d(msg, "Got invalid MAC address from QR scan:" + mac.toString());
	    return;
	}
	device = bluetoothAdapter.getRemoteDevice(mac.toString());
	pincode = data[5];
	startIpCamActivity();
    }

    
    private void setStatus(String text) {
	Log.d(msg, "Status: " + text);
	runOnUiThread(new Runnable() {
		@Override
		public void run() {
		    TextView status = (TextView) findViewById(R.id.mainstatus);
		    status.setText(text);
		}
	    });
    }

}
