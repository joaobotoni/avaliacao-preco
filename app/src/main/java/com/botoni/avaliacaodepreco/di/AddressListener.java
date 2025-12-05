package com.botoni.avaliacaodepreco.di;

import android.location.Address;

import java.util.List;
import java.util.stream.Collectors;

public interface AddressListener {
    List<Address> search(String query);
    default List<Address> filter(List<Address> addresses, String code){
        return addresses.stream().filter(address -> code.equals(address.getCountryCode()))
                .collect(Collectors.toList());
    }
}
