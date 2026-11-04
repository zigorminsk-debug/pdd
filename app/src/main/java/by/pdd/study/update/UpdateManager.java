package by.pdd.study.update;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import by.pdd.study.BuildConfig;

/** Secure, browser-free APK updates distributed as GitHub Release assets. */
public final class UpdateManager {
    private static final String LATEST_METADATA =
            "https://github.com/zigorminsk-debug/pdd/releases/latest/download/update.json";
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private static final int MAX_APK_BYTES = 150 * 1024 * 1024;

    public interface CheckListener {
        void onUpdate(UpdateInfo update);
        void onNoUpdate();
        void onError(String message);
    }

    public interface DownloadListener {
        void onProgress(int percent);
        void onVerified(File apk);
        void onError(String message);
    }

    public static final class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String notes;
        public String apkUrl;
        public String sha256;
    }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void check(CheckListener listener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = connect(LATEST_METADATA);
                String body = readText(connection.getInputStream(), MAX_METADATA_BYTES);
                UpdateInfo update = parse(body);
                if (update.versionCode <= BuildConfig.VERSION_CODE) {
                    main.post(listener::onNoUpdate);
                } else {
                    main.post(() -> listener.onUpdate(update));
                }
            } catch (IOException | JSONException | IllegalArgumentException exception) {
                main.post(() -> listener.onError(friendlyError(exception)));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void download(UpdateInfo update, DownloadListener listener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            File partial = new File(UpdateApkProvider.updatesDirectory(activity),
                    "update-" + update.versionCode + ".apk.part");
            File target = new File(partial.getParentFile(), "update-" + update.versionCode + ".apk");
            try {
                if (update.versionCode <= BuildConfig.VERSION_CODE) {
                    throw new IOException("Загруженная версия не новее установленной");
                }
                connection = connect(update.apkUrl);
                long expectedLength = connection.getContentLengthLong();
                if (expectedLength > MAX_APK_BYTES) throw new IOException("APK превышает 150 МБ");
                MessageDigest digest = sha256();
                long total = 0;
                int previousPercent = -1;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_APK_BYTES) throw new IOException("APK превышает 150 МБ");
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        if (expectedLength > 0) {
                            int percent = (int) Math.min(99, total * 100 / expectedLength);
                            if (percent != previousPercent) {
                                previousPercent = percent;
                                int reported = percent;
                                main.post(() -> listener.onProgress(reported));
                            }
                        }
                    }
                }
                if (total < 1024) throw new IOException("Сервер вернул пустой APK");
                String actualHash = hex(digest.digest());
                if (!update.sha256.equalsIgnoreCase(actualHash)) {
                    throw new IOException("Контрольная сумма APK не совпадает");
                }
                if (target.exists() && !target.delete()) throw new IOException("Не удалось заменить старый APK");
                if (!partial.renameTo(target)) throw new IOException("Не удалось сохранить APK");
                verifyApk(target, update.versionCode);
                main.post(() -> listener.onProgress(100));
                main.post(() -> listener.onVerified(target));
            } catch (IOException | PackageManager.NameNotFoundException | NoSuchAlgorithmException exception) {
                if (partial.exists()) partial.delete();
                if (target.exists()) target.delete();
                main.post(() -> listener.onError(friendlyError(exception)));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public boolean canInstall() {
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    public void openInstallSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + BuildConfig.APPLICATION_ID));
        activity.startActivity(intent);
    }

    public void install(File apk) {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(BuildConfig.APPLICATION_ID);
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);
            try (FileInputStream input = new FileInputStream(apk);
                 OutputStream output = session.openOutput("verified-update.apk")) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                session.fsync(output);
            }

            Intent callback = new Intent(activity, UpdateInstallReceiver.class);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingFlags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    activity, sessionId, callback, pendingFlags);
            session.commit(pendingIntent.getIntentSender());
        } catch (Exception exception) {
            if (session != null) {
                try {
                    session.abandon();
                } catch (RuntimeException ignored) {
                    // The system may already have closed a failed session.
                }
            }
            Toast.makeText(activity,
                    "Не удалось запустить системный установщик: " + exception.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            if (session != null) session.close();
        }
    }

    private void verifyApk(File apk, int expectedVersionCode)
            throws PackageManager.NameNotFoundException, IOException, NoSuchAlgorithmException {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new IOException("Android не смог прочитать APK");
        if (!BuildConfig.APPLICATION_ID.equals(archive.packageName)) {
            throw new IOException("APK относится к другому приложению");
        }
        long archiveVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveVersion != expectedVersionCode || archiveVersion <= BuildConfig.VERSION_CODE) {
            throw new IOException("Номер версии APK не совпадает с описанием");
        }
        PackageInfo installed = manager.getPackageInfo(BuildConfig.APPLICATION_ID, flags);
        Set<String> archiveSigners = signerHashes(archive);
        Set<String> installedSigners = signerHashes(installed);
        if (archiveSigners.isEmpty() || !archiveSigners.equals(installedSigners)) {
            throw new IOException("Подпись APK не совпадает с подписью установленного приложения");
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signerHashes(PackageInfo info) throws NoSuchAlgorithmException {
        Set<String> result = new HashSet<>();
        android.content.pm.Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return result;
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures != null) {
            for (android.content.pm.Signature signature : signatures) {
                result.add(hex(sha256().digest(signature.toByteArray())));
            }
        }
        return result;
    }

    private static UpdateInfo parse(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        if (!BuildConfig.APPLICATION_ID.equals(object.getString("packageName"))) {
            throw new IllegalArgumentException("Пакет обновления не совпадает");
        }
        UpdateInfo update = new UpdateInfo();
        update.versionCode = object.getInt("versionCode");
        update.versionName = object.getString("versionName");
        update.apkUrl = object.getString("apkUrl");
        update.sha256 = object.getString("sha256").toLowerCase(Locale.ROOT);
        update.notes = object.optString("notes", "Исправления и улучшения.");
        if (!update.apkUrl.startsWith("https://")) throw new IllegalArgumentException("Небезопасный URL APK");
        if (!update.sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Неверный SHA-256");
        return update;
    }

    private static HttpURLConnection connect(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream, application/json");
        connection.setRequestProperty("User-Agent", "PDD-Belarus-Android/" + BuildConfig.VERSION_NAME);
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) throw new IOException("Сервер обновлений: HTTP " + status);
        return connection;
    }

    private static String readText(InputStream input, int limit) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                result.append(buffer, 0, count);
                if (result.length() > limit) throw new IOException("Описание обновления слишком большое");
            }
        }
        return result.toString();
    }

    private static MessageDigest sha256() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static String friendlyError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Не удалось проверить обновление. Проверьте подключение к интернету." : message;
    }

    public void close() {
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
