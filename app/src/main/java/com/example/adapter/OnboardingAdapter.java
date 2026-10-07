package com.example.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.util.ThemeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * OnboardingAdapter manages the 3-step minimalist onboarding pages in ViewPager2.
 */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

    public interface OnboardingActionListener {
        void onStartFreshClicked();
        void onImportBackupClicked();
    }

    public static class SlideItem {
        public final int iconResId;
        public final int titleResId;
        public final int descResId;
        public final boolean isFinalSlide;

        public SlideItem(int iconResId, int titleResId, int descResId, boolean isFinalSlide) {
            this.iconResId = iconResId;
            this.titleResId = titleResId;
            this.descResId = descResId;
            this.isFinalSlide = isFinalSlide;
        }
    }

    private final List<SlideItem> slides;
    private final OnboardingActionListener actionListener;

    public OnboardingAdapter(List<SlideItem> slides, OnboardingActionListener actionListener) {
        this.slides = slides;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        SlideItem item = slides.get(position);
        holder.bind(item, actionListener);
    }

    @Override
    public int getItemCount() {
        return slides != null ? slides.size() : 0;
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout slideIconContainer;
        private final ImageView ivSlideIcon;
        private final TextView tvSlideTitle;
        private final TextView tvSlideDescription;
        private final LinearLayout layoutFinalActions;
        private final MaterialButton btnStartFresh;
        private final MaterialButton btnImportBackup;

        SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            slideIconContainer = itemView.findViewById(R.id.slide_icon_container);
            ivSlideIcon = itemView.findViewById(R.id.iv_slide_icon);
            tvSlideTitle = itemView.findViewById(R.id.tv_slide_title);
            tvSlideDescription = itemView.findViewById(R.id.tv_slide_description);
            layoutFinalActions = itemView.findViewById(R.id.layout_final_actions);
            btnStartFresh = itemView.findViewById(R.id.btn_start_fresh);
            btnImportBackup = itemView.findViewById(R.id.btn_import_backup);
        }

        void bind(SlideItem item, OnboardingActionListener listener) {
            Context context = itemView.getContext();
            ivSlideIcon.setImageResource(item.iconResId);
            tvSlideTitle.setText(item.titleResId);
            tvSlideDescription.setText(item.descResId);

            int accentColor = ThemeHelper.getAccentColor(context);

            if (item.isFinalSlide) {
                layoutFinalActions.setVisibility(View.VISIBLE);

                // Apply active theme accent to the final slide buttons
                ThemeHelper.applyAccentToPrimaryButton(btnStartFresh, accentColor);
                if (btnImportBackup != null) {
                    btnImportBackup.setIconTint(ColorStateList.valueOf(accentColor));
                }

                if (btnStartFresh != null && listener != null) {
                    btnStartFresh.setOnClickListener(v -> listener.onStartFreshClicked());
                }
                if (btnImportBackup != null && listener != null) {
                    btnImportBackup.setOnClickListener(v -> listener.onImportBackupClicked());
                }
            } else {
                layoutFinalActions.setVisibility(View.GONE);
            }
        }
    }
}
