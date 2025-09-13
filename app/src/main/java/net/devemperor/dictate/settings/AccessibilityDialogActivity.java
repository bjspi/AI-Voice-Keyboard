package net.devemperor.dictate.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.R;

public class AccessibilityDialogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dictate_accessibility_service_disabled_title)
                .setMessage(R.string.dictate_accessibility_service_disabled_message)
                .setPositiveButton(R.string.dictate_open_settings, (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.dictate_cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
