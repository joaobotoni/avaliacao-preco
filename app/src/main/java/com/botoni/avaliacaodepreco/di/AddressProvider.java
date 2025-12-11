package com.botoni.avaliacaodepreco.di;

import android.location.Address;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface AddressProvider {
    List<Address> search(String query);
    default List<Address> filter(List<Address> addresses, String code) {
        if (!hasAddresses(addresses) || code == null) {
            return addresses != null ? addresses : Collections.emptyList();
        }
        return addresses.stream()
                .filter(address -> code.equals(address.getCountryCode()))
                .collect(Collectors.toList());
    }

    default boolean hasAddresses(@Nullable List<Address> addresses) {
        return addresses != null && !addresses.isEmpty();
    }

    default Address first(@Nullable List<Address> addresses) {
        if (hasAddresses(addresses)) {
            assert addresses != null;
            return addresses.get(0);
        } else {
            return null;
        }
    }

    default String format(@Nullable Address address) {
        return address != null && address.getAddressLine(0) != null ? address.getAddressLine(0) : "";
    }

    default String code(@Nullable Address address) {
        return address != null ? address.getCountryCode() : null;
    }

    default List<Address> emptyList() {
        return Collections.emptyList();
    }
}