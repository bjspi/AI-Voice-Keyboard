package net.devemperor.dictate.rewording;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import net.devemperor.dictate.R;

import java.util.List;

public class PromptsKeyboardAdapter extends RecyclerView.Adapter<PromptsKeyboardAdapter.RecyclerViewHolder> {

    private final List<PromptModel> data;
    private final AdapterCallback callback;
    private final AdapterLongPressCallback longPressCallback;
    private final AdapterDoubleClickCallback doubleClickCallback;
    private PromptModel temporaryAlwaysUsePrompt = null;
    private boolean isRecording = false;
    
    // Variables for double-click detection
    private static final long DOUBLE_CLICK_TIME_DELTA = 250; // milliseconds
    private long lastClickTime = 0;
    private int lastClickPosition = -1;
    private final Handler clickHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSingleClickRunnable = null;

    public interface AdapterCallback {
        void onItemClicked(Integer position);
    }
    
    public interface AdapterLongPressCallback {
        void onItemLongPressed(Integer position);
    }
    
    public interface AdapterDoubleClickCallback {
        void onItemDoubleClicked(Integer position);
    }

    public PromptsKeyboardAdapter(List<PromptModel> data, AdapterCallback callback, AdapterLongPressCallback longPressCallback, AdapterDoubleClickCallback doubleClickCallback) {
        this.data = data;
        this.callback = callback;
        this.longPressCallback = longPressCallback;
        this.doubleClickCallback = doubleClickCallback;
    }
    
    public void setTemporaryAlwaysUsePrompt(PromptModel prompt) {
        this.temporaryAlwaysUsePrompt = prompt;
        notifyDataSetChanged();
    }
    
    public void setIsRecording(boolean isRecording) {
        this.isRecording = isRecording;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompts_keyboard, parent, false);
        return new RecyclerViewHolder(view);
    }

    public static class RecyclerViewHolder extends RecyclerView.ViewHolder {
        final MaterialButton promptBtn;

        public RecyclerViewHolder(View itemView) {
            super(itemView);
            promptBtn = itemView.findViewById(R.id.prompts_keyboard_btn);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, final int position) {
        PromptModel model = data.get(position);

        // Default Button Design:
        holder.promptBtn.setBackgroundColor(holder.promptBtn.getContext().getResources().getColor(R.color.dictate_keyboard_button, holder.promptBtn.getContext().getTheme()));

        if (model.getId() == -1) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_auto_awesome_18));
        } else if (model.getId() == -2) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_add_24));
        } else {
            holder.promptBtn.setText(model.getName());
            holder.promptBtn.setForeground(null);
            
            // Highlight the prompt if it has the alwaysUse flag set or is the temporary alwaysUse prompt
            if (temporaryAlwaysUsePrompt != null && model.getId() == temporaryAlwaysUsePrompt.getId()) {
                // Temporärer alwaysUse Prompt - blaue Hervorhebung
                holder.promptBtn.setBackgroundColor(holder.promptBtn.getContext().getResources().getColor(R.color.dictate_temporary_always_use_highlight, holder.promptBtn.getContext().getTheme()));
            } else if (model.isAlwaysUse()) {
                // Permanenter alwaysUse Prompt - goldene Hervorhebung
                holder.promptBtn.setBackgroundColor(holder.promptBtn.getContext().getResources().getColor(R.color.dictate_always_use_highlight, holder.promptBtn.getContext().getTheme()));
            }
        }
        holder.promptBtn.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            int clickPosition = holder.getAdapterPosition();
            
            // Check if the position is valid
            if (clickPosition == RecyclerView.NO_POSITION) {
                return;
            }
            
            // Wenn wir uns in einer Aufnahme befinden und es sich um einen echten Prompt handelt (nicht Instant-Prompt -1),
            // dann aktiviere/deaktiviere den Prompt als temporären alwaysUse Prompt (wie beim Long Press)
            if (isRecording && data.get(clickPosition).getId() != -1 && data.get(clickPosition).getId() != -2) {
                // Verwende die Long Press Logik für normale Klicks während der Aufnahme
                if (longPressCallback != null) {
                    // Bei Long-Press-Äquivalent: vorherige pending Single-Click abbrechen
                    if (pendingSingleClickRunnable != null) {
                        clickHandler.removeCallbacks(pendingSingleClickRunnable);
                        pendingSingleClickRunnable = null;
                        lastClickTime = 0;
                        lastClickPosition = -1;
                    }
                    longPressCallback.onItemLongPressed(clickPosition);
                }
                return;
            }

            // Double-click detection with delayed single-click execution
            if (lastClickPosition == clickPosition && (clickTime - lastClickTime) < DOUBLE_CLICK_TIME_DELTA) {
                // Double click detected -> cancel pending single click and fire double-click immediately
                if (pendingSingleClickRunnable != null) {
                    clickHandler.removeCallbacks(pendingSingleClickRunnable);
                    pendingSingleClickRunnable = null;
                }
                if (doubleClickCallback != null) {
                    doubleClickCallback.onItemDoubleClicked(clickPosition);
                }
                // reset
                lastClickTime = 0;
                lastClickPosition = -1;
            } else {
                // schedule single click after the delta to wait for a possible double click
                lastClickTime = clickTime;
                lastClickPosition = clickPosition;
                final int pos = clickPosition;
                // cancel previous pending if any (safe-guard)
                if (pendingSingleClickRunnable != null) {
                    clickHandler.removeCallbacks(pendingSingleClickRunnable);
                    pendingSingleClickRunnable = null;
                }
                pendingSingleClickRunnable = () -> {
                    // Execute single click if not canceled by a subsequent double-click/long-press
                    if (callback != null) {
                        callback.onItemClicked(pos);
                    }
                    pendingSingleClickRunnable = null;
                    lastClickTime = 0;
                    lastClickPosition = -1;
                };
                clickHandler.postDelayed(pendingSingleClickRunnable, DOUBLE_CLICK_TIME_DELTA);
            }
        });
        holder.promptBtn.setOnLongClickListener(v -> {
            // Wenn Long-Press passiert, verhindere, dass danach noch ein Single-Click ausgeführt wird
            if (pendingSingleClickRunnable != null) {
                clickHandler.removeCallbacks(pendingSingleClickRunnable);
                pendingSingleClickRunnable = null;
                lastClickTime = 0;
                lastClickPosition = -1;
            }
            longPressCallback.onItemLongPressed(position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public List<PromptModel> getData() {
        return data;
    }
    
    public void clearTemporaryAlwaysUsePrompt() {
        this.temporaryAlwaysUsePrompt = null;
        notifyDataSetChanged();
    }
}
