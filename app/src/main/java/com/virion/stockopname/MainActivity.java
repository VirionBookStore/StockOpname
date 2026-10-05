package com.virion.stockopname;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.content.SharedPreferences;
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
    private SharedPreferences sessionPrefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        sessionPrefs = getSharedPreferences("virion_so_session", MODE_PRIVATE);

        w = findViewById(R.id.webView);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
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

        w.clearCache(false);
        w.loadUrl("https://virionbookstore.github.io/StockOpname/");
    }

    private void installApkExportOverride() {
        String script =
            "(function() {" +
            "if(window.__virionApkExportInstalled)return;" +
            "window.__virionApkExportInstalled=true;" +
            "var shareBtns=document.querySelectorAll('button[onclick=\\\"exportRiwayatToExcel()\\\"]');" +
            "for(var i=0;i<shareBtns.length;i++){shareBtns[i].innerHTML='📤';shareBtns[i].title='Bagikan / Simpan Excel';shareBtns[i].setAttribute('aria-label','Bagikan / Simpan Excel');}" +
            "var refreshBtns=document.querySelectorAll('button[onclick=\\\"loadOpnameHistory()\\\"]');" +
            "for(var i=0;i<refreshBtns.length;i++){refreshBtns[i].innerHTML='🔄';refreshBtns[i].title='Refresh riwayat';refreshBtns[i].setAttribute('aria-label','Refresh riwayat');}" +

            "window.__virionFilterState={users:[],sessions:[]};" +
            "window.__virionEnsureFilters=function(){" +
              "var role=String(window.currentUserRole||'').toLowerCase();if(role!=='owner'){var ub=document.getElementById('virionFilterButton');if(ub)ub.remove();var um=document.getElementById('virionFilterModal');if(um)um.remove();return;}" +
              "if(document.getElementById('historyFilterBtn'))return;" +
              "var old=document.getElementById('adminHistoryFilter');if(old)old.classList.add('hidden');" +
              "var share=document.querySelector('button[onclick=\\\"exportRiwayatToExcel()\\\"]');if(!share)return;" +
              "var wrap=share.parentElement;" +
              "var btn=document.createElement('button');btn.id='virionFilterButton';btn.type='button';btn.innerHTML='⚙️';btn.title='Filter User & Sesi';btn.className='text-[10px] bg-slate-100 hover:bg-slate-200 px-3 py-1 rounded-md font-medium text-slate-600';btn.onclick=function(){window.__virionOpenFilter();};wrap.insertBefore(btn,share);" +
              "var modal=document.createElement('div');modal.id='virionFilterModal';modal.className='fixed inset-0 bg-slate-900/50 backdrop-blur-sm hidden items-center justify-center p-3 z-[99999]';" +
              "modal.innerHTML='<div class=\\\"bg-white rounded-2xl shadow-2xl max-w-sm w-full p-4 space-y-3\\\"><div class=\\\"flex justify-between items-center border-b pb-2\\\"><h3 class=\\\"font-bold text-sm text-slate-800\\\">Filter Riwayat</h3><button type=\\\"button\\\" id=\\\"virionFilterClose\\\" class=\\\"font-bold text-lg text-slate-500\\\">&times;</button></div><div><div class=\\\"text-xs font-bold text-slate-700 mb-1\\\">👤 User</div><div id=\\\"virionUserChecks\\\" class=\\\"max-h-36 overflow-y-auto border rounded-xl p-2 space-y-1\\\"></div></div><div><div class=\\\"text-xs font-bold text-slate-700 mb-1\\\">🏷️ Sesi</div><div id=\\\"virionSessionChecks\\\" class=\\\"max-h-36 overflow-y-auto border rounded-xl p-2 space-y-1\\\"></div></div><div class=\\\"flex gap-2 pt-1\\\"><button type=\\\"button\\\" id=\\\"virionFilterReset\\\" class=\\\"flex-1 py-2 bg-slate-100 rounded-xl text-xs font-semibold\\\">Reset</button><button type=\\\"button\\\" id=\\\"virionFilterApply\\\" class=\\\"flex-1 py-2 bg-blue-600 text-white rounded-xl text-xs font-semibold\\\">Terapkan</button></div></div>';" +
              "document.body.appendChild(modal);" +
              "document.getElementById('virionFilterClose').onclick=function(){modal.classList.add('hidden');modal.classList.remove('flex');};" +
              "document.getElementById('virionFilterReset').onclick=function(){window.__virionFilterState={users:[],sessions:[]};window.__virionOpenFilter();};" +
              "document.getElementById('virionFilterApply').onclick=function(){window.__virionReadFilter();modal.classList.add('hidden');modal.classList.remove('flex');if(window.renderHistoryList)window.renderHistoryList();};" +
            "};" +

            "window.__virionBuildChecks=function(){" +
              "var users=[...new Set(globalHistory.map(function(h){return h.petugas;}).filter(Boolean))].sort();" +
              "var sessions=[...new Set(globalHistory.map(function(h){return h.judulSesi;}).filter(Boolean))].sort();" +
              "var us=document.getElementById('virionUserChecks'),ss=document.getElementById('virionSessionChecks');if(!us||!ss)return;" +
              "function make(list,name,selected){return list.map(function(v,i){var checked=selected.indexOf(v)>=0?' checked':'';return '<label class=\\\"flex items-center gap-2 p-1.5 rounded-lg hover:bg-slate-50 text-xs\\\"><input type=\\\"checkbox\\\" data-filter=\\\"'+name+'\\\" value=\\\"'+String(v).replace(/\\\"/g,'&quot;')+'\\\"'+checked+' class=\\\"h-4 w-4\\\">'+String(v)+'</label>';}).join('')||'<div class=\\\"text-[11px] text-slate-400 p-1\\\">Tidak ada data</div>';}" +
              "us.innerHTML=make(users,'user',window.__virionFilterState.users);ss.innerHTML=make(sessions,'session',window.__virionFilterState.sessions);" +
            "};" +

            "window.__virionOpenFilter=function(){window.__virionEnsureFilters();window.__virionBuildChecks();var m=document.getElementById('virionFilterModal');if(m){m.classList.remove('hidden');m.classList.add('flex');}};" +
            "window.__virionReadFilter=function(){var us=[...document.querySelectorAll('#virionUserChecks input:checked')].map(function(x){return x.value;});var ss=[...document.querySelectorAll('#virionSessionChecks input:checked')].map(function(x){return x.value;});window.__virionFilterState={users:us,sessions:ss};};" +

            "window.getFilteredHistoryData=function(){var data=globalHistory;var role=currentUserRole.toLowerCase();var st=window.__virionFilterState||{users:[],sessions:[]};if(role==='user'||role==='staff'){data=data.filter(function(h){return h.petugas===currentUserNama;});}else if(st.users.length){data=data.filter(function(h){return st.users.indexOf(h.petugas)>=0;});}if(st.sessions.length){data=data.filter(function(h){return st.sessions.indexOf(h.judulSesi)>=0;});}return data;};" +

            "window.exportRiwayatToExcel=function(){var dataToExport=getFilteredHistoryData();if(!dataToExport||dataToExport.length===0){alert('Tidak ada data riwayat sesuai filter!');return;}var rows=dataToExport.map(function(h,index){return {'No':index+1,'Waktu':h.waktu,'Petugas':h.petugas||'-','Sesi SO':h.judulSesi||'1','SKU / Barcode':h.sku,'Nama Barang':h.namaBarang,'Stok Sistem':h.stokSistem,'Stok Fisik':h.stokFisik,'Selisih':h.selisih,'Harga (Rp)':h.harga||0,'Supplier':h.supplier||'-','Catatan':h.keterangan||'-'};});var ws=XLSX.utils.json_to_sheet(rows);var wb=XLSX.utils.book_new();XLSX.utils.book_append_sheet(wb,ws,'Laporan Stock Opname');var roleStr=currentUserRole.toLowerCase();var fileLabel=(roleStr==='user'||roleStr==='staff')?currentUserNama:'Filter';var dateStr=new Date().toISOString().slice(0,10);var fileName='Laporan_SO_'+fileLabel+'_'+dateStr+'.xlsx';var base64=XLSX.write(wb,{bookType:'xlsx',type:'base64'});if(window.AndroidInterface&&window.AndroidInterface.exportExcel){window.AndroidInterface.exportExcel(fileName,base64);}else{XLSX.writeFile(wb,fileName);}};" +

            "window.__virionEnsureFilters();setTimeout(window.__virionEnsureFilters,500);setTimeout(window.__virionEnsureFilters,1500);" +
            "var oldLoad=window.loadOpnameHistory;window.loadOpnameHistory=function(){var r=oldLoad.apply(this,arguments);setTimeout(function(){window.__virionEnsureFilters();},400);return r;};" +
            "})();";
        w.evaluateJavascript(script, null);
    }

    private class AndroidExportBridge {
        @JavascriptInterface
        public void saveSession(String nama, String role) {
            sessionPrefs.edit().putString("nama", nama == null ? "" : nama).putString("role", role == null ? "" : role).apply();
        }

        @JavascriptInterface
        public String getSession() {
            String nama = sessionPrefs.getString("nama", "");
            String role = sessionPrefs.getString("role", "");
            if (nama.isEmpty() || role.isEmpty()) return "";
            return android.util.Base64.encodeToString((nama + "\n" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        }

        @JavascriptInterface
        public void clearSession() {
            sessionPrefs.edit().clear().apply();
        }

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
