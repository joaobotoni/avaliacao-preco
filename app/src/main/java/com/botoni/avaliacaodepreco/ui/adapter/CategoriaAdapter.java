    package com.botoni.avaliacaodepreco.ui.adapter;

    import android.content.Context;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.ArrayAdapter;
    import android.widget.TextView;

    import androidx.annotation.NonNull;

    import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;

    import java.util.List;
    import java.util.Objects;

    public class CategoriaAdapter extends ArrayAdapter<CategoriaFrete> {
        public CategoriaAdapter(Context context, List<CategoriaFrete> categorias) {
            super(context, android.R.layout.simple_dropdown_item_1line, categorias);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView textView = view.findViewById(android.R.id.text1);
            textView.setText(Objects.requireNonNull(getItem(position)).getDescricao());
            return view;
        }
    }
