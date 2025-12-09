package com.botoni.avaliacaodepreco.ui.views;

import android.text.Editable;
import android.text.TextWatcher;

public class InputWatchers implements TextWatcher {

    private final Runnable runnable;

    public InputWatchers(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s != null && !s.toString().trim().isEmpty()) {
            runnable.run();
        }
    }
}
