package net.devemperor.dictate.rewording;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds
    private long lastClickTime = 0;
    private int lastClickPosition = -1;

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
            // Double-click detection
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
                    longPressCallback.onItemLongPressed(clickPosition);
                }
            } else {
                // Normale Verarbeitung
                if (clickPosition == lastClickPosition && (clickTime - lastClickTime) < DOUBLE_CLICK_TIME_DELTA) {
                    // Double click detected
                    if (doubleClickCallback != null) {
                        doubleClickCallback.onItemDoubleClicked(clickPosition);
                    }
                    lastClickTime = 0; // Reset to avoid triple-click being treated as another double-click
                } else {
                    // Single click
                    callback.onItemClicked(clickPosition);
                    lastClickTime = clickTime;
                    lastClickPosition = clickPosition;
                }
            }
        });
        holder.promptBtn.setOnLongClickListener(v -> {
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
