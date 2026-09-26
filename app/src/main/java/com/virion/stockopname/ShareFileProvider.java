package com.virion.stockopname;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ShareFileProvider extends ContentProvider {
    private File root;

    @Override public boolean onCreate() {
        root = new File(getContext().getCacheDir(), "shared");
        return true;
    }

    private File resolve(Uri uri) throws IOException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\") || name.contains(".."))
            throw new IOException("File tidak valid");
        File file = new File(root, name).getCanonicalFile();
        File base = root.getCanonicalFile();
        if (!file.getPath().startsWith(base.getPath() + File.separator))
            throw new IOException("Akses ditolak");
        if (!file.isFile()) throw new FileNotFoundException(name);
        return file;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try { return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(cols);
            Object[] row = new Object[cols.length];
            for (int i=0;i<cols.length;i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i]=file.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i]=file.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (Exception e) { return null; }
    }

    @Override public String getType(Uri uri) { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
