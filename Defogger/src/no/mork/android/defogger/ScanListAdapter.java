package no.mork.android.defogger;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;

// originally from https://developer.android.com/guide/topics/ui/layout/recyclerview
// but converted to simpler ArrayAdapter using https://developer.android.com/guide/topics/ui/declaring-layout.html#FillingTheLayout


public class ScanListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> mObjects;
    private Context mCtx;
    private LayoutInflater mInflater;
    private int mRes;
    private int mTxtId;
    
    public ScanListAdapter(Context context, int resource, int textViewResourceId)  {
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
	return convertView;
    }

    public void add(BluetoothDevice device) {
	if (device.getName() != null && mObjects.indexOf(device) < 0) { // avoid duplicates and ignore nameless devices
	    mObjects.add(device);
	    notifyDataSetChanged();
	}
    }
}
