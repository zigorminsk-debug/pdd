package by.pdd.study.update;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import by.pdd.study.BuildConfig;

/** Read-only, grant-based provider used only to hand a verified APK to the system installer. */
public final class UpdateApkProvider extends ContentProvider {
    private static final String APK_MIME = "application/vnd.android.package-archive";

    public static Uri uriForApk(Context context, File apk) {
        File directory = updatesDirectory(context);
        if (!apk.getName().matches("[a-zA-Z0-9._-]+")
                || !directory.getAbsolutePath().equals(apk.getParentFile().getAbsolutePath())) {
            throw new IllegalArgumentException("APK is outside the private update directory");
        }
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.APPLICATION_ID + ".update")
                .appendPath("apk")
                .appendPath(apk.getName())
                .build();
    }

    static File updatesDirectory(Context context) {
        File directory = new File(context.getFilesDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create update directory");
        }
        return directory;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri.getPathSegments().size() != 2 || !"apk".equals(uri.getPathSegments().get(0))) {
            throw new FileNotFoundException("Invalid update URI");
        }
        String name = uri.getPathSegments().get(1);
        if (!name.matches("[a-zA-Z0-9._-]+")) {
            throw new FileNotFoundException("Invalid APK name");
        }
        try {
            File directory = updatesDirectory(requireContext()).getCanonicalFile();
            File file = new File(directory, name).getCanonicalFile();
            if (!directory.equals(file.getParentFile()) || !file.isFile()) {
                throw new FileNotFoundException("APK not found");
            }
            return file;
        } catch (IOException exception) {
            throw new FileNotFoundException(exception.getMessage());
        }
    }

    private Context requireContext() {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider is not attached");
        return context;
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public String getType(Uri uri) {
        return APK_MIME;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        try {
            File file = resolve(uri);
            for (String column : columns) {
                if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
                else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
                else row.add(null);
            }
        } catch (FileNotFoundException exception) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
