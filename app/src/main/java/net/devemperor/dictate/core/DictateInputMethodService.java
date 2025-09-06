package net.devemperor.dictate.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.provider.Settings;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
//import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.DictateUtils;
import net.devemperor.dictate.rewording.PromptModel;
import net.devemperor.dictate.rewording.PromptEditActivity;
import net.devemperor.dictate.rewording.PromptsDatabaseHelper;
import net.devemperor.dictate.rewording.PromptsKeyboardAdapter;
import net.devemperor.dictate.rewording.PromptsOverviewActivity;
import net.devemperor.dictate.settings.DictateSettingsActivity;
import net.devemperor.dictate.R;
import net.devemperor.dictate.usage.UsageDatabaseHelper;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MAIN CLASS
public class DictateInputMethodService extends InputMethodService {

    // define handlers and runnables for background tasks
    private Handler mainHandler;
    private Handler deleteHandler;
    private Handler recordTimeHandler;
    private Runnable deleteRunnable;
    private Runnable recordTimeRunnable;

    // define variables and objects
    private long elapsedTime;
    private boolean isDeleting = false;
    private long startDeleteTime = 0;
    private int currentDeleteDelay = 50;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean instantPrompt = false;
    private boolean vibrationEnabled = true;
    private boolean audioFocusEnabled = true;
    private MaterialButton selectedCharacter = null;
    private boolean spaceButtonUserHasSwiped = false;
    private int currentInputLanguagePos;
    private String currentInputLanguageValue;

    // Variable für das Wort-löschen Feature
    private int selectedWordCount = 0; // Anzahl der selektierten Wörter
    private int initialCursorPosition = 0; // Initiale Cursorposition beim Drücken
    private float startXPosition = 0; // Startposition des Swipes
    private static final int SWIPE_THRESHOLD_PER_WORD = 80; // Pixel pro Wort

    // Flag to switch IME after transcription
    private boolean shouldSwitchImeAfterTranscription = false;

    // Flag, ob IME frisch gebunden wurde
    private boolean imeJustBound = false;

    // Variable für den temporären alwaysUse Prompt
    private PromptModel temporaryAlwaysUsePrompt = null;

    // Flag, ob die Tastatur bereits sichtbar war
    private boolean keyboardWasVisible = false;

    private MediaRecorder recorder;
    private ExecutorService speechApiThread;
    private ExecutorService rewordingApiThread;
    private File audioFile;
    private Vibrator vibrator;
    private SharedPreferences sp;
    private AudioManager am;
    private AudioFocusRequest audioFocusRequest;
    //private MaterialButton selectedCharacter = null;

    // define views
    private ConstraintLayout dictateKeyboardView;
    private MaterialButton settingsButton;
    private MaterialButton recordButton;
    private MaterialButton stopButton;
    private MaterialButton stopSwitchButton;
    private MaterialButton resendButton;
    private MaterialButton backspaceButton;
    private MaterialButton switchButton;
    private MaterialButton trashButton;
    private MaterialButton spaceButton;
    private MaterialButton pauseButton;
    private MaterialButton enterButton;
    private ConstraintLayout infoCl;
    private TextView infoTv;
    private Button infoYesButton;
    private Button infoNoButton;
    private ConstraintLayout promptsCl;
    private RecyclerView promptsRv;
    private TextView runningPromptTv;
    private ProgressBar runningPromptPb;
    private MaterialButton editSelectAllButton;
    private MaterialButton editUndoButton;
    private MaterialButton editRedoButton;
    private MaterialButton editCutButton;
    private MaterialButton editCopyButton;
    private MaterialButton editPasteButton;
    private LinearLayout overlayCharactersLl;
    //private MaterialButton selectedCharacter = null;

    PromptsDatabaseHelper promptsDb;
    PromptsKeyboardAdapter promptsAdapter;

    UsageDatabaseHelper usageDb;

    private boolean isBluetoothScoStarted = false;

    // start method that is called when user opens the keyboard
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateInputView() {
        Context context = new ContextThemeWrapper(this, R.style.Theme_Dictate);

        // initialize some stuff
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        if (deleteHandler == null) deleteHandler = new Handler();
        if (recordTimeHandler == null) recordTimeHandler = new Handler(Looper.getMainLooper());

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);
        promptsDb = new PromptsDatabaseHelper(this);
        usageDb = new UsageDatabaseHelper(this);
        vibrationEnabled = sp.getBoolean("net.devemperor.dictate.vibration", true);
        currentInputLanguagePos = sp.getInt("net.devemperor.dictate.input_language_pos", 0);

        // Initialisiere keyboardWasVisible auf false, da die Tastatur neu erstellt wird
        keyboardWasVisible = false;

        // Zurücksetzen der Wort-löschen Feature-Variablen
        selectedWordCount = 0;
        initialCursorPosition = 0;
        startXPosition = 0;

        dictateKeyboardView = (ConstraintLayout) LayoutInflater.from(context).inflate(R.layout.activity_dictate_keyboard_view, null);
        ViewCompat.setOnApplyWindowInsetsListener(dictateKeyboardView, (v, insets) -> {
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;  // fix for overlapping with navigation bar on Android 15+
        });

