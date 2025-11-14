package com.botoni.avaliacaodepreco;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeTransporte;
import com.botoni.avaliacaodepreco.data.entities.FaixaDistancia;
import com.botoni.avaliacaodepreco.data.entities.TipoTransporte;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends Fragment {
    private static final String TAG = "MainFragment";
    private static final DecimalFormatSymbols BRAZILIAN_SYMBOLS = new DecimalFormatSymbols(new Locale("pt", "br"));
    private static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("#,##0.00", BRAZILIAN_SYMBOLS);
    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADO = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA = new BigDecimal("69.70");
    private static final BigDecimal TAXA_FUNRURAL = new BigDecimal("0.015");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private EditText editTextCalfWeight;
    private EditText editTextPremiumPercentage;
    private EditText editTextArrobaPrice;
    private EditText editTextQuantity;
    private Button buttonCalculate;
    private TextView textViewCalfValue;
    private TextView textViewTotalValue;
    private CardView cardViewResult;
    private AutoCompleteTextView fieldTransportType;
    private TextInputEditText fieldDistanceKm;
    private CardView cardCapacityInfo;
    private TextView textCapacityBois;
    private TextView textCapacityVacas;
    private TextView textCapacityBezerros;
    private CardView cardFreteResult;
    private TextView textFreteTransportType;
    private TextView textFreteDistance;
    private TextView textFreteValue;

    private AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BigDecimal calculatedCalfValue = BigDecimal.ZERO;
    private BigDecimal calculatedTotalValue = BigDecimal.ZERO;


    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeComponents(view);
        setupBezerroCalculation();
        setupFreteCalculation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
    private void initializeComponents(@NonNull View view) {
        database = AppDatabase.getDatabase(requireContext());
        bindBezerroViews(view);
        bindFreteViews(view);
    }

    private void bindBezerroViews(@NonNull View rootView) {
        editTextCalfWeight = rootView.findViewById(R.id.field_weight);
        editTextPremiumPercentage = rootView.findViewById(R.id.field_percentage);
        editTextArrobaPrice = rootView.findViewById(R.id.field_arroba_price);
        editTextQuantity = rootView.findViewById(R.id.field_qtd);
        textViewCalfValue = rootView.findViewById(R.id.text_calf_value);
        textViewTotalValue = rootView.findViewById(R.id.text_result_value);
        buttonCalculate = rootView.findViewById(R.id.button_calculate);
        cardViewResult = rootView.findViewById(R.id.card_result);
    }

    private void bindFreteViews(@NonNull View rootView) {
        fieldTransportType = rootView.findViewById(R.id.field_transport_type);
        fieldDistanceKm = rootView.findViewById(R.id.field_distance_km);
        cardCapacityInfo = rootView.findViewById(R.id.card_capacity_info);
        textCapacityBois = rootView.findViewById(R.id.text_capacity_bois);
        textCapacityVacas = rootView.findViewById(R.id.text_capacity_vacas);
        textCapacityBezerros = rootView.findViewById(R.id.text_capacity_bezerros);
        cardFreteResult = rootView.findViewById(R.id.card_frete_result);
        textFreteTransportType = rootView.findViewById(R.id.text_frete_transport_type);
        textFreteDistance = rootView.findViewById(R.id.text_frete_distance);
        textFreteValue = rootView.findViewById(R.id.text_frete_value);
    }

    private void setupBezerroCalculation() {
        setupCalculateButton();
        setupAutoCalculationWatchers();
    }

    private void setupCalculateButton() {
        buttonCalculate.setOnClickListener(v -> executeBezerroCalculation());
    }

    private void setupAutoCalculationWatchers() {
        TextWatcher autoCalcWatcher = createAutoCalculationWatcher();
        editTextCalfWeight.addTextChangedListener(autoCalcWatcher);
        editTextArrobaPrice.addTextChangedListener(autoCalcWatcher);
        editTextPremiumPercentage.addTextChangedListener(autoCalcWatcher);
        editTextQuantity.addTextChangedListener(autoCalcWatcher);
    }

    private TextWatcher createAutoCalculationWatcher() {
        return new SimpleTextWatcher(() -> {
            if (hasAllRequiredFields()) {
                executeBezerroCalculation();
            } else {
                clearBezerroResult();
            }
        });
    }

    private boolean hasAllRequiredFields() {
        return !isEmptyOrBlank(extractTextFrom(editTextCalfWeight))
                && !isEmptyOrBlank(extractTextFrom(editTextPremiumPercentage))
                && !isEmptyOrBlank(extractTextFrom(editTextArrobaPrice))
                && !isEmptyOrBlank(extractTextFrom(editTextQuantity));
    }

    private void executeBezerroCalculation() {
        String weight = extractTextFrom(editTextCalfWeight);
        String percentage = extractTextFrom(editTextPremiumPercentage);
        String arrobaPrice = extractTextFrom(editTextArrobaPrice);
        String quantity = extractTextFrom(editTextQuantity);

        if (!validateBezerroInputs(weight, percentage, arrobaPrice, quantity)) {
            return;
        }

        try {
            BigDecimal pesoKg = new BigDecimal(weight);
            BigDecimal percentualAgio = new BigDecimal(percentage);
            BigDecimal precoPorArroba = new BigDecimal(arrobaPrice);
            BigDecimal qtd = new BigDecimal(quantity);

            BigDecimal valorBezerro = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
            BigDecimal valorTotal = calcularValorTotalComQuantidade(valorBezerro, qtd);

            displayBezerroResult(valorBezerro, valorTotal);
        } catch (NumberFormatException e) {
            displayErrorMessage("Valores inválidos");
            hideBezerroResult();
        }
    }

    private boolean validateBezerroInputs(String... inputs) {
        for (String input : inputs) {
            if (isEmptyOrBlank(input)) {
                displayErrorMessage(R.string.error_value_solicited);
                hideBezerroResult();
                return false;
            }
        }
        return true;
    }

    private BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    private BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        return estaNoPesoBaseOuAcima(pesoKg)
                ? calcularValorAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio)
                : calcularValorAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private BigDecimal calcularValorAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }
    private BigDecimal calcularValorAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaReferenciaAgio(pesoAtual, precoPorArroba, percentualAgio));
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        return acumulado.add(calcularValorAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private BigDecimal calcularDiferencaReferenciaAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private BigDecimal calcularProximoPesoSuperior(BigDecimal pesoKg) {
        BigDecimal arrobas = converterKgParaArrobas(pesoKg);
        BigDecimal proximasArrobas = arredondarArrobasParaCima(arrobas);
        BigDecimal proximoPeso = proximasArrobas.multiply(PESO_ARROBA_KG);
        return limitarPesoAoPesoBase(proximoPeso);
    }

    private BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterArrobasRestantesParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
    }

    private BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        return funrural.add(TAXA_FIXA);
    }

    private BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal totalTaxas = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterValorReferenciaAgioPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(precoPorArroba, percentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorAgioTotal = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return valorAgioTotal.divide(arrobasRestantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorComAgio = calcularValorBasePesoComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private BigDecimal calcularValorBasePesoComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fatorMultiplicador = obterFatorMultiplicadorAgio(percentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fatorMultiplicador, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterFatorMultiplicadorAgio(BigDecimal percentualAgio) {
        return CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal arredondarArrobasParaCima(BigDecimal arrobas) {
        BigDecimal arredondado = arrobas.setScale(0, RoundingMode.CEILING);
        if (arredondado.compareTo(arrobas) == 0) {
            arredondado = arredondado.add(BigDecimal.ONE);
        }
        return arredondado;
    }

    private BigDecimal limitarPesoAoPesoBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) > 0 ? PESO_BASE_KG : pesoKg;
    }

    private boolean estaNoPesoBaseOuAcima(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private BigDecimal calcularValorTotalComQuantidade(BigDecimal valorBezerro, BigDecimal quantidade) {
        return valorBezerro.multiply(quantidade).setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    private void setupFreteCalculation() {
        setupTransportTypeDropdown();
        setupFreteListeners();
    }

    private void setupTransportTypeDropdown() {
        String[] types = {"TRUCK", "CAIXA BAIXA", "CAIXA ALTA", "3 EIXOS"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                types
        );
        fieldTransportType.setAdapter(adapter);
    }

    private void setupFreteListeners() {
        fieldTransportType.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = (String) parent.getItemAtPosition(position);
            TipoTransporte tipo = converterParaTipoTransporte(selectedType);
            carregarCapacidadeTransporte(tipo);
            executarCalculoFrete();
        });

        fieldDistanceKm.addTextChangedListener(new SimpleTextWatcher(this::executarCalculoFrete));
    }

    private void executarCalculoFrete() {
        String tipoStr = fieldTransportType.getText().toString().trim();
        String distanciaStr = fieldDistanceKm.getText().toString().trim();

        if (!validateFreteInputs(tipoStr, distanciaStr)) {
            hideFreteResult();
            return;
        }

        int distanciaKm = parseDistancia(distanciaStr);
        if (distanciaKm < 0) {
            hideFreteResult();
            return;
        }

        TipoTransporte tipo = converterParaTipoTransporte(tipoStr);
        calcularEExibirFrete(tipo, distanciaKm);
    }

    private boolean validateFreteInputs(String tipo, String distancia) {
        return !tipo.isEmpty() && !distancia.isEmpty();
    }

    private int parseDistancia(String distanciaStr) {
        try {
            return Integer.parseInt(distanciaStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void calcularEExibirFrete(TipoTransporte tipo, int distanciaKm) {
        executor.execute(() -> {
            try {
                Double valorFrete = calcularValorFrete(tipo, distanciaKm);

                requireActivity().runOnUiThread(() -> {
                    if (valorFrete != null && valorFrete > 0) {
                        displayFreteResult(tipo, distanciaKm, valorFrete);
                    } else {
                        displayErrorMessage("Frete não encontrado para esta distância");
                        hideFreteResult();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    displayErrorMessage("Erro ao calcular frete");
                    hideFreteResult();
                });
            }
        });
    }

    private Double calcularValorFrete(TipoTransporte tipoTransporte, int distanciaKm) {
        FaixaDistancia faixa = database.faixaDistanciaDao().getFaixaPorDistancia(distanciaKm);
        if (faixa == null) {
            return null;
        }
        return database.tabelaFreteDao().getValorFrete(tipoTransporte, faixa.getId());
    }

    private void carregarCapacidadeTransporte(TipoTransporte tipo) {
        executor.execute(() -> {
            try {
                CapacidadeTransporte capacidade = database.capacidadeTransporteDao().getByTipoTransporte(tipo);
                requireActivity().runOnUiThread(() -> Optional.ofNullable(capacidade).ifPresentOrElse(
                        this::displayCapacidadeInfo,
                        this::hideCapacidadeInfo));
            } catch (Exception e) {
                requireActivity().runOnUiThread(this::hideCapacidadeInfo);
            }
        });
    }

    private TipoTransporte converterParaTipoTransporte(String text) {
        if (text == null || text.isEmpty()) {
            return TipoTransporte.TRUCK;
        }

        switch (text) {
            case "TRUCK":
                return TipoTransporte.TRUCK;
            case "CAIXA BAIXA":
                return TipoTransporte.CAIXA_BAIXA;
            case "CAIXA ALTA":
                return TipoTransporte.CAIXA_ALTA;
            case "3 EIXOS":
                return TipoTransporte.TRES_EIXOS;
            default:
                return TipoTransporte.TRUCK;
        }
    }

    private String formatarTipoTransporte(TipoTransporte tipo) {
        if (tipo == null) {
            return "";
        }

        switch (tipo) {
            case TRUCK:
                return "TRUCK";
            case CAIXA_BAIXA:
                return "CAIXA BAIXA";
            case CAIXA_ALTA:
                return "CAIXA ALTA";
            case TRES_EIXOS:
                return "3 EIXOS";
            default:
                return "";
        }
    }

    private String formatCurrency(BigDecimal value) {
        return "R$ " + CURRENCY_FORMATTER.format(value);
    }

    @NonNull
    private String extractTextFrom(@NonNull EditText editText) {
        return editText.getText().toString().trim();
    }

    private boolean isEmptyOrBlank(@NonNull String text) {
        return text.isBlank();
    }

    private void displayBezerroResult(BigDecimal calfValue, BigDecimal totalValue) {
        calculatedCalfValue = calfValue;
        calculatedTotalValue = totalValue;
        textViewCalfValue.setText(formatCurrency(calfValue));
        textViewTotalValue.setText(formatCurrency(totalValue));
        showBezerroResult();
    }

    private void displayFreteResult(TipoTransporte tipo, int distanciaKm, double valor) {
        textFreteTransportType.setText(formatarTipoTransporte(tipo));
        textFreteDistance.setText(distanciaKm + " km");
        textFreteValue.setText(formatCurrency(BigDecimal.valueOf(valor)));
        showFreteResult();
    }

    private void displayCapacidadeInfo(CapacidadeTransporte capacidade) {
        textCapacityBois.setText(String.valueOf(capacidade.getQuantidadeBois()));
        textCapacityVacas.setText(String.valueOf(capacidade.getQuantidadeVacas()));
        textCapacityBezerros.setText(
                capacidade.getQuantidadeBezerrosMinimo() + " - " +
                        capacidade.getQuantidadeBezerrosMaximo()
        );
        showCapacidadeInfo();
    }

    private void displayErrorMessage(int resId) {
        displayErrorMessage(getResources().getString(resId));
    }

    private void displayErrorMessage(String message) {
        View root = requireView();
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }

    private void showBezerroResult() {
        cardViewResult.setVisibility(View.VISIBLE);
    }

    private void hideBezerroResult() {
        cardViewResult.setVisibility(View.GONE);
    }

    private void showFreteResult() {
        cardFreteResult.setVisibility(View.VISIBLE);
    }

    private void hideFreteResult() {
        cardFreteResult.setVisibility(View.GONE);
    }

    private void showCapacidadeInfo() {
        cardCapacityInfo.setVisibility(View.VISIBLE);
    }

    private void hideCapacidadeInfo() {
        cardCapacityInfo.setVisibility(View.GONE);
    }

    private void clearBezerroResult() {
        hideBezerroResult();
        calculatedCalfValue = BigDecimal.ZERO;
        calculatedTotalValue = BigDecimal.ZERO;
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable action;

        private SimpleTextWatcher(Runnable action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            action.run();
        }
    }
}