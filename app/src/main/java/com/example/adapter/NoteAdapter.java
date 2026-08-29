package com.example.adapter;

import android.graphics.Color;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Note;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NoteAdapter is the RecyclerView adapter that binds Note objects to item views in the notes list.
 *
 * Supports rendering:
 * - Important badge indicators
 * - Target date pills
 * - Rich text content preview stripping
 * - Custom accent colors
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<Note> notes = new ArrayList<>();
    private final OnNoteClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onNoteDelete(Note note);
    }

    public NoteAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void setNotes(final List<Note> newNotes) {
        if (newNotes == null) {
            this.notes.clear();
            notifyDataSetChanged();
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return notes.size();
            }

            @Override
            public int getNewListSize() {
                return newNotes.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return notes.get(oldItemPosition).getId() == newNotes.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Note oldItem = notes.get(oldItemPosition);
                Note newItem = newNotes.get(newItemPosition);
                return TextUtils.equals(oldItem.getTitle(), newItem.getTitle()) &&
                        TextUtils.equals(oldItem.getContent(), newItem.getContent()) &&
                        oldItem.getTimestamp() == newItem.getTimestamp() &&
                        oldItem.isImportant() == newItem.isImportant() &&
                        TextUtils.equals(oldItem.getColorHex(), newItem.getColorHex()) &&
                        TextUtils.equals(oldItem.getTargetDate(), newItem.getTargetDate());
            }
        });

        this.notes = new ArrayList<>(newNotes);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note currentNote = notes.get(position);
        holder.bind(currentNote, listener, dateFormat);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardNote;
        private final TextView tvTitle;
        private final TextView tvPreview;
        private final TextView tvDate;
        private final ImageButton btnDelete;

        private final LinearLayout layoutBadgesRow;
        private final LinearLayout badgeImportant;
        private final LinearLayout badgeDate;
        private final TextView tvBadgeDateText;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardNote = itemView.findViewById(R.id.card_note);
            tvTitle = itemView.findViewById(R.id.tv_note_title);
            tvPreview = itemView.findViewById(R.id.tv_note_preview);
            tvDate = itemView.findViewById(R.id.tv_note_date);
            btnDelete = itemView.findViewById(R.id.btn_delete_note);

            layoutBadgesRow = itemView.findViewById(R.id.layout_badges_row);
            badgeImportant = itemView.findViewById(R.id.badge_important);
            badgeDate = itemView.findViewById(R.id.badge_date);
            tvBadgeDateText = itemView.findViewById(R.id.tv_badge_date_text);
        }

        public void bind(final Note note, final OnNoteClickListener listener, SimpleDateFormat dateFormat) {
            // Badges handling
            boolean showBadges = false;

            if (note.isImportant()) {
                badgeImportant.setVisibility(View.VISIBLE);
                showBadges = true;
            } else {
                badgeImportant.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(note.getTargetDate())) {
                badgeDate.setVisibility(View.VISIBLE);
                tvBadgeDateText.setText(note.getTargetDate());
                showBadges = true;
            } else {
                badgeDate.setVisibility(View.GONE);
            }

            layoutBadgesRow.setVisibility(showBadges ? View.VISIBLE : View.GONE);

            // Card custom color border or default
            if (!TextUtils.isEmpty(note.getColorHex())) {
                try {
                    int color = Color.parseColor(note.getColorHex());
                    cardNote.setStrokeColor(color);
                } catch (Exception e) {
                    cardNote.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.zen_border));
                }
            } else {
                cardNote.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.zen_border));
            }

            // Title
            if (!TextUtils.isEmpty(note.getTitle())) {
                tvTitle.setText(note.getTitle());
                tvTitle.setVisibility(View.VISIBLE);
            } else {
                tvTitle.setText("Untitled");
                tvTitle.setVisibility(View.VISIBLE);
            }

            // Content preview (stripping raw HTML tags for clean display)
            if (!TextUtils.isEmpty(note.getContent())) {
                String content = note.getContent();
                CharSequence cleanText;
                if (content.contains("<") && content.contains(">")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        cleanText = Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT);
                    } else {
                        cleanText = Html.fromHtml(content);
                    }
                } else {
                    cleanText = content;
                }
                tvPreview.setText(cleanText.toString().trim());
                tvPreview.setVisibility(View.VISIBLE);
            } else {
                tvPreview.setVisibility(View.GONE);
            }

            // Timestamp
            if (note.getTimestamp() > 0) {
                tvDate.setText(dateFormat.format(new Date(note.getTimestamp())));
                tvDate.setVisibility(View.VISIBLE);
            } else {
                tvDate.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteClick(note);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteDelete(note);
                }
            });
        }
    }
}
