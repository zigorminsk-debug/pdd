package by.pdd.study.update;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import java.io.File;

/** Receives explicit PackageInstaller results, including MIUI's confirmation IntentSender. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation == null) {
                showError(context, "Система не вернула экран подтверждения установки");
                return;
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirmation);
            } catch (ActivityNotFoundException | SecurityException exception) {
                showError(context, "Системный установщик недоступен: " + exception.getClass().getSimpleName());
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            cleanup(context);
            Toast.makeText(context, "Обновление установлено", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        cleanup(context);
        showError(context, message == null ? "Установка отменена или отклонена системой (код " + status + ")" : message);
    }

    private static void cleanup(Context context) {
        File[] files = new File(context.getFilesDir(), "updates").listFiles();
        if (files != null) {
            for (File file : files) file.delete();
        }
    }

    private static void showError(Context context, String message) {
        Toast.makeText(context, "Не удалось установить обновление:\n" + message, Toast.LENGTH_LONG).show();
    }
}
