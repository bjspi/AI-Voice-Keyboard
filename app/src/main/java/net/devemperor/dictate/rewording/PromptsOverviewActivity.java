package net.devemperor.dictate.rewording;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.devemperor.dictate.R;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class PromptsOverviewActivity extends AppCompatActivity {

    PromptsDatabaseHelper db;
    List<PromptModel> data;
    RecyclerView recyclerView;
    PromptsOverviewAdapter adapter;

    ActivityResultLauncher<Intent> addEditPromptLauncher;
    ActivityResultLauncher<Intent> importFileLauncher;
    ActivityResultLauncher<Intent> exportFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_prompts_overview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_prompts_overview), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_prompts);
        }

        db = new PromptsDatabaseHelper(this);
        data = db.getAll();

        recyclerView = findViewById(R.id.prompts_overview_rv);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PromptsOverviewAdapter(this, data, db, position -> {
            PromptModel model = data.get(position);

            Intent intent = new Intent(this, PromptEditActivity.class);
            intent.putExtra("net.devemperor.dictate.prompt_edit_activity_id", model.getId());
            addEditPromptLauncher.launch(intent);
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);

        FloatingActionButton addPromptFab = findViewById(R.id.prompts_overview_add_fab);
        addPromptFab.setOnClickListener(v -> {
            Intent intent = new Intent(PromptsOverviewActivity.this, PromptEditActivity.class);
            addEditPromptLauncher.launch(intent);
        });

        // Import/Export buttons
        Button importBtn = findViewById(R.id.prompts_overview_import_btn);
        Button exportBtn = findViewById(R.id.prompts_overview_export_btn);

        importBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importFileLauncher.launch(intent);
        });

        exportBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "dictate_prompts.json");
            exportFileLauncher.launch(intent);
        });

        addEditPromptLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        int updatedId = -1;
                        int addedId = -1;
                        if (result.getData() != null) {
                            updatedId = result.getData().getIntExtra("updated_id", -1);
                            addedId = result.getData().getIntExtra("added_id", -1);
                        }
                        if (updatedId != -1) {
                            PromptModel updatedPrompt = db.get(updatedId);
                            for (int i = 0; i < data.size(); i++) {
                                if (data.get(i).getId() == updatedId) {
                                    data.set(i, updatedPrompt);
                                    adapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                        } else if (addedId != -1) {
                            data.add(db.get(addedId));
                            adapter.notifyItemInserted(data.size() - 1);
                            findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                }
        );

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getData() != null) {
                            try {
                                FileInputStream fis = (FileInputStream) getContentResolver().openInputStream(data.getData());
                                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                                StringBuilder jsonContent = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    jsonContent.append(line);
                                }
                                reader.close();
                                fis.close();

                                // Parse JSON and import prompts
                                Gson gson = new Gson();
                                Type listType = new TypeToken<List<PromptModel>>(){}.getType();
                                List<PromptModel> importedPrompts = gson.fromJson(jsonContent.toString(), listType);

                                // Clear existing prompts
                                db.clearAllPrompts();

                                // Add imported prompts
                                for (int i = 0; i < importedPrompts.size(); i++) {
                                    PromptModel prompt = importedPrompts.get(i);
                                    prompt.setPos(i);
                                    db.add(prompt);
                                }

                                // Refresh data
                                this.data.clear();
                                this.data.addAll(db.getAll());
                                adapter.notifyDataSetChanged();
                                findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(this.data.isEmpty() ? View.VISIBLE : View.GONE);

                                Toast.makeText(this, "Prompts imported successfully", Toast.LENGTH_SHORT).show();
                            } catch (IOException e) {
                                Toast.makeText(this, "Error importing prompts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            }
                        }
                    }
                }
        );

        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getData() != null) {
                            try {
                                // Get all prompts
                                List<PromptModel> prompts = db.getAll();

                                // Convert to JSON
                                Gson gson = new Gson();
                                String json = gson.toJson(prompts);

                                // Write to file
                                FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(data.getData());
                                fos.write(json.getBytes());
                                fos.close();

                                Toast.makeText(this, "Prompts exported successfully", Toast.LENGTH_SHORT).show();
                            } catch (IOException e) {
                                Toast.makeText(this, "Error exporting prompts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            }
                        }
                    }
                }
        );
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
}