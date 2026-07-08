package com.cannon.onyxlauncher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class FatalErrorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bundle extras = getIntent().getExtras();
        if(extras == null) {
            finish();
            return;
        }
        
        Object throwableObj = extras.get("throwable");
        String stackTrace = (throwableObj instanceof Throwable) ? Tools.printToString((Throwable) throwableObj) : String.valueOf(throwableObj);
        
        // Zmieniony dialog na Onyx Launcher
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.fatal_error_dialog_title))
            .setMessage(getString(R.string.fatal_error_dialog_message))
            .setPositiveButton(getString(R.string.fatal_error_dialog_view_logs), (p1, p2) -> {
                Intent intent = new Intent(this, OnyxMainActivity.class);
                intent.putExtra("open_screen", "CrashLogs"); // Przekierowanie do Twojej konsoli
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            })
            .setNegativeButton(getString(R.string.fatal_error_dialog_copy_error), (p1, p2) -> {
                ClipboardManager mgr = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                mgr.setPrimaryClip(ClipData.newPlainText("error", stackTrace));
                finish();
            })
            .setCancelable(false)
            .show();
    }

    public static void showError(Context ctx, String savePath, boolean storageAllow, Throwable th) {
        Intent intent = new Intent(ctx, FatalErrorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("throwable", th);
        ctx.startActivity(intent);
    }
}
