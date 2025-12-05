package com.botoni.avaliacaodepreco.di;

@FunctionalInterface
public interface SearchProvider {
    void onSearch(String query);
}
