package no.mork.android.defogger;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

public class ScannerActivity extends Activity {
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      //setContentView(R.layout.activity_main);

      CharSequence text = "Hello toast!";

      Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
      finish();
   }
}
