package com.botoni.avaliacaodepreco.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.botoni.avaliacaodepreco.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;

public class LocationCardFragment extends Fragment {
    private FragmentManager manager;
    private ExecutorService executor;

    public LocationCardFragment() {}
    public static LocationCardFragment newInstance() {
        return new LocationCardFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_card_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Fragment parentFragment = getParentFragment();
        if (parentFragment != null)
            manager = parentFragment.getParentFragmentManager();
        MaterialCardView cardView = view.findViewById(R.id.adicionar_localizacao_card);
        cardView.setOnClickListener(v -> manager.beginTransaction()
                .replace(R.id.fragment_container_view, LocationFragment.class, null)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    private void showDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.submit, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSnackBar(int messageResId) {
        Snackbar.make(requireView(), messageResId, Snackbar.LENGTH_SHORT).show();
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
