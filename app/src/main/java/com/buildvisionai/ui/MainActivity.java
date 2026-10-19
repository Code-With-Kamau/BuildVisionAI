package com.buildvisionai.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.buildvisionai.R;

/**
 * The single activity: a navigation host that holds the project list, editor, estimate and versions
 * screens. It draws behind the system bars (mandatory from Android 15 when targeting API 35 or
 * later) and pads the content by the bar, cutout and keyboard insets, so no screen has to.
 */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets in = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(in.left, in.top, in.right, in.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
