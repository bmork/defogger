package no.mork.android.defogger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ScannerActivity extends Activity {
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      //setContentView(R.layout.scanning);

      CharSequence text = "Hello toast!";

      Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
      Intent intent = new Intent();
      intent.putExtra("message_return", "This data is returned when scan activity is finished.");
      setResult(RESULT_OK, intent);
      finish();
   }
}
