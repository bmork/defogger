package no.mork.android.defogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import no.mork.android.defogger.ScannerActivity;

public class MainActivity extends Activity {
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
		    view.getContext().startActivity(intent);
  		}
            });
	
    }
}
