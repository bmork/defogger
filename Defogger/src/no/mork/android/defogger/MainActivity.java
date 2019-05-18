package no.mork.android.defogger;

import android.app.Activity;
import android.os.Bundle;

import no.mork.android.defogger.ClientActivity;

public class MainActivity extends Activity {
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      //      binding.launchClientButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this,
      //									  ClientActivity.class)));


   }
}
