package com.example;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.database.AppDatabase;
import com.example.model.Note;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * NoteEditorActivity provides a minimalist yet feature-rich writing environment.
 *
 * Implements:
 * - Real-time Bold toggle: When Bold is toggled ON, all newly typed characters are bold.
 * - Real-time Color toggle: When a Color is active, all newly typed characters are colored.
 * - Interactive Popup Screen for Date Picker with presets & direct insertion.
 * - Important / Star Tagging & Note Export/Sharing.
 */
public class NoteEditorActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE = "extra_note";
    public static final String EXTRA_NOTE_ID = "extra_note_id";

    // UI View references
    private EditText etTitle;
    private EditText etBody;
    private TextView tvDate;
    private TextView tvWordCount;
    private TextView tvEditorTitle;
    private ImageButton btnBack;
    private ImageButton btnDelete;
    private ImageButton btnToggleImportant;
    private ImageButton btnShareNote;
    private MaterialButton btnSave;

    // Formatting Toolbar
    private LinearLayout btnFormatBold;
    private ImageView ivBoldIcon;
    private TextView tvBoldLabel;

    private LinearLayout btnFormatColor;
    private ImageView ivColorIcon;
    private TextView tvColorLabel;

    private LinearLayout btnInsertDate;
    private LinearLayout colorPickerStrip;

    // Target Date Badge
    private LinearLayout layoutTargetDateBadge;
    private TextView tvAttachedDate;
    private ImageView btnRemoveDate;

    // Formatting State
    private boolean isBoldActive = false;
    private String activeColorHex = ""; // empty means default text color
    private boolean isFormattingTextWatcher = false;

    // Last typed range tracked for real-time span styling
    private int lastTypedStart = 0;
    private int lastTypedCount = 0;

    // Database & Note State
    private AppDatabase database;
    private Note existingNote;
    private int noteId = -1;
    private boolean isImportant = false;
    private String noteThemeColorHex = "";
    private String attachedTargetDate = "";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
    private final SimpleDateFormat popupDateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        database = AppDatabase.getInstance(this);

        initViews();
        checkIntentData();
        setupListeners();
        setupFormattingToolbar();
        setupTextWatchers();
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_note_title);
        etBody = findViewById(R.id.et_note_body);
        tvDate = findViewById(R.id.tv_editor_date);
        tvWordCount = findViewById(R.id.tv_word_count);
        tvEditorTitle = findViewById(R.id.tv_editor_title);
        btnBack = findViewById(R.id.btn_back);
        btnDelete = findViewById(R.id.btn_delete_editor_note);
        btnToggleImportant = findViewById(R.id.btn_toggle_important);
        btnShareNote = findViewById(R.id.btn_share_note);
        btnSave = findViewById(R.id.btn_save_note);

        btnFormatBold = findViewById(R.id.btn_format_bold);
        ivBoldIcon = (ImageView) btnFormatBold.getChildAt(0);
        tvBoldLabel = (TextView) btnFormatBold.getChildAt(1);

        btnFormatColor = findViewById(R.id.btn_format_color);
        ivColorIcon = (ImageView) btnFormatColor.getChildAt(0);
        tvColorLabel = (TextView) btnFormatColor.getChildAt(1);

        btnInsertDate = findViewById(R.id.btn_insert_date);
        colorPickerStrip = findViewById(R.id.color_picker_strip);

        layoutTargetDateBadge = findViewById(R.id.layout_target_date_badge);
        tvAttachedDate = findViewById(R.id.tv_attached_date);
        btnRemoveDate = findViewById(R.id.btn_remove_date);
    }

    private void checkIntentData() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_NOTE)) {
            existingNote = (Note) intent.getSerializableExtra(EXTRA_NOTE);
            if (existingNote != null) {
                noteId = existingNote.getId();
                etTitle.setText(existingNote.getTitle());

                // Parse rich HTML or plain text into Spannable
                String content = existingNote.getContent();
                if (content != null && (content.contains("<") && content.contains(">"))) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        etBody.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT));
                    } else {
                        etBody.setText(Html.fromHtml(content));
                    }
                } else {
                    etBody.setText(content != null ? content : "");
                }

                isImportant = existingNote.isImportant();
                noteThemeColorHex = existingNote.getColorHex() != null ? existingNote.getColorHex() : "";
                attachedTargetDate = existingNote.getTargetDate() != null ? existingNote.getTargetDate() : "";

                tvEditorTitle.setText("EDIT NOTE");
                btnDelete.setVisibility(View.VISIBLE);

                if (existingNote.getTimestamp() > 0) {
                    tvDate.setText("Edited: " + dateFormat.format(new Date(existingNote.getTimestamp())));
                }
                updateImportantUI();
                updateDateBadgeUI();
                updateCounters();
                return;
            }
        }

        // New Note Mode
        tvEditorTitle.setText("NEW NOTE");
        btnDelete.setVisibility(View.GONE);
        tvDate.setText("Created: " + dateFormat.format(new Date()));
        updateImportantUI();
        updateDateBadgeUI();
        updateCounters();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> saveNoteAndFinish());
        btnSave.setOnClickListener(v -> saveNoteAndFinish());
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());

        // Toggle Important Tag
        btnToggleImportant.setOnClickListener(v -> {
            isImportant = !isImportant;
            updateImportantUI();
            String msg = isImportant ? "Marked as Important ⭐" : "Removed from Important";
            Toast.makeText(NoteEditorActivity.this, msg, Toast.LENGTH_SHORT).show();
        });

        // Share / Export Note
        btnShareNote.setOnClickListener(v -> shareCurrentNote());

        // Remove Attached Date
        btnRemoveDate.setOnClickListener(v -> {
            attachedTargetDate = "";
            updateDateBadgeUI();
            Toast.makeText(NoteEditorActivity.this, "Date removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateImportantUI() {
        if (isImportant) {
            btnToggleImportant.setImageResource(R.drawable.ic_star_filled);
            btnToggleImportant.setColorFilter(ContextCompat.getColor(this, R.color.zen_accent));
        } else {
            btnToggleImportant.setImageResource(R.drawable.ic_star_outline);
            btnToggleImportant.setColorFilter(ContextCompat.getColor(this, R.color.zen_text_secondary));
        }
    }

    private void updateDateBadgeUI() {
        if (!TextUtils.isEmpty(attachedTargetDate)) {
            layoutTargetDateBadge.setVisibility(View.VISIBLE);
            tvAttachedDate.setText("📅 " + attachedTargetDate);
        } else {
            layoutTargetDateBadge.setVisibility(View.GONE);
        }
    }

    private void setupFormattingToolbar() {
        // Toggle Bold Mode
        btnFormatBold.setOnClickListener(v -> toggleBoldMode());

        // Toggle Color Bar
        btnFormatColor.setOnClickListener(v -> {
            if (colorPickerStrip.getVisibility() == View.VISIBLE) {
                colorPickerStrip.setVisibility(View.GONE);
            } else {
                colorPickerStrip.setVisibility(View.VISIBLE);
            }
        });

        // Date Picker Tool (Popup Screen)
        btnInsertDate.setOnClickListener(v -> showPopupDatePickerDialog());

        // Setup Color Chips
        setupColorChip(R.id.color_mint, "#B9F6CA");
        setupColorChip(R.id.color_gold, "#FFE082");
        setupColorChip(R.id.color_coral, "#FF8A80");
        setupColorChip(R.id.color_blue, "#80D8FF");
        setupColorChip(R.id.color_lavender, "#EA80FC");
        setupColorChip(R.id.color_white, "#F5F5F5");
    }

    private void setupColorChip(int viewId, String hexColor) {
        View chip = findViewById(viewId);
        if (chip != null) {
            chip.setOnClickListener(v -> selectTextColor(hexColor));
        }
    }

    /**
     * Toggles Bold state for newly typed text, and applies/removes bold on any active selection.
     */
    private void toggleBoldMode() {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();
        Editable editable = etBody.getText();

        if (start >= 0 && end >= 0 && start != end) {
            // Apply or toggle bold to selected text
            int selStart = Math.min(start, end);
            int selEnd = Math.max(start, end);

            StyleSpan[] spans = editable.getSpans(selStart, selEnd, StyleSpan.class);
            boolean alreadyBold = false;
            for (StyleSpan span : spans) {
                if (span.getStyle() == Typeface.BOLD) {
                    editable.removeSpan(span);
                    alreadyBold = true;
                }
            }

            if (!alreadyBold) {
                editable.setSpan(new StyleSpan(Typeface.BOLD), selStart, selEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                isBoldActive = true;
            } else {
                isBoldActive = false;
            }
        } else {
            // Toggle typing state
            isBoldActive = !isBoldActive;
        }

        updateBoldButtonUI();
        String status = isBoldActive ? "Bold ON (typing is bold)" : "Bold OFF";
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    private void updateBoldButtonUI() {
        if (isBoldActive) {
            btnFormatBold.setBackgroundResource(R.drawable.bg_format_btn_active);
            ivBoldIcon.setColorFilter(ContextCompat.getColor(this, R.color.zen_accent));
            tvBoldLabel.setTextColor(ContextCompat.getColor(this, R.color.zen_accent));
        } else {
            btnFormatBold.setBackgroundResource(R.drawable.bg_format_btn);
            ivBoldIcon.setColorFilter(ContextCompat.getColor(this, R.color.zen_text_primary));
            tvBoldLabel.setTextColor(ContextCompat.getColor(this, R.color.zen_text_primary));
        }
    }

    /**
     * Selects text color for typing and applies it to active selection.
     */
    private void selectTextColor(String hexColor) {
        if (hexColor.equalsIgnoreCase(activeColorHex)) {
            // If tapped again, reset to default text color
            activeColorHex = "";
            updateColorButtonUI();
            Toast.makeText(this, "Color reset to default", Toast.LENGTH_SHORT).show();
            colorPickerStrip.setVisibility(View.GONE);
            return;
        }

        activeColorHex = hexColor;
        noteThemeColorHex = hexColor;
        int color = Color.parseColor(hexColor);

        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();
        Editable editable = etBody.getText();

        if (start >= 0 && end >= 0 && start != end) {
            int selStart = Math.min(start, end);
            int selEnd = Math.max(start, end);

            // Remove existing color spans on selection
            ForegroundColorSpan[] spans = editable.getSpans(selStart, selEnd, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                editable.removeSpan(span);
            }

            editable.setSpan(new ForegroundColorSpan(color), selStart, selEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
            Toast.makeText(this, "Selected text colored", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Color ON (typing in this color)", Toast.LENGTH_SHORT).show();
        }

        updateColorButtonUI();
        colorPickerStrip.setVisibility(View.GONE);
    }

    private void updateColorButtonUI() {
        if (!TextUtils.isEmpty(activeColorHex)) {
            btnFormatColor.setBackgroundResource(R.drawable.bg_format_btn_active);
            int parsedColor = Color.parseColor(activeColorHex);
            ivColorIcon.setColorFilter(parsedColor);
            tvColorLabel.setTextColor(parsedColor);
        } else {
            btnFormatColor.setBackgroundResource(R.drawable.bg_format_btn);
            ivColorIcon.setColorFilter(ContextCompat.getColor(this, R.color.zen_accent));
            tvColorLabel.setTextColor(ContextCompat.getColor(this, R.color.zen_text_primary));
        }
    }

    /**
     * Displays a full pop-up screen modal dialog for Date Picker.
     */
    private void showPopupDatePickerDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_popup_date_picker);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        DatePicker datePicker = dialog.findViewById(R.id.popup_date_picker);
        TextView tvPreview = dialog.findViewById(R.id.tv_popup_selected_preview);
        ImageView btnClose = dialog.findViewById(R.id.btn_close_date_popup);

        TextView presetToday = dialog.findViewById(R.id.preset_today);
        TextView presetTomorrow = dialog.findViewById(R.id.preset_tomorrow);
        TextView presetIn3Days = dialog.findViewById(R.id.preset_in_3_days);
        TextView presetNextWeek = dialog.findViewById(R.id.preset_next_week);

        TextView optInsertText = dialog.findViewById(R.id.opt_insert_text);
        TextView optAttachBadge = dialog.findViewById(R.id.opt_attach_badge);
        TextView optBoth = dialog.findViewById(R.id.opt_both);

        MaterialButton btnCancel = dialog.findViewById(R.id.btn_popup_cancel);
        MaterialButton btnApply = dialog.findViewById(R.id.btn_popup_apply);

        final Calendar selectedCal = Calendar.getInstance();
        final int[] actionChoice = {0}; // 0: In Text, 1: As Badge, 2: Both

        Runnable updatePreview = () -> {
            tvPreview.setText(popupDateFormat.format(selectedCal.getTime()));
        };
        updatePreview.run();

        // DatePicker changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            datePicker.setOnDateChangedListener((view, year, monthOfYear, dayOfMonth) -> {
                selectedCal.set(year, monthOfYear, dayOfMonth);
                updatePreview.run();
            });
        }

        // Preset Chips
        TextView[] presets = {presetToday, presetTomorrow, presetIn3Days, presetNextWeek};
        java.util.function.Consumer<TextView> selectPreset = (selected) -> {
            for (TextView p : presets) {
                if (p == selected) {
                    p.setBackgroundResource(R.drawable.bg_chip_selected);
                    p.setTextColor(ContextCompat.getColor(this, R.color.zen_accent));
                } else {
                    p.setBackgroundResource(R.drawable.bg_chip_unselected);
                    p.setTextColor(ContextCompat.getColor(this, R.color.zen_text_secondary));
                }
            }
        };

        presetToday.setOnClickListener(v -> {
            selectPreset.accept(presetToday);
            selectedCal.setTimeInMillis(System.currentTimeMillis());
            datePicker.updateDate(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH));
            updatePreview.run();
        });

        presetTomorrow.setOnClickListener(v -> {
            selectPreset.accept(presetTomorrow);
            selectedCal.setTimeInMillis(System.currentTimeMillis() + (24L * 60 * 60 * 1000));
            datePicker.updateDate(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH));
            updatePreview.run();
        });

        presetIn3Days.setOnClickListener(v -> {
            selectPreset.accept(presetIn3Days);
            selectedCal.setTimeInMillis(System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000));
            datePicker.updateDate(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH));
            updatePreview.run();
        });

        presetNextWeek.setOnClickListener(v -> {
            selectPreset.accept(presetNextWeek);
            selectedCal.setTimeInMillis(System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000));
            datePicker.updateDate(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH));
            updatePreview.run();
        });

        // Action Options
        TextView[] actionViews = {optInsertText, optAttachBadge, optBoth};
        java.util.function.Consumer<Integer> selectAction = (choice) -> {
            actionChoice[0] = choice;
            for (int i = 0; i < actionViews.length; i++) {
                if (i == choice) {
                    actionViews[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    actionViews[i].setTextColor(ContextCompat.getColor(this, R.color.zen_accent));
                } else {
                    actionViews[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                    actionViews[i].setTextColor(ContextCompat.getColor(this, R.color.zen_text_secondary));
                }
            }
        };

        optInsertText.setOnClickListener(v -> selectAction.accept(0));
        optAttachBadge.setOnClickListener(v -> selectAction.accept(1));
        optBoth.setOnClickListener(v -> selectAction.accept(2));

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnApply.setOnClickListener(v -> {
            selectedCal.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
            String formattedDate = popupDateFormat.format(selectedCal.getTime());
            String badgeDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(selectedCal.getTime());

            int choice = actionChoice[0];
            if (choice == 0 || choice == 2) {
                // Insert in text
                int cursor = Math.max(etBody.getSelectionStart(), 0);
                String insertStr = "\n📅 " + formattedDate + "\n";
                etBody.getText().insert(cursor, insertStr);
            }

            if (choice == 1 || choice == 2) {
                // Attach as badge
                attachedTargetDate = badgeDate;
                updateDateBadgeUI();
            }

            dialog.dismiss();
            Toast.makeText(this, "Date added 📅", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void shareCurrentNote() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) {
            Toast.makeText(this, "Note is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            shareBuilder.append(title).append("\n\n");
        }
        if (!TextUtils.isEmpty(attachedTargetDate)) {
            shareBuilder.append("📅 Date: ").append(attachedTargetDate).append("\n\n");
        }
        shareBuilder.append(body);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, !TextUtils.isEmpty(title) ? title : "ZEN Note");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBuilder.toString());

        startActivity(Intent.createChooser(shareIntent, "Share or Save Note to..."));
    }

    private void setupTextWatchers() {
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateCounters();
            }
        });

        etBody.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastTypedStart = start;
                lastTypedCount = count;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormattingTextWatcher) return;

                // Real-time styling of newly typed characters based on active Bold / Color states
                if (lastTypedCount > 0) {
                    isFormattingTextWatcher = true;
                    int start = lastTypedStart;
                    int end = Math.min(lastTypedStart + lastTypedCount, s.length());

                    if (start < end) {
                        if (isBoldActive) {
                            s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                        }

                        if (!TextUtils.isEmpty(activeColorHex)) {
                            int color = Color.parseColor(activeColorHex);
                            s.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                        }
                    }
                    isFormattingTextWatcher = false;
                }

                updateCounters();
            }
        });
    }

    private void updateCounters() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();
        String combined = title + " " + body;

        int charCount = title.length() + body.length();
        int wordCount = 0;

        if (!TextUtils.isEmpty(combined.trim())) {
            String[] words = combined.trim().split("\\s+");
            wordCount = words.length;
        }

        tvWordCount.setText(wordCount + " words • " + charCount + " chars");
    }

    private void saveNoteAndFinish() {
        String title = etTitle.getText().toString().trim();
        Editable bodyEditable = etBody.getText();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(bodyEditable.toString().trim())) {
            finish();
            return;
        }

        // Convert Spannable to HTML if spans exist, else plain string
        String contentToSave;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            contentToSave = Html.toHtml(bodyEditable, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        } else {
            contentToSave = Html.toHtml(bodyEditable);
        }

        long currentTimestamp = System.currentTimeMillis();

        if (existingNote == null) {
            Note newNote = new Note(title, contentToSave, currentTimestamp, isImportant, noteThemeColorHex, attachedTargetDate);
            AppDatabase.databaseWriteExecutor.execute(() -> database.noteDao().insert(newNote));
        } else {
            existingNote.setTitle(title);
            existingNote.setContent(contentToSave);
            existingNote.setTimestamp(currentTimestamp);
            existingNote.setImportant(isImportant);
            existingNote.setColorHex(noteThemeColorHex);
            existingNote.setTargetDate(attachedTargetDate);
            AppDatabase.databaseWriteExecutor.execute(() -> database.noteDao().update(existingNote));
        }

        finish();
    }

    private void showDeleteConfirmationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_delete_popup_title);
        TextView tvMsg = dialog.findViewById(R.id.tv_delete_popup_message);
        TextView tvPreview = dialog.findViewById(R.id.tv_delete_item_preview);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_popup_delete_cancel);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btn_popup_delete_confirm);

        tvTitle.setText("DELETE NOTE");
        tvMsg.setText("Are you sure you want to permanently delete this note? This action cannot be undone.");

        String title = etTitle.getText().toString().trim();
        if (!TextUtils.isEmpty(title)) {
            tvPreview.setVisibility(View.VISIBLE);
            tvPreview.setText(title);
        } else {
            tvPreview.setVisibility(View.GONE);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (existingNote != null) {
                AppDatabase.databaseWriteExecutor.execute(() -> database.noteDao().delete(existingNote));
                Toast.makeText(NoteEditorActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        saveNoteAndFinish();
    }
}
