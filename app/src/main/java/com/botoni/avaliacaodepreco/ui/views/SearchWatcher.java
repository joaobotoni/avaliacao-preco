package com.botoni.avaliacaodepreco.ui.views;

import android.text.Editable;
import android.text.TextWatcher;


import com.botoni.avaliacaodepreco.di.SearchCallback;

public class SearchWatcher implements TextWatcher {
    private static final int MIN_SEARCH_LENGTH = 2;
    private final SearchCallback callback;

    public SearchWatcher(SearchCallback callback) {
        this.callback = callback;
    }

    @Override
    public void afterTextChanged(Editable text) {
        if (text.length() >= MIN_SEARCH_LENGTH) {
            callback.onSearch(text.toString());
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
}
