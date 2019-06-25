/*
 *  SPDX-License-Identifier: GPL-3.0-only
 *  Copyright (c) 2019  Bjørn Mork <bjorn@mork.no>
 */

package no.mork.android.defogger;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;


public class HttpService extends Service {
    private final IBinder mBinder = new MyBinder();
    private int counter = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        addResultValues();
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        addResultValues();
        return mBinder;
    }

    public class MyBinder extends Binder {
        HttpService getService() {
            return HttpService.this;
        }
    }

    private void addResultValues() {
        counter++;
        if (counter == Integer.MAX_VALUE) {
            counter = 0;
        }
    }
}
