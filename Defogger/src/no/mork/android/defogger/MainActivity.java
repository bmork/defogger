package no.mork.android.defogger;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import no.mork.android.defogger.ScannerActivity;

public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 0x1042;
    private BluetoothAdapter bluetoothAdapter;

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
        super.onActivityResult(requestCode, resultCode, dataIntent);
	switch (requestCode) {
	case REQUEST_ENABLE_BT:
	    if (resultCode != RESULT_OK) { // user refused to enable BT?
		// logError("BT disabled.");
		finish();
	    }	    
		
	    break;
	default:
	    TextView hello_text = (TextView) findViewById(requestCode);
	    String messageReturn = resultCode == RESULT_OK ? dataIntent.getStringExtra("scan_ret") : "not OK";
	    hello_text.setText(messageReturn);
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
}
