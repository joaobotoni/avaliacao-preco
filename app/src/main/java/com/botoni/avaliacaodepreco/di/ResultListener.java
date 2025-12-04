package com.botoni.avaliacaodepreco.di;

public interface ResultListener<T> {
    void onSuccess(T result);
    void onError(int error);
}
