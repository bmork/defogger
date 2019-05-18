package no.mork.android.defogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
		    startActivityForResult(intent, 1);
  		}
            });
	
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);

        switch (requestCode)
        {
            case 1:
		TextView hello_text = (TextView) findViewById(R.id.hello_text);
                if(resultCode == RESULT_OK)
                {
                    String messageReturn = dataIntent.getStringExtra("scan_ret");
		    hello_text.setText(messageReturn);
		}
        }
    }
}
