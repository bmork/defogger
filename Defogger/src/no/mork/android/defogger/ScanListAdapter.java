/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */
package no.mork.android.defogger;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import no.mork.android.defogger.ScannerActivity;

public class ScanListAdapter extends BaseAdapter {
    private static String msg = "Defogger Adapter: ";
    private ArrayList<BluetoothDevice> mObjects;
    private Context mCtx;
    private LayoutInflater mInflater;
    private int mRes;
    private int mTxtId;
    
    public ScanListAdapter(Context context, int resource, int textViewResourceId)  {
	mCtx = context;
	mObjects = new ArrayList<BluetoothDevice>();
	mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	mRes = resource;
	mTxtId = textViewResourceId;
    }

    @Override
    public int getCount() {
	return mObjects.size() ;
    }

    @Override
    public BluetoothDevice getItem(int position) {
	return mObjects.get(position);
    }

    @Override
    public long getItemId(int position) {
	return getItem(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {
	BluetoothDevice device = getItem(position);

	if (convertView == null) {
	    convertView = mInflater.inflate(mRes, container, false);
	}
	
	((TextView) convertView.findViewById(mTxtId)).setText(device.getAddress() + " - " + device.getName());

	// react when selecting iteam
	convertView.setOnClickListener(new OnClickListener() {
		private BluetoothDevice ret = device;
		private ScannerActivity c = (ScannerActivity)mCtx;

		@Override
		public void onClick(View v) {
		    Log.d(msg, "ScanListAdapter: onClick() will return " + ret.getName());
		    c.returnScanResult(ret);
		}
	    });

	return convertView;
    }

    public void add(BluetoothDevice device) {

	// FIXME: Export methods to allow moving this test to the caller
	if (device.getName() != null && mObjects.indexOf(device) < 0) { // avoid duplicates and ignore nameless devices
	    mObjects.add(device);
	    notifyDataSetChanged();
	}
    }
}
