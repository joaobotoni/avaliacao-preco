package com.botoni.avaliacaodepreco.di;

public interface Callback<T> {
    void onSuccess(T result);
    void onError(int error);
}
