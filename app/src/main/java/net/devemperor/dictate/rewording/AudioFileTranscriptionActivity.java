package net.devemperor.dictate.rewording;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.DictateUtils;
import net.devemperor.dictate.R;
import net.devemperor.dictate.usage.UsageDatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioFileTranscriptionActivity extends AppCompatActivity {

    private static final String TAG = "AudioFileTranscription";

    private TextView transcriptionResultTv;
    private ProgressBar transcriptionPb;
    private Button copyBtn;
    private Button closeBtn;
    private SharedPreferences sp;
    private UsageDatabaseHelper usageDb;
    private Vibrator vibrator;
    private ExecutorService transcriptionThread;
    private String transcriptionResult = "";
    private boolean vibrationEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_audio_file_transcription);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_audio_file_transcription), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        transcriptionResultTv = findViewById(R.id.audio_file_transcription_result_tv);
        transcriptionPb = findViewById(R.id.audio_file_transcription_pb);
        copyBtn = findViewById(R.id.audio_file_transcription_copy_btn);
        closeBtn = findViewById(R.id.audio_file_transcription_close_btn);

        // Initialize other components
        sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);
        usageDb = new UsageDatabaseHelper(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrationEnabled = sp.getBoolean("net.devemperor.dictate.vibration", true);

        // Set up button listeners
        copyBtn.setOnClickListener(v -> copyTranscriptionToClipboard());
        closeBtn.setOnClickListener(v -> finish());

        // Check for file in intent
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            if (intent.getType().startsWith("audio/")) {
                Uri audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (audioUri != null) {
                    handleAudioFile(audioUri);
                } else {
                    Toast.makeText(this, R.string.dictate_no_audio_file, Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Toast.makeText(this, R.string.dictate_invalid_file_type, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, R.string.dictate_no_audio_file, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void handleAudioFile(Uri audioUri) {
        // Show progress UI
        transcriptionPb.setVisibility(View.VISIBLE);
        transcriptionResultTv.setText(R.string.dictate_transcribing);
        copyBtn.setVisibility(View.GONE);
        closeBtn.setVisibility(View.GONE);

        // Start transcription in background thread
        transcriptionThread = Executors.newSingleThreadExecutor();
        transcriptionThread.execute(() -> transcribeAudioFile(audioUri));
    }

    private void transcribeAudioFile(Uri audioUri) {
        try {
            // Copy the file to internal storage since the API needs a file path
            File tempFile = copyUriToFile(audioUri);
            if (tempFile == null) {
                runOnUiThread(() -> {
                    transcriptionResultTv.setText(R.string.dictate_file_copy_error);
                    transcriptionPb.setVisibility(View.GONE);
                    closeBtn.setVisibility(View.VISIBLE);
                });
                return;
            }

            // Perform transcription
            String resultText = performTranscription(tempFile);
            
            // Clean up temp file
            tempFile.delete();
            
            // Update UI with results
            transcriptionResult = resultText;
            runOnUiThread(() -> {
                transcriptionPb.setVisibility(View.GONE);
                transcriptionResultTv.setText(resultText);
                copyBtn.setVisibility(View.VISIBLE);
                closeBtn.setVisibility(View.VISIBLE);
                
                // Vibrate on completion
                if (vibrationEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error transcribing audio file", e);
            runOnUiThread(() -> {
                transcriptionResultTv.setText(getString(R.string.dictate_transcription_error, e.getMessage()));
                transcriptionPb.setVisibility(View.GONE);
                closeBtn.setVisibility(View.VISIBLE);
                
                // Vibrate on error
                if (vibrationEnabled) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            });
        }
    }

    private File copyUriToFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            // Determine the file extension
            String fileExtension = getFileExtension(uri);
            Log.d(TAG, "File extension from URI: " + fileExtension);
            
            // If we can't determine the extension from the URI, try to guess it from the MIME type
            if (fileExtension == null || fileExtension.isEmpty()) {
                String mimeType = getContentResolver().getType(uri);
                fileExtension = getExtensionFromMimeType(mimeType);
                Log.d(TAG, "File extension from MIME type (" + mimeType + "): " + fileExtension);
            }
            
            // If we still don't have an extension, default to .ogg (common for voice messages)
            if (fileExtension == null || fileExtension.isEmpty()) {
                fileExtension = "ogg";
            }
            
            // Ensure the extension starts with a dot
            if (!fileExtension.startsWith(".")) {
                fileExtension = "." + fileExtension;
            }
            
            // Validate that the extension is supported by the transcription service
            String extensionWithoutDot = fileExtension.startsWith(".") ? fileExtension.substring(1) : fileExtension;
            if (!isValidAudioExtension(extensionWithoutDot)) {
                // If not supported, try common voice message formats
                if (extensionWithoutDot.equals("tmp") || extensionWithoutDot.equals("temp")) {
                    fileExtension = ".ogg"; // Common for WhatsApp and other messaging apps
                } else {
                    fileExtension = ".mp3"; // Most universally supported format
                }
            }
            
            Log.d(TAG, "Final file extension: " + fileExtension);
            
            // Create a temporary file with the proper extension
            String fileName = "temp_audio_" + System.currentTimeMillis() + fileExtension;
            File tempFile = new File(getCacheDir(), fileName);
            
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();
            
            return tempFile;
        } catch (IOException e) {
            Log.e(TAG, "Error copying URI to file", e);
            return null;
        }
    }
    
    private String getFileExtension(Uri uri) {
        String fileName = uri.getLastPathSegment();
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        } else if (fileName != null && !fileName.isEmpty()) {
            // If there's a file name but no extension, return empty string
            // This will trigger fallback to MIME type detection
            return "";
        }
        return null;
    }
    
    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        
        // Normalize the MIME type (handle cases like "audio/mp3; charset=utf-8")
        if (mimeType.contains(";")) {
            mimeType = mimeType.substring(0, mimeType.indexOf(";")).trim();
        }
        
        switch (mimeType.toLowerCase()) {
            case "audio/flac":
                return "flac";
            case "audio/mpeg":
            case "audio/mp3":
                return "mp3";
            case "audio/mp4":
            case "audio/x-m4a":
                return "mp4";  // or "m4a" - using mp4 as it's more generic
            case "audio/ogg":
            case "application/ogg":
                return "ogg";
            case "audio/opus":
                return "opus";
            case "audio/wav":
            case "audio/x-wav":
                return "wav";
            case "audio/webm":
                return "webm";
            default:
                // Handle vendor-specific MIME types
                if (mimeType.startsWith("audio/")) {
                    // For unknown audio types, try to extract extension from the MIME type
                    if (mimeType.length() > 6) { // "audio/".length() = 6
                        String subtype = mimeType.substring(6);
                        // Simple validation - only alphanumeric and hyphen/underscore
                        if (subtype.matches("[a-zA-Z0-9_-]+")) {
                            return subtype;
                        }
                    }
                }
                return null;
        }
    }
    
    private boolean isValidAudioExtension(String extension) {
        if (extension == null) return false;
        
        // Remove the dot if present
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        
        // List of supported extensions by the transcription service
        String[] supportedExtensions = {"flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "opus", "wav", "webm"};
        
        for (String supported : supportedExtensions) {
            if (supported.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        
        return false;
    }

    private String performTranscription(File audioFile) throws Exception {
        int transcriptionProvider = sp.getInt("net.devemperor.dictate.transcription_provider", 0);
        String apiHost = getResources().getStringArray(R.array.dictate_api_providers_values)[transcriptionProvider];
        if (apiHost.equals("custom_server")) {
            apiHost = sp.getString("net.devemperor.dictate.transcription_custom_host", getString(R.string.dictate_custom_server_host_hint));
        }

        String apiKey = sp.getString("net.devemperor.dictate.transcription_api_key", sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY")).replaceAll("[^ -~]", "");
        String proxyHost = sp.getString("net.devemperor.dictate.proxy_host", getString(R.string.dictate_settings_proxy_hint));

        String transcriptionModel = "";
        switch (transcriptionProvider) {  // for upgrading: use old transcription_model preference
            case 0: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_openai_model", sp.getString("net.devemperor.dictate.transcription_model", "gpt-4o-mini-transcribe")); break;
            case 1: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_groq_model", "whisper-large-v3-turbo"); break;
            case 2: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_custom_model", getString(R.string.dictate_custom_transcription_model_hint));
        }

        OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiHost)
                .timeout(Duration.ofSeconds(120));

        String stylePrompt;
        switch (sp.getInt("net.devemperor.dictate.style_prompt_selection", 1)) {
            case 1:
                stylePrompt = DictateUtils.PROMPT_PUNCTUATION_CAPITALIZATION;
                break;
            case 2:
                stylePrompt = sp.getString("net.devemperor.dictate.style_prompt_custom_text", "");
                break;
            default:
                stylePrompt = "";
        }

        TranscriptionCreateParams.Builder transcriptionBuilder = TranscriptionCreateParams.builder()
                .file(audioFile.toPath())
                .model(transcriptionModel)
                .responseFormat(AudioResponseFormat.JSON);  // gpt-4o-transcribe only supports json

        if (!stylePrompt.isEmpty()) transcriptionBuilder.prompt(stylePrompt);
        if (sp.getBoolean("net.devemperor.dictate.proxy_enabled", false)) {
            if (DictateUtils.isValidProxy(proxyHost)) DictateUtils.applyProxy(clientBuilder, sp);
        }

        // Logging für die API-Anfrage (ohne API-Key)
        Log.d(TAG, "Transkriptionsanfrage - URL: " + apiHost + ", Modell: " + transcriptionModel);

        Transcription transcription = clientBuilder.build().audio().transcriptions().create(transcriptionBuilder.build()).asTranscription();
        String resultText = transcription.text().strip();  // Groq sometimes adds leading whitespace

        // Logging der Transkription (ohne API-Key)
        Log.d(TAG, "Transkription erhalten: " + resultText);

        usageDb.edit(transcriptionModel, DictateUtils.getAudioDuration(audioFile), 0, 0, transcriptionProvider);

        return resultText;
    }

    private void copyTranscriptionToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.dictate_transcription), transcriptionResult);
        clipboard.setPrimaryClip(clip);
        
        // Show toast confirmation
        Toast.makeText(this, R.string.dictate_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        
        // Vibrate on copy
        if (vibrationEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (transcriptionThread != null && !transcriptionThread.isShutdown()) {
            transcriptionThread.shutdownNow();
        }
        if (usageDb != null) usageDb.close();
    }
}