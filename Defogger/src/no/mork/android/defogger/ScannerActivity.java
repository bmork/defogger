/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class ScannerActivity extends Activity implements Runnable {
    private static String msg = "Defogger Scanning: ";
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private BluetoothLeScanner btScanner;
    private boolean mScanning;
    private ScanCallback leScanCallback;
    private ScanAdapter scanlistAdapter;

    private class BtleScanCallback extends ScanCallback {
	@Override
	public void onScanResult(int callbackType, ScanResult result) {
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
    };

    private class ScanAdapter extends ArrayAdapter<BluetoothDevice> {
	private ScannerActivity ctx;
       	private int resource;
	
	public ScanAdapter(Context context, int resource) {
	    super(context, resource);
	    ctx = (ScannerActivity)context;
	    this.resource = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
	    BluetoothDevice device = getItem(position);

	    if (convertView == null) {
		convertView = LayoutInflater.from(ctx).inflate(resource, parent, false);
	    }
	
	    TextView txt = (TextView) convertView.findViewById(R.id.scanitem);
	    txt.setText(device.getAddress() + " - " + device.getName());

	    // react when selecting iteam
	    convertView.setOnClickListener(new OnClickListener() {
		    private BluetoothDevice ret = device;

		    @Override
		    public void onClick(View v) {
			Log.d(msg, "ScanListAdapter: onClick() will return " + ret.getName());
			ctx.returnScanResult(ret);
		    }
		});

	    return convertView;
	}
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_scanner);

        ListView listView = (ListView) findViewById(R.id.scanlist_view);
	scanlistAdapter = new ScanAdapter(this, R.layout.item_scan);
	listView.setAdapter(scanlistAdapter);
	
	leScanCallback = new BtleScanCallback();
    }

    @Override
    protected void onResume() {
	super.onResume();
	startScan();
    }

    @Override
    public void run() {
	stopScan();
    }

    private void addScanResult(ScanResult result) {
	BluetoothDevice device = result.getDevice();

	/* filter result manually, since the filter API is dysfunctional */
	if (device.getName() == null || scanlistAdapter.getPosition(device) >=0) // avoid duplicates and ignore nameless devices
	    return;

	/* FIXME: further filtering on camera service */
	scanlistAdapter.add(device);
    }
    
    public void returnScanResult(BluetoothDevice device) {
	Log.d(msg, "returnScanResult()");
	stopScan();

	EditText pincode = (EditText) findViewById(R.id.pincode);
	Intent intent = new Intent();
	intent.putExtra("pincode",  pincode.getText().toString());
	intent.putExtra("btdevice", device);
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
