/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */

package no.mork.android.defogger;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Util {
    private static String msg = "Defogger Util: ";

    public static Map<String,String> splitKV(String kv, String splitter)
    {
	Map<String,String> ret = new HashMap();
	
	if (kv != null)
	    for (String s : kv.split(splitter)) {
		String[] foo = s.split("=");
		ret.put(foo[0], foo[1]); 
	    }

	return ret;
    }

    public static String calculateKey(String in) {
	MessageDigest md5Hash = null;
	try {
	    md5Hash = MessageDigest.getInstance("MD5");
	} catch (NoSuchAlgorithmException e) {
 	    Log.d(msg, "Exception while encrypting to md5");
	}
	byte[] bytes = in.getBytes(StandardCharsets.UTF_8);
	md5Hash.update(bytes, 0, bytes.length);
	String ret = Base64.encodeToString(md5Hash.digest(), Base64.DEFAULT);
	return ret.substring(0, 16);
    }

    public static UUID UUIDfromInt(long uuid) {
	return new UUID(uuid << 32 | 0x1000 , 0x800000805f9b34fbL);
    }
}
