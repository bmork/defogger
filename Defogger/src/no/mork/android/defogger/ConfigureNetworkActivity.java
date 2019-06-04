/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */

package no.mork.android.defogger;

import android.app.Activity;
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
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigureNetworkActivity extends Activity {
    private static String msg = "Defogger Network Config: ";
    private ArrayAdapter<String> networklist;
    private String selected = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	Log.d(msg, "onCreate()");
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_configurenetwork);

	// Get the Intent that started this activity and extract parameters
	Intent intent = getIntent();
	String[] networks = intent.getStringArrayExtra("networks");
	if (networks == null)
	    networks = new String[0];

	networklist = new ArrayAdapter<String> (this, R.layout.item_net, networks) {
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		    // Check if an existing view is being reused, otherwise inflate the view
		    if (convertView == null) {
			convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_net, parent, false);
		    }
	    
		    //L=I=aaaa7,M=0,C=4,S=4,E=2,P=100
		    // Get the data item for this position
		    String txt = getItem(position);
		    TextView ssid = (TextView) convertView.findViewById(R.id.ssid);
		    ssid.setText(txt.substring(4).split(",", 1)[0]);
		    
		    // react when selecting item
		    convertView.setOnClickListener(new OnClickListener() {
			    private String ret = getItem(position).substring(2);

			    @Override
			    public void onClick(View v) {
				Log.d(msg, "onClick() will return " + ret);
				selected = ret;
			    }
			});

		    return convertView;
		}
	    };
 
	ListView listView = (ListView) findViewById(R.id.networks);
	listView.setAdapter(networklist);
	setResult(RESULT_CANCELED); // default
    }

    public void returnConfigResult(String config) {
	Log.d(msg, "returnConfigResult(): " + config);
 	Intent intent = new Intent();
	if (config != null) {
	    intent.putExtra("netconf", config);
	    setResult(RESULT_OK, intent);
	}
	finish();
    }

    public void doFinish(View v) {
	//                cfg = "M=" + net["M"] + ";I=" + essid + ";S=" + net["S"] + ";E=" + net["E"] + ";K=" + passwd
	// L=I=Telenor_Guest,M=0,C=11,S=0,E=0,P=74
	// L=I=Telenor_employee,M=0,C=11,S=4,E=2,P=74
  	if (selected == null)
	    returnConfigResult(null);

	Log.d(msg, "selected is " + selected);

	Map<String,String> kv = Util.splitKV(selected, ",");
    	EditText edit = (EditText) findViewById(R.id.password);
	String password = edit.getText().toString();

    	edit = (EditText) findViewById(R.id.ssid);
	String ssid = kv.containsKey("I") ? kv.get("I") : edit.getText().toString();

	Log.d(msg, "kv is " + kv);
	
	/* returning empty ssid is not allowed */
	if (ssid == null || ssid.length() == 0)
	    returnConfigResult(null);

	if (password == null)
	    password = "";

	/* set defaults, assuming open network if password is empty and ssid was not found */
	int S = kv.containsKey("S") ? Integer.parseInt(kv.get("S")) : password.length() > 0 ? 4 : 0;
	int E = kv.containsKey("E") ? Integer.parseInt(kv.get("E")) : password.length() > 0 ? 2 : 0;

	/* password is required unless open network */
	if (password.length() == 0 && (S != 0 || E !=0))
	    returnConfigResult(null);
	else
	    returnConfigResult("M=0;I=" + ssid + ";S=" + S + ";E=" + E + ";K=" + password);
    }
    
    private class NetAdapter extends ArrayAdapter {
       	private int resource;
	
	public NetAdapter(Context context, int resource, String[] networks) {
	    super(context, resource, networks);
	    this.resource = resource;
	}
	
   }
}
