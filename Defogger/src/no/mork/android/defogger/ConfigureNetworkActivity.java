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
	intent.putExtra("netconf", "");
	setResult(RESULT_CANCELED, intent);
    }

    public void returnConfigResult(String config) {
	Log.d(msg, "returnConfigResult()");
 	Intent intent = new Intent();
	if (config != null) {
	    intent.putExtra("netconf", config);
	    setResult(RESULT_OK, intent);
	} else {
	    intent.putExtra("netconf", "");
	    setResult(RESULT_CANCELED, intent);
	}
	finish();
    }

    public void doFinish(View v) {
	//                cfg = "M=" + net["M"] + ";I=" + essid + ";S=" + net["S"] + ";E=" + net["E"] + ";K=" + passwd
	// L=I=Telenor_Guest,M=0,C=11,S=0,E=0,P=74
	// L=I=Telenor_employee,M=0,C=11,S=4,E=2,P=74
  	if (selected == null)
	    returnConfigResult(null);

	Map<String,String> kv = Util.splitKV(selected, ".");
    	EditText edit = (EditText) findViewById(R.id.password);
	String password = edit.getText().toString();
	String ret = null;
	String ssid = kv.get("I"); // FIXME: allow entering SSID in edit field
	if (password == null)
	    password = "";

	/* assume open network if password is empty? */
	if (password.length() == 0 && (!kv.get("S").equals("0") || !kv.get("E").equals("0")))
	    returnConfigResult(null);
	else
	    ret = "M=0;I=" + ssid + ";S=" + kv.get("S") + ";E=" + kv.get("E") + ";K=" + password;
	
	returnConfigResult(ret);
    }
    
    private class NetAdapter extends ArrayAdapter {
       	private int resource;
	
	public NetAdapter(Context context, int resource, String[] networks) {
	    super(context, resource, networks);
	    this.resource = resource;
	}
	
   }
}
