package com.virion.stockopname;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;

public class MainActivity extends Activity {
    WebView w;
    ValueCallback<Uri[]> f;
    static final int C = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        
        w = findViewById(R.id.webView);
        WebSettings s = w.getSettings();
        
        // Pengaturan WebView agar script, DOM storage, dan fitur kamera/file berjalan lancar
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String o, GeolocationPermissions.Callback c) {
                c.invoke(o, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams q) {
                f = c;
                try {
                    startActivityForResult(q.createIntent(), C);
                    return true;
                } catch (Exception e) {
                    f = null;
                    return false;
                }
            }
        });

        // Meminta izin akses kamera, lokasi, dan internet secara runtime
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, 10);
        }

        // Memuat halaman web dari GitHub Pages StockOpname Anda
        w.loadUrl("https://virionbookstore.github.io/StockOpname/");
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == C && f != null) {
            f.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d));
            f = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (w.canGoBack()) {
            w.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

