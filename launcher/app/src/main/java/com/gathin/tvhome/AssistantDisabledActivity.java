package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** The destination for the remote's Assistant key when Google Assistant is disabled. */
public final class AssistantDisabledActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_assistant_disabled);
        View root = findViewById(R.id.assistant_disabled_root);
        if (root != null) root.requestFocus();
    }
}
