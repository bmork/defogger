package no.mork.android.defogger;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.widget.ArrayAdapter;

// originally from https://developer.android.com/guide/topics/ui/layout/recyclerview
// but converted to simpler ArrayAdapter using https://developer.android.com/guide/topics/ui/declaring-layout.html#FillingTheLayout

public class ScanListAdapter extends ArrayAdapter<BluetoothDevice> {

    public ScanListAdapter(Context context, int resource, int textViewResourceId)  {
	 super(context, resource, textViewResourceId);
    }
}
