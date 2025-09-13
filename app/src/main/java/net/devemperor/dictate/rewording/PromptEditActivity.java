package net.devemperor.dictate.rewording;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import net.devemperor.dictate.R;
import net.devemperor.dictate.SimpleTextWatcher;
import net.devemperor.dictate.core.DictateAccessibilityService;

public class PromptEditActivity extends AppCompatActivity {

    private PromptsDatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_prompt_edit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_prompt_edit), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_edit_prompt);
        }

        EditText promptNameEt = findViewById(R.id.prompt_edit_name_et);
        EditText promptPromptEt = findViewById(R.id.prompt_edit_prompt_et);
        MaterialSwitch promptRequiresSelectionSwitch = findViewById(R.id.prompt_edit_requires_selection_switch);
        MaterialSwitch promptAlwaysUseSwitch = findViewById(R.id.prompt_edit_always_use_switch);
        MaterialSwitch promptSendScreenshotSwitch = findViewById(R.id.prompt_edit_send_screenshot_switch);
        MaterialButton savePromptBtn = findViewById(R.id.prompt_edit_save_btn);

        db = new PromptsDatabaseHelper(this);

        int id = getIntent().getIntExtra("net.devemperor.dictate.prompt_edit_activity_id", -1);
        boolean hasAlwaysUsePrompt = db.hasAlwaysUsePrompt();
        PromptModel alwaysUseModel = db.getAlwaysUsePrompt();
        
        // If the alwaysUse prompt is the same as the one being edited, we don't count it as "having" an alwaysUse prompt
        if (alwaysUseModel != null && alwaysUseModel.getId() == id) {
            hasAlwaysUsePrompt = false;
            alwaysUseModel = null;
        }

        if (id != -1) {
            PromptModel model = db.get(id);
            promptNameEt.setText(model.getName());
            promptPromptEt.setText(model.getPrompt());
            promptRequiresSelectionSwitch.setChecked(model.requiresSelection());
            promptAlwaysUseSwitch.setChecked(model.isAlwaysUse());
            
            // Disable the alwaysUse switch if another prompt already has it and this isn't that prompt
            if (hasAlwaysUsePrompt && !model.isAlwaysUse()) {
                promptAlwaysUseSwitch.setEnabled(false);
            }

            promptSendScreenshotSwitch.setChecked(model.isSendScreenshot());
            savePromptBtn.setEnabled(true);
        } else {
            // For new prompts, disable alwaysUse if another prompt already has it
            if (hasAlwaysUsePrompt) {
                promptAlwaysUseSwitch.setEnabled(false);
            }
        }

        // When "SEND Screenshot" is checked, check Accessibility Service
        promptSendScreenshotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isAccessibilityServiceEnabled()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dictate_accessibility_service_required)
                        .setMessage(R.string.dictate_accessibility_service_required_desc)
                        .setPositiveButton(R.string.dictate_go_to_settings, (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.dictate_cancel, (dialog, which) -> {
                            promptSendScreenshotSwitch.setChecked(false);
                        })
                        .setOnCancelListener(dialog -> {
                            promptSendScreenshotSwitch.setChecked(false);
                        })
                        .show();
           }
        });

        // When "Always use" is checked, also check "Requires text selection"
        promptAlwaysUseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                promptRequiresSelectionSwitch.setChecked(true);
            }
        });

        // When "Requires text selection" is unchecked, uncheck "Always use"
        promptRequiresSelectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                promptAlwaysUseSwitch.setChecked(false);
            }
        });

        SimpleTextWatcher tw = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                savePromptBtn.setEnabled(!(promptNameEt.getText().toString().isEmpty()) && !(promptPromptEt.getText().toString().isEmpty()));
            }
        };
        promptNameEt.addTextChangedListener(tw);
        promptPromptEt.addTextChangedListener(tw);

        savePromptBtn.setOnClickListener(v -> {
            String name = promptNameEt.getText().toString();
            String prompt = promptPromptEt.getText().toString();
            boolean requiresSelection = promptRequiresSelectionSwitch.isChecked();
            boolean alwaysUse = promptAlwaysUseSwitch.isChecked();
            boolean sendScreenshot = promptSendScreenshotSwitch.isChecked();

            // If "Always use" is checked, ensure only this prompt has it set
            if (alwaysUse) {
                // Set alwaysUse to false for all other prompts
                for (PromptModel model : db.getAll()) {
                    if (model.getId() != id) {
                        model.setAlwaysUse(false);
                        db.update(model);
                    }
                }
            }

            Intent result = new Intent();
            if (id == -1) {
                int addId = db.add(new PromptModel(0, db.count(), name, prompt, requiresSelection, alwaysUse, sendScreenshot));
                result.putExtra("added_id", addId);
            } else {
                PromptModel model = db.get(id);
                model.setName(name);
                model.setPrompt(prompt);
                model.setRequiresSelection(requiresSelection);
                model.setAlwaysUse(alwaysUse);
                model.setSendScreenshot(sendScreenshot);
                db.update(model);
                result.putExtra("updated_id", id);
            }

            setResult(RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isAccessibilityServiceEnabled() {
        ContentResolver contentResolver = getContentResolver();
        String enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServices);
        while (colonSplitter.hasNext()) {
            String componentName = colonSplitter.next();
            if (componentName.equalsIgnoreCase(getPackageName() + "/" + DictateAccessibilityService.class.getName())) {
                return true;
            }
        }
        return false;
    }
}