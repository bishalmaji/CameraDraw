package com.bishal.fingerdraw;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class ColorPickerDialogFragment extends DialogFragment {

    public interface ColorPickerListener {
        void onColorSelected(int color);
    }

    private ColorPickerListener listener;

    // List of 15 colors (excluding dark colors)
    private final int[] colors = {
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
            Color.MAGENTA, Color.LTGRAY, Color.GRAY, Color.WHITE,
            Color.parseColor("#FFC107"), Color.parseColor("#FF5722"),
            Color.parseColor("#4CAF50"), Color.parseColor("#03A9F4"),
            Color.parseColor("#FF9800"), Color.parseColor("#E91E63")
    };

    public ColorPickerDialogFragment(ColorPickerListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.fragment_color_picker_dialog);
        setupColorViews(dialog);
        return dialog;
    }

    private void setupColorViews(Dialog dialog) {
        LinearLayout colorLayout = dialog.findViewById(R.id.color_layout);
        if (colorLayout != null) {
            colorLayout.setOrientation(LinearLayout.HORIZONTAL);
            colorLayout.setPadding(8, 8, 8, 8); // Optional padding around the whole layout
            colorLayout.setGravity(Gravity.CENTER_VERTICAL);

            for (int color : colors) {
                TextView colorView = new TextView(requireContext());

                // Convert dp to pixels
                int size = dpToPx(40); // 40dp

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        size, // Width in pixels
                        size  // Height in pixels
                );
                params.setMargins(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5)); // Margins in pixels
                colorView.setLayoutParams(params);

                colorView.setBackgroundColor(color);
                colorView.setClickable(true); // Ensures it can be clicked
                colorView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onColorSelected(color);
                        dismiss();
                    }
                });

                colorLayout.addView(colorView);
            }
        }
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }}
