package com.example.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.BackupActivity;
import com.example.LogActivity;
import com.example.MainActivity;
import com.example.R;
import com.example.util.ThemeHelper;

import java.util.Locale;

/**
 * SettingsFragment: Allows customizing accent theme colors, navigating to Activity Logs,
 * and opening Backup & Restore Hub.
 */
public class SettingsFragment extends Fragment {

    private FrameLayout frameMint, frameTeal, frameCyan, frameSage, frameLime;
    private View ringMint, ringTeal, ringCyan, ringSage, ringLime;
    private ImageView checkMint, checkTeal, checkCyan, checkSage, checkLime;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupThemeSelector();
        setupNavigation(view);
    }

    private void initViews(View root) {
        frameMint = root.findViewById(R.id.frame_color_mint);
        frameTeal = root.findViewById(R.id.frame_color_teal);
        frameCyan = root.findViewById(R.id.frame_color_cyan);
        frameSage = root.findViewById(R.id.frame_color_sage);
        frameLime = root.findViewById(R.id.frame_color_lime);

        ringMint = root.findViewById(R.id.ring_color_mint);
        ringTeal = root.findViewById(R.id.ring_color_teal);
        ringCyan = root.findViewById(R.id.ring_color_cyan);
        ringSage = root.findViewById(R.id.ring_color_sage);
        ringLime = root.findViewById(R.id.ring_color_lime);

        checkMint = root.findViewById(R.id.check_color_mint);
        checkTeal = root.findViewById(R.id.check_color_teal);
        checkCyan = root.findViewById(R.id.check_color_cyan);
        checkSage = root.findViewById(R.id.check_color_sage);
        checkLime = root.findViewById(R.id.check_color_lime);
    }

    private void setupThemeSelector() {
        if (!isAdded()) return;
        updateThemeSelectionIndicators(ThemeHelper.getAccentColorHex(requireContext()));

        if (frameMint != null) {
            frameMint.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_MINT_DEFAULT));
        }
        if (frameTeal != null) {
            frameTeal.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_NEON_TEAL));
        }
        if (frameCyan != null) {
            frameCyan.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_CYAN_BLUE));
        }
        if (frameSage != null) {
            frameSage.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_SAGE_GREEN));
        }
        if (frameLime != null) {
            frameLime.setOnClickListener(v -> selectThemeColor(ThemeHelper.COLOR_ELECTRIC_LIME));
        }
    }

    private void selectThemeColor(String hexColor) {
        if (!isAdded()) return;
        ThemeHelper.setAccentColor(requireContext(), hexColor);
        int newColor = ThemeHelper.getAccentColor(requireContext());
        updateThemeSelectionIndicators(hexColor);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).applyThemeAccent(newColor);
        }
        Toast.makeText(requireContext(), "Theme accent updated", Toast.LENGTH_SHORT).show();
    }

    private void updateThemeSelectionIndicators(String selectedHex) {
        String hex = (selectedHex != null) ? selectedHex.toLowerCase(Locale.ROOT) : ThemeHelper.COLOR_MINT_DEFAULT;
        boolean isMint = hex.equalsIgnoreCase(ThemeHelper.COLOR_MINT_DEFAULT);
        boolean isTeal = hex.equalsIgnoreCase(ThemeHelper.COLOR_NEON_TEAL);
        boolean isCyan = hex.equalsIgnoreCase(ThemeHelper.COLOR_CYAN_BLUE);
        boolean isSage = hex.equalsIgnoreCase(ThemeHelper.COLOR_SAGE_GREEN);
        boolean isLime = hex.equalsIgnoreCase(ThemeHelper.COLOR_ELECTRIC_LIME);

        if (ringMint != null) ringMint.setVisibility(isMint ? View.VISIBLE : View.GONE);
        if (checkMint != null) checkMint.setVisibility(isMint ? View.VISIBLE : View.GONE);

        if (ringTeal != null) ringTeal.setVisibility(isTeal ? View.VISIBLE : View.GONE);
        if (checkTeal != null) checkTeal.setVisibility(isTeal ? View.VISIBLE : View.GONE);

        if (ringCyan != null) ringCyan.setVisibility(isCyan ? View.VISIBLE : View.GONE);
        if (checkCyan != null) checkCyan.setVisibility(isCyan ? View.VISIBLE : View.GONE);

        if (ringSage != null) ringSage.setVisibility(isSage ? View.VISIBLE : View.GONE);
        if (checkSage != null) checkSage.setVisibility(isSage ? View.VISIBLE : View.GONE);

        if (ringLime != null) ringLime.setVisibility(isLime ? View.VISIBLE : View.GONE);
        if (checkLime != null) checkLime.setVisibility(isLime ? View.VISIBLE : View.GONE);
    }

    private void setupNavigation(View root) {
        View cardActivityLog = root.findViewById(R.id.card_settings_activity_log);
        if (cardActivityLog != null) {
            cardActivityLog.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), LogActivity.class);
                startActivity(intent);
            });
        }

        View cardFullBackup = root.findViewById(R.id.card_settings_full_backup);
        if (cardFullBackup != null) {
            cardFullBackup.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), BackupActivity.class);
                startActivity(intent);
            });
        }
    }
}
