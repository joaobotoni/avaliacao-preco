package com.botoni.avaliacaodepreco.di;

import android.location.Address;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface GeocodeTask {
    List<Address> execute() throws IOException;
}
