package com.virion.stockopname;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.webkit.*;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {
    private WebView w;
    private ValueCallback<Uri[]> f;
    private static final int C = 1001;
    private static final int SAVE_FILE_REQUEST = 1002;
    private boolean doubleBackToExitPressedOnce = false;
    private File pendingSaveFile;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        w = findViewById(R.id.webView);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        w.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        w.addJavascriptInterface(new AndroidExportBridge(), "AndroidInterface");

        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://virionbookstore.github.io/StockOpname/")) {
                    installApkExportOverride();
                }
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String o, GeolocationPermissions.Callback c) {
                c.invoke(o, true, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
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

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, 10);
        }

        w.loadUrl("https://virionbookstore.github.io/StockOpname/");
    }

    private void installApkExportOverride() {
        String script =
            "(function() {" +
            "if(window.__virionApkExportInstalled)return;" +
            "window.__virionApkExportInstalled=true;" +
            "var btns=document.querySelectorAll('button[onclick=\\\"exportRiwayatToExcel()\\\"]');" +
            "for(var i=0;i<btns.length;i++){btns[i].innerHTML='📤 Bagikan';btns[i].title='Bagikan / Simpan Excel';}" +
            "window.exportRiwayatToExcel=function(){" +
            "var dataToExport=getFilteredHistoryData();" +
            "if(!dataToExport||dataToExport.length===0){alert('Tidak ada data riwayat untuk diexport!');return;}" +
            "var rows=dataToExport.map(function(h,index){return {'No':index+1,'Waktu':h.waktu,'Petugas':h.petugas||'-','Sesi SO':h.judulSesi||'1','SKU / Barcode':h.sku,'Nama Barang':h.namaBarang,'Stok Sistem':h.stokSistem,'Stok Fisik':h.stokFisik,'Selisih':h.selisih,'Harga (Rp)':h.harga||0,'Supplier':h.supplier||'-','Catatan':h.keterangan||'-'};});" +
            "var ws=XLSX.utils.json_to_sheet(rows);var wb=XLSX.utils.book_new();XLSX.utils.book_append_sheet(wb,ws,'Laporan Stock Opname');" +
            "var roleStr=currentUserRole.toLowerCase();var fileLabel=currentUserNama;" +
            "if(roleStr!=='user'&&roleStr!=='staff'){var fd=document.getElementById('adminHistoryFilter');fileLabel=fd?fd.value:'Semua';}" +
            "var dateStr=new Date().toISOString().slice(0,10);var fileName='Laporan_SO_'+fileLabel+'_'+dateStr+'.xlsx';" +
            "var base64=XLSX.write(wb,{bookType:'xlsx',type:'base64'});" +
            "if(window.AndroidInterface&&window.AndroidInterface.exportExcel){window.AndroidInterface.exportExcel(fileName,base64);}else{XLSX.writeFile(wb,fileName);}" +
            "};})();";
        w.evaluateJavascript(script, null);
    }

    private class AndroidExportBridge {
        @JavascriptInterface
        public void exportExcel(final String fileName, final String base64Data) {
            runOnUiThread(() -> {
                try {
                    String safeName = sanitizeFileName(fileName);
                    if (!safeName.toLowerCase().endsWith(".xlsx")) safeName += ".xlsx";
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Folder cache tidak dapat dibuat");
                    File file = new File(dir, safeName);
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    FileOutputStream out = new FileOutputStream(file);
                    out.write(bytes);
                    out.close();
                    showExportOptions(file);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Gagal menyiapkan Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "Laporan_StockOpname.xlsx";
        return name.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    private void showExportOptions(final File file) {
        new AlertDialog.Builder(this)
            .setTitle("File Excel siap")
            .setItems(new String[]{"Bagikan ke aplikasi lain", "Simpan file di HP"}, (dialog, which) -> {
                if (which == 0) shareFile(file);
                else {
                    pendingSaveFile = file;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    intent.putExtra(Intent.EXTRA_TITLE, file.getName());
                    startActivityForResult(intent, SAVE_FILE_REQUEST);
                }
            }).show();
    }

    private void shareFile(File file) {
        Uri uri = Uri.parse("content://" + getPackageName() + ".sharedfiles/" + Uri.encode(file.getName()));
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(android.content.ClipData.newRawUri("Excel", uri));
        startActivity(Intent.createChooser(share, "Bagikan file Excel"));
    }

    private void saveFileToUri(Uri uri) {
        File source = pendingSaveFile;
        pendingSaveFile = null;
        if (uri == null || source == null) return;
        try {
            java.io.OutputStream output = getContentResolver().openOutputStream(uri);
            if (output == null) throw new IOException("Tidak dapat membuka lokasi penyimpanan");
            java.io.InputStream input = new java.io.FileInputStream(source);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
            input.close();
            output.close();
            Toast.makeText(this, "File berhasil disimpan.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == C && f != null) {
            f.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d));
            f = null;
        } else if (r == SAVE_FILE_REQUEST) {
            saveFileToUri(d == null ? null : d.getData());
        }
    }

    @Override
    public void onBackPressed() {
        if (w.canGoBack()) w.goBack();
        else {
            if (doubleBackToExitPressedOnce) { super.onBackPressed(); return; }
            doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "Tekan sekali lagi untuk keluar aplikasi", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
        }
    }
}
