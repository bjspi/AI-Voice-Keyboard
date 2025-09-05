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
    private PromptModel temporaryAlwaysUsePrompt = null;

    public interface AdapterCallback {
        void onItemClicked(Integer position);
    }
    
    public interface AdapterLongPressCallback {
        void onItemLongPressed(Integer position);
    }

    public PromptsKeyboardAdapter(List<PromptModel> data, AdapterCallback callback, AdapterLongPressCallback longPressCallback) {
        this.data = data;
        this.callback = callback;
        this.longPressCallback = longPressCallback;
    }
    
    public void setTemporaryAlwaysUsePrompt(PromptModel prompt) {
        this.temporaryAlwaysUsePrompt = prompt;
        notifyDataSetChanged();
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
        holder.promptBtn.setOnClickListener(v -> callback.onItemClicked(position));
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