        // set background of dictateKeyboardView according to device theme (default in layout is already light)
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            dictateKeyboardView.setBackgroundColor(getResources().getColor(R.color.dictate_keyboard_background_dark, getTheme()));
        }

        settingsButton = dictateKeyboardView.findViewById(R.id.settings_btn);
        recordButton = dictateKeyboardView.findViewById(R.id.record_btn);
        stopButton = dictateKeyboardView.findViewById(R.id.stop_btn);
        stopSwitchButton = dictateKeyboardView.findViewById(R.id.stop_switch_btn);
        resendButton = dictateKeyboardView.findViewById(R.id.resend_btn);
        backspaceButton = dictateKeyboardView.findViewById(R.id.backspace_btn);
        switchButton = dictateKeyboardView.findViewById(R.id.switch_btn);
        trashButton = dictateKeyboardView.findViewById(R.id.trash_btn);
        spaceButton = dictateKeyboardView.findViewById(R.id.space_btn);
        pauseButton = dictateKeyboardView.findViewById(R.id.pause_btn);
        enterButton = dictateKeyboardView.findViewById(R.id.enter_btn);

        infoCl = dictateKeyboardView.findViewById(R.id.info_cl);
        infoTv = dictateKeyboardView.findViewById(R.id.info_tv);
        infoYesButton = dictateKeyboardView.findViewById(R.id.info_yes_btn);
        infoNoButton = dictateKeyboardView.findViewById(R.id.info_no_btn);

        promptsCl = dictateKeyboardView.findViewById(R.id.prompts_keyboard_cl);
        promptsRv = dictateKeyboardView.findViewById(R.id.prompts_keyboard_rv);
        runningPromptPb = dictateKeyboardView.findViewById(R.id.prompts_keyboard_running_pb);
        runningPromptTv = dictateKeyboardView.findViewById(R.id.prompts_keyboard_running_prompt_tv);

        editSelectAllButton = dictateKeyboardView.findViewById(R.id.edit_select_all_btn);
        editUndoButton = dictateKeyboardView.findViewById(R.id.edit_undo_btn);
        editRedoButton = dictateKeyboardView.findViewById(R.id.edit_redo_btn);
        editCutButton = dictateKeyboardView.findViewById(R.id.edit_cut_btn);
        editCopyButton = dictateKeyboardView.findViewById(R.id.edit_copy_btn);
        editPasteButton = dictateKeyboardView.findViewById(R.id.edit_paste_btn);

        overlayCharactersLl = dictateKeyboardView.findViewById(R.id.overlay_characters_ll);

        promptsRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // if user id is not set, set a random number as user id
        if (sp.getString("net.devemperor.dictate.user_id", "null").equals("null")) {
            sp.edit().putString("net.devemperor.dictate.user_id", String.valueOf((int) (Math.random() * 1000000))).apply();
        }

        recordTimeRunnable = new Runnable() {  // runnable to update the record button time text
            @Override
            public void run() {
                elapsedTime += 100;
                // Performance-Optimierung: Nur aktualisieren, wenn der Text sich tatsächlich ändert
                String newText = getString(R.string.dictate_send,
                        String.format(Locale.getDefault(), "%02d:%02d", (int) (elapsedTime / 60000), (int) (elapsedTime / 1000) % 60));
                if (!stopButton.getText().equals(newText)) {
                    stopButton.setText(newText);
                }
                // Performance-Optimierung: Prüfung auf null bevor postDelayed aufgerufen wird
                if (recordTimeHandler != null) {
                    recordTimeHandler.postDelayed(this, 100);
                }
            }
        };

        // initialize audio manager to stop and start background audio
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChange -> {
                    Log.d("DictateInputMethodService", "Audio focus change: " + focusChange);
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        Log.d("DictateInputMethodService", "Audio focus lost, stopping recording if active");
                        if (isRecording) pauseButton.performClick();
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Log.d("DictateInputMethodService", "Audio focus temporarily lost");
                        // Pause recording but don't stop it completely
                        if (isRecording && !isPaused) pauseButton.performClick();
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        Log.d("DictateInputMethodService", "Audio focus gained");
                        // Resume recording if it was paused
                        if (isRecording && isPaused) pauseButton.performClick();
                    }
                })
                .build();

        settingsButton.setOnClickListener(v -> {
            if (isRecording) trashButton.performClick();
            infoCl.setVisibility(View.GONE);
            openSettingsActivity();
        });

        recordButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_mic_20, 0, R.drawable.ic_baseline_folder_open_20, 0);
        recordButton.setOnClickListener(v -> {
            vibrate();

            infoCl.setVisibility(View.GONE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                openSettingsActivity();
            } else {
                startRecording();
            }
        });

        recordButton.setOnLongClickListener(v -> {
            vibrate();

            if (!isRecording) {  // open real settings activity to start file picker
                Intent intent = new Intent(this, DictateSettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("net.devemperor.dictate.open_file_picker", true);
                startActivity(intent);
            }
            return true;
        });

        stopButton.setOnClickListener(v -> {
            vibrate();
            stopRecording();
        });

        stopSwitchButton.setOnClickListener(v -> {
            vibrate();
            shouldSwitchImeAfterTranscription = true;
            stopRecording();
        });

        resendButton.setOnClickListener(v -> {
            vibrate();
            // if user clicked on resendButton without error before, audioFile is default audio
            if (audioFile == null) audioFile = new File(getCacheDir(), sp.getString("net.devemperor.dictate.last_file_name", "audio.m4a"));
            startWhisperApiRequest();
        });

        backspaceButton.setOnClickListener(v -> {
            vibrate();
            deleteOneCharacter();
        });

        backspaceButton.setOnLongClickListener(v -> {
            isDeleting = true;
            startDeleteTime = System.currentTimeMillis();
            currentDeleteDelay = 50;
            deleteRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isDeleting) {
                        deleteOneCharacter();
                        long diff = System.currentTimeMillis() - startDeleteTime;
                        if (diff > 1500 && currentDeleteDelay == 50) {
                            vibrate();
                            currentDeleteDelay = 25;
                        } else if (diff > 3000 && currentDeleteDelay == 25) {
                            vibrate();
                            currentDeleteDelay = 10;
                        } else if (diff > 5000 && currentDeleteDelay == 10) {
                            vibrate();
                            currentDeleteDelay = 5;
                        }
                        // Performance-Optimierung: Prüfung auf null bevor postDelayed aufgerufen wird
                        if (deleteHandler != null) {
                            deleteHandler.postDelayed(this, currentDeleteDelay);
                        }
                    }
                }
            };
            // Performance-Optimierung: Prüfung auf null bevor post aufgerufen wird
            if (deleteHandler != null && deleteRunnable != null) {
                deleteHandler.post(deleteRunnable);
            }
            return true;
        });

        backspaceButton.setOnTouchListener((v, event) -> {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Speichere die initiale Cursorposition
                        ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
                        if (extractedText != null) {
                            initialCursorPosition = extractedText.startOffset + extractedText.selectionStart;
                        } else {
                            // Fallback: Hole die aktuelle Cursorposition
                            CharSequence textBeforeCursor = inputConnection.getTextBeforeCursor(0, 0);
                            initialCursorPosition = textBeforeCursor != null ? textBeforeCursor.length() : 0;
                        }
                        selectedWordCount = 0;
                        startXPosition = event.getX();
                        backspaceButton.setTag(startXPosition);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float startX = (float) backspaceButton.getTag();
                        float currentX = event.getX();
                        float deltaX = currentX - startX;

                        // Berechne die Anzahl der zu selektierenden Wörter basierend auf der Wischdistanz
                        if (deltaX < -30) { // Mindestens 30px nach links
                            int wordsToSelect = Math.abs((int) (deltaX / SWIPE_THRESHOLD_PER_WORD)) + 1; // +1, damit auch bei kurzen Swipes ein Wort selektiert wird

                            // Nur aktualisieren, wenn sich die Anzahl geändert hat
                            if (wordsToSelect != selectedWordCount) {
                                selectedWordCount = wordsToSelect;
                                selectWords(inputConnection, selectedWordCount);
                            }
                        } else if (selectedWordCount > 0) {
                            // Zurücksetzen der Selektion, wenn nicht mehr nach links gewischt wird
                            selectedWordCount = 0;
                            inputConnection.setSelection(initialCursorPosition, initialCursorPosition);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        // Lösche den selektierten Text beim Loslassen
                        if (selectedWordCount > 0) {
                            inputConnection.commitText("", 1);
                            selectedWordCount = 0;
                            return true; // Verhindere die normale onClick-Aktion
                        }
                        break;
                }
            }
            return false; // Erlaube die normale onClick-Aktion für normales Tippen
        });

        switchButton.setOnClickListener(v -> {
            vibrate();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Debugging-Ausgabe für den Switch-Button
                Log.d("DictateInputMethodService", "Switch button clicked");

                // Prüfen, ob die vorherige Tastatur dieselbe ist wie die aktuelle
                if (isPreviousImeSameAsCurrent()) {
                    // Debugging-Ausgabe
                    Log.d("DictateInputMethodService", "Previous IME is same as current or current is default, showing input method picker");

                    // Wenn die vorherige Tastatur dieselbe ist oder die aktuelle Tastatur die Standardtastatur ist,
                    // zeige das Tastaturwechsel-Overlay an
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showInputMethodPicker();
                    }
                } else {
                    // Debugging-Ausgabe
                    Log.d("DictateInputMethodService", "Attempting to switch to previous IME");

                    // Versuche zur vorherigen Tastatur zu wechseln
                    boolean switched = switchToPreviousInputMethod();

                    // Debugging-Ausgabe für das Ergebnis des Wechsels
                    Log.d("DictateInputMethodService", "Switch to previous IME result: " + switched);

                    // Wenn der Wechsel nicht erfolgreich war, zeige das Tastaturwechsel-Overlay an
                    if (!switched) {
                        Log.d("DictateInputMethodService", "Switch failed, showing input method picker");
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showInputMethodPicker();
                        }
                    }
                }
            }
        });

        switchButton.setOnLongClickListener(v -> {
            vibrate();

            currentInputLanguagePos++;
            recordButton.setText(getDictateButtonText());
            return true;
        });

        // trash button to abort the recording and reset all variables and views
        trashButton.setOnClickListener(v -> {
            vibrate();
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (RuntimeException ignored) { }
                recorder.release();
                recorder = null;

                if (recordTimeRunnable != null) {
                    recordTimeHandler.removeCallbacks(recordTimeRunnable);
                }
            }
            if (audioFocusEnabled) am.abandonAudioFocusRequest(audioFocusRequest);

            isRecording = false;
            // Setze den Recording-Status im Adapter
            if (promptsAdapter != null) {
                promptsAdapter.setIsRecording(false);
            }
            isPaused = false;
            instantPrompt = false;
            recordButton.setText(getDictateButtonText());
            recordButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_mic_20, 0, R.drawable.ic_baseline_folder_open_20, 0);
            recordButton.setEnabled(true);
            stopButton.setVisibility(View.GONE);
            stopSwitchButton.setVisibility(View.GONE);
            recordButton.setVisibility(View.VISIBLE);
            pauseButton.setVisibility(View.GONE);
            pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_pause_24));
            trashButton.setVisibility(View.GONE);
            resendButton.setVisibility(View.GONE);
            infoCl.setVisibility(View.GONE);
            // Reset record button color to original blue
            //recordButton.setBackgroundColor(getResources().getColor(R.color.dictate_blue, getTheme()));
            stopSwitchButton.setBackgroundColor(getResources().getColor(R.color.dictate_blue, getTheme()));

        });

        // space button that changes cursor position if user swipes over it
        spaceButton.setOnTouchListener((v, event) -> {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                spaceButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_keyboard_double_arrow_left_24,
                        0, R.drawable.ic_baseline_keyboard_double_arrow_right_24, 0);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        spaceButtonUserHasSwiped = false;
                        spaceButton.setTag(event.getX());
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float x = (float) spaceButton.getTag();
                        if (event.getX() - x > 30) {
                            vibrate();
                            inputConnection.commitText("", 2);
                            spaceButton.setTag(event.getX());
                            spaceButtonUserHasSwiped = true;
                        } else if (x - event.getX() > 30) {
                            vibrate();
                            inputConnection.commitText("", -1);
                            spaceButton.setTag(event.getX());
                            spaceButtonUserHasSwiped = true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (!spaceButtonUserHasSwiped) {
                            vibrate();
                            inputConnection.commitText(" ", 1);
                        }
                        spaceButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                        break;
                }
            } else {
                spaceButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            }
            return false;
        });

        pauseButton.setOnClickListener(v -> {
            vibrate();
            if (recorder != null) {
                if (isPaused) {
                    if (audioFocusEnabled) am.requestAudioFocus(audioFocusRequest);
                    recorder.resume();
                    recordTimeHandler.post(recordTimeRunnable);
                    pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_pause_24));
                    isPaused = false;
                    // Set record button background to light green (active recording)
                    stopButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green, getTheme()));
                    stopSwitchButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green, getTheme()));
                } else {
                    if (audioFocusEnabled) am.abandonAudioFocusRequest(audioFocusRequest);
                    recorder.pause();
                    recordTimeHandler.removeCallbacks(recordTimeRunnable);
                    pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_mic_24));
                    isPaused = true;
                    // Set record button background to a different green (paused)
                    stopButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green_paused, getTheme()));
                    stopSwitchButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green_paused, getTheme()));
                }
            }
        });

        enterButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                EditorInfo editorInfo = getCurrentInputEditorInfo();
                if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
                    inputConnection.commitText("\n", 1);
                } else {
                    inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                }
            }
        });

        enterButton.setOnLongClickListener(v -> {
            vibrate();
            overlayCharactersLl.setVisibility(View.VISIBLE);
            return true;
        });

        enterButton.setOnTouchListener((v, event) -> {
            if (overlayCharactersLl.getVisibility() == View.VISIBLE) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        for (int i = 0; i < overlayCharactersLl.getChildCount(); i++) {
                            MaterialButton charView = (MaterialButton) overlayCharactersLl.getChildAt(i);
                            if (isPointInsideView(event.getRawX(), charView)) {
                                if (selectedCharacter != charView) {
                                    selectedCharacter = charView;
                                    highlightSelectedCharacter(selectedCharacter);
                                }
                                break;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (selectedCharacter != null) {
                            InputConnection inputConnection = getCurrentInputConnection();
                            if (inputConnection != null) {
                                inputConnection.commitText(selectedCharacter.getText(), 1);
                            }
                            highlightSelectedCharacter(selectedCharacter);
                            selectedCharacter = null;
                        }
                        overlayCharactersLl.setVisibility(View.GONE);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        overlayCharactersLl.setVisibility(View.GONE);
                        return true;
                }
            }
            return false;
        });

        editSelectAllButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);

                if (inputConnection.getSelectedText(0) == null && extractedText.text.length() > 0) {
                    inputConnection.performContextMenuAction(android.R.id.selectAll);
                    editSelectAllButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_deselect_24));
                } else {
                    inputConnection.clearMetaKeyStates(0);
                    if (extractedText == null || extractedText.text == null) {
                        inputConnection.setSelection(0, 0);
                    } else {
                        inputConnection.setSelection(extractedText.text.length(), extractedText.text.length());
                    }
                    editSelectAllButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_select_all_24));
                }
            }
        });

        // initialize all edit buttons
        Object[][] buttonsActions = {
                { editUndoButton, android.R.id.undo },
                { editRedoButton, android.R.id.redo },
                { editCutButton,  android.R.id.cut },
                { editCopyButton, android.R.id.copy },
                { editPasteButton, android.R.id.paste }
        };

        for (Object[] pair : buttonsActions) {
            ((Button) pair[0]).setOnClickListener(v -> {
                vibrate();
                InputConnection inputConnection = getCurrentInputConnection();
                if (inputConnection != null) {
                    inputConnection.performContextMenuAction((int) pair[1]);
                }
            });
        }

        // initialize overlay characters
        for (int i = 0; i < 14; i++) {
            MaterialButton charView = (MaterialButton) LayoutInflater.from(context).inflate(R.layout.item_overlay_characters, overlayCharactersLl, false);
            overlayCharactersLl.addView(charView);
        }

        return dictateKeyboardView;
    }

    // method is called if the user closed the keyboard
    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) { }
            recorder.release();
            recorder = null;

            if (recordTimeRunnable != null) {
                recordTimeHandler.removeCallbacks(recordTimeRunnable);
            }
        }

        if (speechApiThread != null) speechApiThread.shutdownNow();
        if (rewordingApiThread != null) rewordingApiThread.shutdownNow();

        // Clean up handlers and runnables
        if (deleteHandler != null && deleteRunnable != null) {
            deleteHandler.removeCallbacks(deleteRunnable);
        }
        if (recordTimeHandler != null && recordTimeRunnable != null) {
            recordTimeHandler.removeCallbacks(recordTimeRunnable);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (deleteHandler != null) {
            deleteHandler.removeCallbacksAndMessages(null);
        }
        if (recordTimeHandler != null) {
            recordTimeHandler.removeCallbacksAndMessages(null);
        }

        pauseButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_pause_24));
        stopButton.setVisibility(View.GONE);
        stopSwitchButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.GONE);
        trashButton.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
        infoCl.setVisibility(View.GONE);
        isRecording = false;
        isPaused = false;
        instantPrompt = false;
        if (audioFocusEnabled) am.abandonAudioFocusRequest(audioFocusRequest);
        recordButton.setText(R.string.dictate_record);
        recordButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_mic_20, 0, R.drawable.ic_baseline_folder_open_20, 0);
        recordButton.setEnabled(true);

        // Setze keyboardWasVisible auf false, da die Tastatur jetzt geschlossen ist
        keyboardWasVisible = false;

        // Zurücksetzen des temporären alwaysUse Prompts
        temporaryAlwaysUsePrompt = null;
        if (promptsAdapter != null) {
            promptsAdapter.clearTemporaryAlwaysUsePrompt();
            // Setze den Recording-Status im Adapter zurück
            promptsAdapter.setIsRecording(false);
        }

        // Zurücksetzen der Wort-löschen Feature-Variablen
        selectedWordCount = 0;
        initialCursorPosition = 0;
        startXPosition = 0;
    }

    // method is called if the keyboard appears again
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        // Setze imeJustBound nur, wenn die Tastatur nicht bereits sichtbar war
        if (!keyboardWasVisible) {
            imeJustBound = true;
        }

        // Reinitialize handlers if they were cleaned up
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        if (deleteHandler == null) deleteHandler = new Handler();
        if (recordTimeHandler == null) recordTimeHandler = new Handler(Looper.getMainLooper());

        // Zurücksetzen der Wort-löschen Feature-Variablen
        selectedWordCount = 0;
        initialCursorPosition = 0;
        startXPosition = 0;

        // Add a small delay before checking for instant recording to ensure proper initialization
        mainHandler.postDelayed(() -> {
            if (sp.getBoolean("net.devemperor.dictate.rewording_enabled", true)) {
                promptsCl.setVisibility(View.VISIBLE);

                // collect all prompts from database
                List<PromptModel> data;
                InputConnection inputConnection = getCurrentInputConnection();
                // Improve text selection detection
                boolean noTextSelected = true;
                if (inputConnection != null) {
                    CharSequence selectedText = inputConnection.getSelectedText(0);
                    noTextSelected = selectedText == null || selectedText.length() == 0;
                }

                // No text selected, show all instant prompts and set select all icon
                data = promptsDb.getAll(true);
                editSelectAllButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_select_all_24));

                promptsAdapter = new PromptsKeyboardAdapter(data, position -> {
                    vibrate();
                    PromptModel model = data.get(position);

                    if (model.getId() == -1) {  // instant prompt clicked
                        instantPrompt = true;
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            openSettingsActivity();
                        } else if (!isRecording) {
                            startRecording();
                        } else {
                            stopRecording();
                        }
                    } else if (model.getId() == -2) {  // add prompt clicked
                        Intent intent = new Intent(this, PromptsOverviewActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } else {
                        // Check text selection at the time of button click
                        InputConnection inputConnection2 = getCurrentInputConnection();
                        String selectedText = null;
                        boolean noTextSelected2 = true;
                        if (inputConnection2 != null) {
                            CharSequence selected = inputConnection2.getSelectedText(0);
                            if (selected != null && selected.length() > 0) {
                                selectedText = selected.toString();
                                noTextSelected2 = false;
                            }
                        }

                        // Only select all text if no text is currently selected
                        if(noTextSelected2) {
                            if (inputConnection2 != null) {
                                inputConnection2.performContextMenuAction(android.R.id.selectAll);
                                // Get the newly selected text
                                CharSequence newlySelected = inputConnection2.getSelectedText(0);
                                if (newlySelected != null && newlySelected.length() > 0) {
                                    selectedText = newlySelected.toString();
                                }
                            }
                        }

                        // If still no text is selected after trying to select all, abort the operation
                        if (selectedText == null || selectedText.isEmpty()) {
                            // Abort the operation - don't call startGPTApiRequest
                            return;
                        }

                        startGPTApiRequest(model, selectedText);  // another normal prompt clicked
                    }
                }, position -> {
                    vibrate();
                    PromptModel model = data.get(position);
                    if (model.getId() != -1 && model.getId() != -2) {  // Nur für echte Prompts, nicht für die speziellen Buttons
                        // Prüfe, ob dieser Prompt bereits der temporäre alwaysUse Prompt ist
                        if (temporaryAlwaysUsePrompt != null && model.getId() == temporaryAlwaysUsePrompt.getId()) {
                            // Wenn ja, dann entferne den temporären alwaysUse Prompt
                            temporaryAlwaysUsePrompt = null;

                            // Aktualisiere die Anzeige
                            if (promptsAdapter != null) {
                                promptsAdapter.clearTemporaryAlwaysUsePrompt();
                            }
                        } else {
                            // Wenn nein, setze diesen Prompt als temporären alwaysUse Prompt
                            temporaryAlwaysUsePrompt = model;

                            // Aktualisiere die Anzeige
                            if (promptsAdapter != null) {
                                promptsAdapter.setTemporaryAlwaysUsePrompt(model);
                            }
                        }
                    }
                }, position -> {
                    // Double-click handler - open prompt edit activity
                    PromptModel model = data.get(position);
                    if (model.getId() != -1 && model.getId() != -2) {  // Nur für echte Prompts, nicht für die speziellen Buttons
                        Intent intent = new Intent(this, PromptEditActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("net.devemperor.dictate.prompt_edit_activity_id", model.getId());
                        startActivity(intent);
                    }
                });
                // Setze den Recording-Status im Adapter
                promptsAdapter.setIsRecording(isRecording);
                promptsRv.setAdapter(promptsAdapter);
            } else {
                promptsCl.setVisibility(View.GONE);
            }

            // enable resend button if previous audio file still exists in cache
            if (new File(getCacheDir(), sp.getString("net.devemperor.dictate.last_file_name", "audio.m4a")).exists()
                    && sp.getBoolean("net.devemperor.dictate.resend_button", false)) {
                resendButton.setVisibility(View.VISIBLE);
            } else {
                resendButton.setVisibility(View.GONE);
            }

            // fill all overlay characters
            String charactersString = sp.getString("net.devemperor.dictate.overlay_characters", "()-:!?,.");
            // Use BreakIterator to properly handle Unicode grapheme clusters (like emojis)
            java.text.BreakIterator iterator = java.text.BreakIterator.getCharacterInstance();
            iterator.setText(charactersString);
            int start = iterator.first();
            int end = iterator.next();
            int index = 0;

            while (end != java.text.BreakIterator.DONE && index < overlayCharactersLl.getChildCount()) {
                TextView charView = (TextView) overlayCharactersLl.getChildAt(index);
                charView.setVisibility(View.VISIBLE);
                charView.setText(charactersString.substring(start, end));
                start = end;
                end = iterator.next();
                index++;
            }

            // Hide any remaining unused character views
            for (int i = index; i < overlayCharactersLl.getChildCount(); i++) {
                TextView charView = (TextView) overlayCharactersLl.getChildAt(i);
                charView.setVisibility(View.GONE);
            }

            // get the currently selected input language
            recordButton.setText(getDictateButtonText());

            // check if user enabled audio focus
            audioFocusEnabled = sp.getBoolean("net.devemperor.dictate.audio_focus", true);

            // show infos for updates, ratings or donations
            long totalAudioTime = usageDb.getTotalAudioTime();
            if (sp.getInt("net.devemperor.dictate.last_version_code", 0) < BuildConfig.VERSION_CODE) {
                showInfo("update");
            } /*else if (totalAudioTime > 180 && totalAudioTime <= 600 && !sp.getBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", false)) {
                showInfo("rate");  // in case someone had Dictate installed before, he shouldn't get both messages
            } else if (totalAudioTime > 600 && !sp.getBoolean("net.devemperor.dictate.flag_has_donated", false)) {
                showInfo("donate");
            }*/

            // start audio file transcription if user selected an audio file
            if (!sp.getString("net.devemperor.dictate.transcription_audio_file", "").isEmpty()) {
                audioFile = new File(getCacheDir(), sp.getString("net.devemperor.dictate.transcription_audio_file", ""));
                sp.edit().putString("net.devemperor.dictate.last_file_name", audioFile.getName()).apply();

                sp.edit().remove("net.devemperor.dictate.transcription_audio_file").apply();
                startWhisperApiRequest();

            } else if (sp.getBoolean("net.devemperor.dictate.instant_recording", false) && imeJustBound) {
                // Add a small delay before starting instant recording to ensure proper initialization
                mainHandler.postDelayed(() -> {
                    if (imeJustBound) {  // Double-check that imeJustBound is still true
                        imeJustBound = false;
                        recordButton.performClick();
                    }
                }, 100);  // 100ms delay
            }

            // Setze keyboardWasVisible auf true, da die Tastatur jetzt sichtbar ist
            keyboardWasVisible = true;
        }, 50);  // 50ms delay for overall initialization
    }

    // method is called if user changed text selection
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onUpdateSelection (int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);

        // refill all prompts
        if (sp != null && sp.getBoolean("net.devemperor.dictate.rewording_enabled", true)) {
            List<PromptModel> data;

            // Always show all instant prompts even if no text is selected
            data = promptsDb.getAll(true);
            editSelectAllButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_select_all_24));

            promptsAdapter.getData().clear();
            promptsAdapter.getData().addAll(data);
            promptsAdapter.notifyDataSetChanged();
        }
    }

    private void vibrate() {
        if (vibrationEnabled) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, DictateSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startRecording() {
        // Check if we're already recording
        if (isRecording) {
            return;
        }

        audioFile = new File(getCacheDir(), "audio.m4a");
        sp.edit().putString("net.devemperor.dictate.last_file_name", audioFile.getName()).apply();

        boolean useBluetoothMic = sp.getBoolean("net.devemperor.dictate.use_bluetooth_mic", true);
        boolean bluetoothAvailable = isBluetoothScoAvailable();

        if (useBluetoothMic && bluetoothAvailable) {
            startBluetoothSco();
        }

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC); // This will pick up from Bluetooth SCO when SCO is active
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(audioFile);

        if (audioFocusEnabled) am.requestAudioFocus(audioFocusRequest);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            sendLogToCrashlytics(e);
            // Show error to user
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            showInfo("internet_error");
            return;
        } catch (IllegalStateException e) {
            sendLogToCrashlytics(e);
            // Show error to user
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            showInfo("internet_error");
            return;
        }

        recordButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
        stopSwitchButton.setVisibility(View.VISIBLE);
        stopButton.setText(R.string.dictate_send);
        stopButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_send_20, 0, 0, 0);
        pauseButton.setVisibility(View.VISIBLE);
        trashButton.setVisibility(View.VISIBLE);
        resendButton.setVisibility(View.GONE);
        isRecording = true;
        // Setze den Recording-Status im Adapter
        if (promptsAdapter != null) {
            promptsAdapter.setIsRecording(true);
        }
        // Set stop button background to light green
        stopButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green, getTheme()));
        stopSwitchButton.setBackgroundColor(getResources().getColor(R.color.dictate_recording_green, getTheme()));
        elapsedTime = 0;
        recordTimeHandler.post(recordTimeRunnable);

        // Log the start of recording for debugging
        Log.d("DictateInputMethodService", "Recording started successfully");
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                Log.d("DictateInputMethodService", "Recording stopped successfully");
            } catch (RuntimeException e) {
                Log.w("DictateInputMethodService", "Error stopping recording: " + e.getMessage());
                // This can happen if recording was too short or other issues
            }
            try {
                recorder.release();
            } catch (RuntimeException e) {
                Log.w("DictateInputMethodService", "Error releasing recorder: " + e.getMessage());
            }
            recorder = null;

            if (isBluetoothScoStarted) {
                stopBluetoothSco();
            }

            if (recordTimeRunnable != null) {
                recordTimeHandler.removeCallbacks(recordTimeRunnable);
            }
            stopButton.setVisibility(View.GONE);
            stopSwitchButton.setVisibility(View.GONE);
            recordButton.setVisibility(View.VISIBLE);

            // Reset record button color to original blue
            //recordButton.setBackgroundColor(getResources().getColor(R.color.dictate_blue, getTheme()));
            //stopSwitchButton.setBackgroundColor(getResources().getColor(R.color.dictate_blue, getTheme()));
            startWhisperApiRequest();
        } else {
            Log.w("DictateInputMethodService", "stopRecording called but recorder is null");
        }
    }

    private void startWhisperApiRequest() {
        recordButton.setText(R.string.dictate_sending);
        recordButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_send_20, 0, 0, 0);
        recordButton.setEnabled(false);
        pauseButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_pause_24));
        stopButton.setVisibility(View.GONE);
        stopSwitchButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.GONE);
        trashButton.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
        infoCl.setVisibility(View.GONE);
        isRecording = false;
        // Setze den Recording-Status im Adapter
        if (promptsAdapter != null) {
            promptsAdapter.setIsRecording(false);
        }
        isPaused = false;

        if (audioFocusEnabled)
            am.abandonAudioFocusRequest(audioFocusRequest);

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

        // Shutdown any existing thread before creating a new one
        if (speechApiThread != null && !speechApiThread.isShutdown()) {
            speechApiThread.shutdownNow();
        }
        speechApiThread = Executors.newSingleThreadExecutor();
        speechApiThread.execute(() -> {
            try {
                // Use the shared transcription function
                String resultText;
                try {
                    resultText = transcribeAudioFile(this, audioFile, usageDb, currentInputLanguageValue, stylePrompt);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (!instantPrompt)
                {
                    // Prüfe, ob ein "alwaysUse" Prompt vorhanden ist
                    PromptModel alwaysUsePrompt = temporaryAlwaysUsePrompt != null ? temporaryAlwaysUsePrompt : promptsDb.getAlwaysUsePrompt();

                    InputConnection inputConnection = getCurrentInputConnection();
                    if (inputConnection != null) {
                        if (alwaysUsePrompt != null) {
                            // Wenn ein "alwaysUse" Prompt vorhanden ist, wende ihn auf die Transkription an
                            startGPTApiRequest(alwaysUsePrompt, resultText);
                        } else {
                            // Kein "alwaysUse" Prompt, füge den Text direkt ein
                            boolean instantOutputEnabled = sp.getBoolean("net.devemperor.dictate.instant_output", false);
                            if (instantOutputEnabled) {
                                inputConnection.commitText(resultText, 1);

                                // Switch IME if flag is set
                                if (shouldSwitchImeAfterTranscription) {
                                    shouldSwitchImeAfterTranscription = false;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        switchToPreviousInputMethod();
                                    }
                                }
                            } else {
                                int speed = sp.getInt("net.devemperor.dictate.output_speed", 5);
                                // Schrittweise Textausgabe (kann in den Einstellungen aktiviert werden)
                                for (int i = 0; i < resultText.length(); i++) {
                                    char character = resultText.charAt(i);
                                    mainHandler.postDelayed(() -> inputConnection.commitText(String.valueOf(character), 1), (long) (i * (20L / (speed / 5f))));
                                }
                            }
                        }
                    }
                } else {
                    // continue with ChatGPT API request
                    instantPrompt = false;
                    // Get selected text for instant prompt
                    String selectedText = null;
                    InputConnection inputConnection = getCurrentInputConnection();
                    if (inputConnection != null) {
                        CharSequence selected = inputConnection.getSelectedText(0);
                        if (selected != null && selected.length() > 0) {
                            selectedText = selected.toString();
                        }
                    }
                    startGPTApiRequest(new PromptModel(-1, Integer.MIN_VALUE, "", resultText, false), selectedText);
                }

                if (new File(getCacheDir(), sp.getString("net.devemperor.dictate.last_file_name", "audio.m4a")).exists()
                        && sp.getBoolean("net.devemperor.dictate.resend_button", false)) {
                    mainHandler.post(() -> resendButton.setVisibility(View.VISIBLE));
                }

            } catch (RuntimeException e) {
                // Detailliertes Logging des Fehlers
                Log.e("DictateAPI", "Fehler bei der Transkriptionsanfrage", e);

                if (!(e.getCause() instanceof InterruptedIOException)) {
                    sendLogToCrashlytics(e);
                    if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    // Performance-Optimierung: Prüfung auf null bevor post aufgerufen wird
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            resendButton.setVisibility(View.VISIBLE);
                            String message = Objects.requireNonNull(e.getMessage()).toLowerCase();
                            Log.e("DictateAPI", "Fehlermeldung: " + message);

                            if (message.contains("api key")) {
                                Log.e("DictateAPI", "Ungültiger API-Schlüssel erkannt");
                                showInfo("invalid_api_key");
                            } else if (message.contains("quota")) {
                                Log.e("DictateAPI", "Quota überschritten");
                                showInfo("quota_exceeded");
                            } else if (message.contains("audio duration") || message.contains("content size limit")) {  // gpt-o-transcribe and whisper have different limits
                                Log.e("DictateAPI", "Audioinhalt zu groß");
                                showInfo("content_size_limit");
                            } else if (message.contains("format")) {
                                Log.e("DictateAPI", "Nicht unterstütztes Audioformat");
                                showInfo("format_not_supported");
                            } else {
                                Log.e("DictateAPI", "Allgemeiner Internetfehler");
                                showInfo("internet_error");
                            }
                        });
                    }
                } else if (e.getCause().getMessage() != null && (e.getCause().getMessage().contains("timeout") || e.getCause().getMessage().contains("failed to connect"))) {
                    Log.e("DictateAPI", "Timeout oder Verbindungsfehler", e);
                    sendLogToCrashlytics(e);
                    if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    // Performance-Optimierung: Prüfung auf null bevor post aufgerufen wird
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            resendButton.setVisibility(View.VISIBLE);
                            showInfo("timeout");
                        });
                    }
                }
            }

            mainHandler.post(() -> {
                recordButton.setText(getDictateButtonText());
                recordButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_mic_20, 0, R.drawable.ic_baseline_folder_open_20, 0);
                recordButton.setEnabled(true);
            });
        });
    }

    private void startGPTApiRequest(PromptModel model, String selectedText) {
        mainHandler.post(() -> {
            promptsRv.setVisibility(View.GONE);
            runningPromptTv.setVisibility(View.VISIBLE);
            runningPromptTv.setText(model.getId() == -1 ? getString(R.string.dictate_live_prompt) : model.getName());
            runningPromptPb.setVisibility(View.VISIBLE);
            infoCl.setVisibility(View.GONE);
        });

        // Shutdown any existing thread before creating a new one
        if (rewordingApiThread != null && !rewordingApiThread.isShutdown()) {
            rewordingApiThread.shutdownNow();
        }
        rewordingApiThread = Executors.newSingleThreadExecutor();
        rewordingApiThread.execute(() -> {
            try {
                int rewordingProvider = sp.getInt("net.devemperor.dictate.rewording_provider", 0);
                String apiHost = getResources().getStringArray(R.array.dictate_api_providers_values)[rewordingProvider];
                if (apiHost.equals("custom_server")) apiHost = sp.getString("net.devemperor.dictate.rewording_custom_host", getString(R.string.dictate_custom_server_host_hint));

                String apiKey = sp.getString("net.devemperor.dictate.rewording_api_key", sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY")).replaceAll("[^ -~]", "");
                String proxyHost = sp.getString("net.devemperor.dictate.proxy_host", getString(R.string.dictate_settings_proxy_hint));

                String rewordingModel = "";
                switch (rewordingProvider) {
                    case 0: rewordingModel = sp.getString("net.devemperor.dictate.rewording_openai_model", sp.getString("net.devemperor.dictate.rewording_model", "gpt-4o-mini")); break;
                    case 1: rewordingModel = sp.getString("net.devemperor.dictate.rewording_groq_model", "llama-3.3-70b-versatile"); break;
                    case 2: rewordingModel = sp.getString("net.devemperor.dictate.rewording_custom_model", getString(R.string.dictate_custom_rewording_model_hint));
                }

                OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                        .apiKey(apiKey)
                        .baseUrl(apiHost)
                        .timeout(Duration.ofSeconds(120));

                if (sp.getBoolean("net.devemperor.dictate.proxy_enabled", false)) {
                    clientBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.split(":")[0], Integer.parseInt(proxyHost.split(":")[1]))));
                }

                String prompt = model.getPrompt();
                String rewordedText;
                if (prompt.startsWith("[") && prompt.endsWith("]")) {
                    rewordedText = prompt.substring(1, prompt.length() - 1);
                }
                else
                {
                    prompt += "\n\n" + DictateUtils.PROMPT_REWORDING_BE_PRECISE;
                    if (selectedText != null) {
                        prompt += "\n\n" + selectedText;
                    }

                    // Logging für die API-Anfrage (ohne API-Key)
                    Log.d("DictateAPI", "Rewording-Anfrage - URL: " + apiHost + ", Modell: " + rewordingModel + ", Prompt: " + prompt);

                    ChatCompletionCreateParams chatCompletionCreateParams = ChatCompletionCreateParams.builder()
                            .addUserMessage(prompt)
                            .model(rewordingModel)
                            .build();
                    ChatCompletion chatCompletion = clientBuilder.build().chat().completions().create(chatCompletionCreateParams);
                    rewordedText = chatCompletion.choices().get(0).message().content().orElse("");

                    // Logging der Antwort (ohne API-Key)
                    Log.d("DictateAPI", "Rewording-Antwort erhalten: " + rewordedText);

                    if (chatCompletion.usage().isPresent()) {
                        usageDb.edit(rewordingModel, 0, chatCompletion.usage().get().promptTokens(), chatCompletion.usage().get().completionTokens(), rewordingProvider);
                    }
                }

                InputConnection inputConnection = getCurrentInputConnection();
                boolean instantOutputEnabled = sp.getBoolean("net.devemperor.dictate.instant_output", false);
                if (inputConnection != null) {
                    if (instantOutputEnabled) {
                        inputConnection.commitText(rewordedText, 1);

                        if(shouldSwitchImeAfterTranscription) {
                            // Switch IME if flag is set
                            shouldSwitchImeAfterTranscription = false;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                switchToPreviousInputMethod();
                            }
                        }
                    } else {
                        int speed = sp.getInt("net.devemperor.dictate.output_speed", 5);
                        // Schrittweise Textausgabe (kann in den Einstellungen aktiviert werden)
                        for (int i = 0; i < rewordedText.length(); i++) {
                            char character = rewordedText.charAt(i);
                            mainHandler.postDelayed(() -> inputConnection.commitText(String.valueOf(character), 1), (long) (i * (20L / (speed / 5f))));
                        }
                    }
                }
            } catch (RuntimeException e) {
                // Detailliertes Logging des Fehlers
                Log.e("DictateAPI", "Fehler bei der Rewording-Anfrage", e);

                if (!(e.getCause() instanceof InterruptedIOException)) {
                    sendLogToCrashlytics(e);
                    if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    // Performance-Optimierung: Prüfung auf null bevor post aufgerufen wird
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            resendButton.setVisibility(View.VISIBLE);
                            String message = Objects.requireNonNull(e.getMessage()).toLowerCase();
                            Log.e("DictateAPI", "Fehlermeldung: " + message);

                            if (message.contains("api key")) {
                                Log.e("DictateAPI", "Ungültiger API-Schlüssel erkannt");
                                showInfo("invalid_api_key");
                            } else if (message.contains("quota")) {
                                Log.e("DictateAPI", "Quota überschritten");
                                showInfo("quota_exceeded");
                            } else {
                                Log.e("DictateAPI", "Allgemeiner Internetfehler");
                                showInfo("internet_error");
                            }
                        });
                    }
                } else if (e.getCause().getMessage() != null && e.getCause().getMessage().contains("timeout")) {
                    Log.e("DictateAPI", "Timeout bei der Rewording-Anfrage", e);
                    sendLogToCrashlytics(e);
                    if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    // Performance-Optimierung: Prüfung auf null bevor post aufgerufen wird
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            resendButton.setVisibility(View.VISIBLE);
                            showInfo("timeout");
                        });
                    }
                }
            }

            mainHandler.post(() -> {
                promptsRv.setVisibility(View.VISIBLE);
                runningPromptTv.setVisibility(View.GONE);
                runningPromptPb.setVisibility(View.GONE);
            });
        });
    }

    private void sendLogToCrashlytics(Exception e) {
        // get all values from SharedPreferences and add them as custom keys to crashlytics
        /*FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        for (String key : sp.getAll().keySet()) {
            Object value = sp.getAll().get(key);
            if (value instanceof Boolean) {
                crashlytics.setCustomKey(key, (Boolean) value);
            } else if (value instanceof Float) {
                crashlytics.setCustomKey(key, (Float) value);
            } else if (value instanceof Integer) {
                crashlytics.setCustomKey(key, (Integer) value);
            } else if (value instanceof Long) {
                crashlytics.setCustomKey(key, (Long) value);
            } else if (value instanceof String) {
                crashlytics.setCustomKey(key, (String) value);
            }
        }
        crashlytics.setUserId(sp.getString("net.devemperor.dictate.user_id", "null"));
        crashlytics.recordException(e);
        // */
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        Log.e("DictateInputMethodService", sw.toString());
        Log.e("DictateInputMethodService", "Recorded crashlytics report");
    }

    private void showInfo(String type) {
        infoCl.setVisibility(View.VISIBLE);
        infoNoButton.setVisibility(View.VISIBLE);
        infoTv.setTextColor(getResources().getColor(R.color.dictate_red, getTheme()));
        switch (type) {
            case "update":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_blue, getTheme()));
                infoTv.setText(R.string.dictate_update_installed_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    openSettingsActivity();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putInt("net.devemperor.dictate.last_version_code", BuildConfig.VERSION_CODE).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
            case "rate":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_blue, getTheme()));
                infoTv.setText(R.string.dictate_rate_app_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.devemperor.dictate"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
            case "donate":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_blue, getTheme()));
                infoTv.setText(R.string.dictate_donate_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/DevEmperor"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_donated", true)  // in case someone had Dictate installed before, he shouldn't get both messages
                            .putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_donated", true)
                            .putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
            case "timeout":
                infoTv.setText(R.string.dictate_timeout_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "invalid_api_key":
                infoTv.setText(R.string.dictate_invalid_api_key_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    openSettingsActivity();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "quota_exceeded":
                infoTv.setText(R.string.dictate_quota_exceeded_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/settings/organization/billing/overview"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "content_size_limit":
                infoTv.setText(R.string.dictate_content_size_limit_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "format_not_supported":
                infoTv.setText(R.string.dictate_format_not_supported_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "internet_error":
                infoTv.setText(R.string.dictate_internet_error_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
        }
    }

    private String getDictateButtonText() {
        Set<String> currentInputLanguagesValues = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.dictate_default_input_languages)));
        currentInputLanguagesValues = sp.getStringSet("net.devemperor.dictate.input_languages", currentInputLanguagesValues);
        List<String> allLanguagesValues = Arrays.asList(getResources().getStringArray(R.array.dictate_input_languages_values));
        List<String> recordDifferentLanguages = Arrays.asList(getResources().getStringArray(R.array.dictate_record_different_languages));

        if (currentInputLanguagePos >= currentInputLanguagesValues.size()) currentInputLanguagePos = 0;
        sp.edit().putInt("net.devemperor.dictate.input_language_pos", currentInputLanguagePos).apply();

        currentInputLanguageValue = currentInputLanguagesValues.toArray()[currentInputLanguagePos].toString();
        return recordDifferentLanguages.get(allLanguagesValues.indexOf(currentInputLanguagesValues.toArray()[currentInputLanguagePos].toString()));
    }

    private void deleteOneCharacter() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            CharSequence selectedText = inputConnection.getSelectedText(0);

            if (selectedText != null) {
                inputConnection.commitText("", 1);
            } else {
                // Use BreakIterator to properly handle Unicode grapheme clusters (like emojis)
                ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
                if (extractedText != null && extractedText.text != null && extractedText.text.length() > 0) {
                    String text = extractedText.text.toString();
                    java.text.BreakIterator iterator = java.text.BreakIterator.getCharacterInstance();
                    iterator.setText(text);

                    // Find the last grapheme cluster
                    int end = iterator.last();
                    int start = iterator.previous();

                    // If we have a valid grapheme cluster, delete it
                    if (start != java.text.BreakIterator.DONE && end != java.text.BreakIterator.DONE) {
                        // Calculate how many Java characters (UTF-16 code units) to delete
                        int charCount = end - start;
                        inputConnection.deleteSurroundingText(charCount, 0);
                    } else {
                        // Fallback to deleting one character if BreakIterator fails
                        inputConnection.deleteSurroundingText(1, 0);
                    }
                } else {
                    // Fallback to deleting one character if no text is available
                    inputConnection.deleteSurroundingText(1, 0);
                }
            }
        }
    }

    private void selectWords(InputConnection inputConnection, int wordCount) {
        // Hole den aktuellen Text vor der Cursor-Position
        ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extractedText != null && extractedText.text != null && extractedText.text.length() > 0) {
            String text = extractedText.text.toString();
            int cursorPosition = initialCursorPosition;

            // Sicherstellen, dass die Cursorposition nicht außerhalb des Textes liegt
            if (cursorPosition > text.length()) {
                cursorPosition = text.length();
            }

            // Berechne die Startposition für die Selektion
            int selectionStart = cursorPosition;
            for (int i = 0; i < wordCount; i++) {
                int wordStart = findWordStart(text, selectionStart);
                if (wordStart >= 0 && wordStart < selectionStart) {
                    selectionStart = wordStart;
                } else {
                    break; // Kein weiteres Wort gefunden
                }
            }

            // Selektiere den Text vom Anfang des ersten Wortes bis zur ursprünglichen Cursorposition
            inputConnection.setSelection(selectionStart, cursorPosition);
        } else {
            // Fallback: Verwende getTextBeforeCursor und getTextAfterCursor
            CharSequence textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0);
            if (textBeforeCursor != null) {
                String text = textBeforeCursor.toString();
                int cursorPosition = text.length();

                // Berechne die Startposition für die Selektion
                int selectionStart = cursorPosition;
                for (int i = 0; i < wordCount; i++) {
                    int wordStart = findWordStart(text, selectionStart);
                    if (wordStart >= 0 && wordStart < selectionStart) {
                        selectionStart = wordStart;
                    } else {
                        break; // Kein weiteres Wort gefunden
                    }
                }

                // Selektiere den Text vom Anfang des ersten Wortes bis zur ursprünglichen Cursorposition
                inputConnection.setSelection(selectionStart, cursorPosition);
            }
        }
    }

    private int findWordStart(String text, int position) {
        // Finde den Anfang des letzten Wortes rückwärts von der Position
        if (position <= 0) return -1;

        // Überspringe Leerzeichen und Sonderzeichen am Ende
        int i = position - 1;
        while (i >= 0 && !Character.isLetterOrDigit(text.charAt(i))) {
            i--;
        }

        // Finde den Anfang des Wortes (Buchstaben und Zahlen)
        while (i >= 0 && Character.isLetterOrDigit(text.charAt(i))) {
            i--;
        }

        return i + 1;
    }

    // checks whether a point is inside a view based on its horizontal position
    private boolean isPointInsideView(float x, View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return x > location[0] && x < location[0] + view.getWidth();
    }

    private void highlightSelectedCharacter(MaterialButton selectedView) {
        for (int i = 0; i < overlayCharactersLl.getChildCount(); i++) {
            MaterialButton charView = (MaterialButton) overlayCharactersLl.getChildAt(i);
            if (charView == selectedView) {
                charView.setStrokeColorResource(R.color.dictate_blue);
                charView.setStrokeWidth(4);
            } else {
                charView.setStrokeWidth(0);
            }
        }
    }

    // Methode zum Prüfen, ob die vorherige Tastatur dieselbe ist wie die aktuelle
    private boolean isPreviousImeSameAsCurrent() {
        try {
            // Erhalte den Namen der aktuellen Tastatur
            String currentImeId = getPackageName() + "/" + getClass().getName();

            // Debugging-Ausgaben für ADB
            Log.d("DictateInputMethodService", "Current IME ID: " + currentImeId);

            // Alternative Methode: Prüfe den letzten Eintrag im InputMethodHistory
            // Dies ist eine einfachere Implementierung, die in den meisten Fällen funktionieren sollte
            // Wir prüfen einfach, ob die aktuelle Tastatur die Standardtastatur ist
            String defaultImeId = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            );

            // Debugging-Ausgabe für Standard-IME
            Log.d("DictateInputMethodService", "Default IME ID: " + defaultImeId);

            // Wenn die aktuelle Tastatur die Standardtastatur ist, gehen wir davon aus,
            // dass der Wechsel zur vorherigen Tastatur ein Wechsel zu derselben Tastatur wäre
            boolean isDefault = currentImeId != null && defaultImeId != null && currentImeId.equals(defaultImeId);
            Log.d("DictateInputMethodService", "Is current IME the default: " + isDefault);

            return isDefault;
        } catch (Exception e) {
            // Debugging-Ausgabe für Fehler
            Log.e("DictateInputMethodService", "Error in isPreviousImeSameAsCurrent: " + e.getMessage());
            e.printStackTrace();

            // Im Fehlerfall geben wir false zurück, um das Standardverhalten zu erhalten
            return false;
        }
    }

    /**
     * Shared method to transcribe an audio file using the configured transcription service
     * This method can be called from anywhere in the app to transcribe audio files
     *
     * @param context The context to use for accessing SharedPreferences and resources
     * @param audioFile The audio file to transcribe
     * @param usageDb The usage database to track transcription usage
     * @return The transcribed text
     * @throws Exception If transcription fails
     */
    public static String transcribeAudioFile(Context context, File audioFile, UsageDatabaseHelper usageDb) throws Exception {
        return transcribeAudioFile(context, audioFile, usageDb, "detect", "");
    }

    /**
     * Shared method to transcribe an audio file using the configured transcription service
     * This method can be called from anywhere in the app to transcribe audio files
     *
     * @param context The context to use for accessing SharedPreferences and resources
     * @param audioFile The audio file to transcribe
     * @param usageDb The usage database to track transcription usage
     * @param language The language to use for transcription (or "detect" for auto-detection)
     * @param stylePrompt The style prompt to use for transcription
     * @return The transcribed text
     * @throws Exception If transcription fails
     */
    public static String transcribeAudioFile(Context context, File audioFile, UsageDatabaseHelper usageDb, String language, String stylePrompt) throws Exception {
        SharedPreferences sp = context.getSharedPreferences("net.devemperor.dictate", Context.MODE_PRIVATE);

        int transcriptionProvider = sp.getInt("net.devemperor.dictate.transcription_provider", 0);
        String apiHost = context.getResources().getStringArray(R.array.dictate_api_providers_values)[transcriptionProvider];
        if (apiHost.equals("custom_server")) {
            apiHost = sp.getString("net.devemperor.dictate.transcription_custom_host", context.getString(R.string.dictate_custom_server_host_hint));
        }

        String apiKey = sp.getString("net.devemperor.dictate.transcription_api_key", sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY")).replaceAll("[^ -~]", "");
        String proxyHost = sp.getString("net.devemperor.dictate.proxy_host", context.getString(R.string.dictate_settings_proxy_hint));

        String transcriptionModel = "";
        switch (transcriptionProvider) {  // for upgrading: use old transcription_model preference
            case 0: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_openai_model", sp.getString("net.devemperor.dictate.transcription_model", "gpt-4o-mini-transcribe")); break;
            case 1: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_groq_model", "whisper-large-v3-turbo"); break;
            case 2: transcriptionModel = sp.getString("net.devemperor.dictate.transcription_custom_model", context.getString(R.string.dictate_custom_transcription_model_hint));
        }

        OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiHost)
                .timeout(Duration.ofSeconds(120));

        TranscriptionCreateParams.Builder transcriptionBuilder = TranscriptionCreateParams.builder()
                .file(audioFile.toPath())
                .model(transcriptionModel)
                .responseFormat(AudioResponseFormat.JSON);  // gpt-4o-transcribe only supports json

        if (language != null && !language.equals("detect")) transcriptionBuilder.language(language);
        if (stylePrompt != null && !stylePrompt.isEmpty()) transcriptionBuilder.prompt(stylePrompt);
        if (sp.getBoolean("net.devemperor.dictate.proxy_enabled", false)) {
            if (DictateUtils.isValidProxy(proxyHost)) DictateUtils.applyProxy(clientBuilder, sp);
        }

        // Logging für die API-Anfrage (ohne API-Key)
        Log.d("DictateAPI", "Transkriptionsanfrage - URL: " + apiHost + ", Modell: " + transcriptionModel);

        Transcription transcription = clientBuilder.build().audio().transcriptions().create(transcriptionBuilder.build()).asTranscription();
        String resultText = transcription.text().strip();  // Groq sometimes adds leading whitespace

        // Logging der Transkription (ohne API-Key)
        Log.d("DictateAPI", "Transkription erhalten: " + resultText);

        usageDb.edit(transcriptionModel, DictateUtils.getAudioDuration(audioFile), 0, 0, transcriptionProvider);

        return resultText;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Register SCO state change receiver
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        registerReceiver(scoStateReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unregister SCO state change receiver
        unregisterReceiver(scoStateReceiver);

        // Stop Bluetooth SCO if it's still running
        if (isBluetoothScoStarted) {
            stopBluetoothSco();
        }
    }

    private final BroadcastReceiver scoStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                // Bluetooth SCO is now connected and can be used
                Log.d("DictateInputMethodService", "Bluetooth SCO connected");
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                Log.d("DictateInputMethodService", "Bluetooth SCO disconnected");
            }
        }
    };

    private boolean isBluetoothScoAvailable() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isBluetoothScoAvailableOffCall();
    }

    private void startBluetoothSco() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.startBluetoothSco();
        audioManager.setBluetoothScoOn(true);
        isBluetoothScoStarted = true;
    }

    private void stopBluetoothSco() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
        isBluetoothScoStarted = false;
    }

}
