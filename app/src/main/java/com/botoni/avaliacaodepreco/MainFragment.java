package com.botoni.avaliacaodepreco;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainFragment extends Fragment {

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

    private TextView textViewCalfValue;
    private TextView textViewTotalValue;
    private EditText editTextArrobaPrice;
    private EditText editTextCalfWeight;
    private EditText editTextPremiumPercentage;
    private EditText editTextQuantity;
    private Button buttonCalculate;
    private CardView cardViewResult;

    private BigDecimal calculatedCalfValue = BigDecimal.ZERO;
    private BigDecimal calculatedTotalValue = BigDecimal.ZERO;

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewComponents(view);
        setupCalculateButton();
        setupAutoCalculateOnWeight();
        setupAutoCalculateOnArrobaPrice();
        setupAutoCalculateOnPercentage();
        setupAutoCalculateOnQuantity();
    }

    private void bindViewComponents(@NonNull View rootView) {
        editTextCalfWeight = rootView.findViewById(R.id.field_weight);
        editTextPremiumPercentage = rootView.findViewById(R.id.field_percentage);
        editTextArrobaPrice = rootView.findViewById(R.id.field_arroba_price);
        editTextQuantity = rootView.findViewById(R.id.field_qtd);
        textViewCalfValue = rootView.findViewById(R.id.text_calf_value);
        textViewTotalValue = rootView.findViewById(R.id.text_result_value);
        buttonCalculate = rootView.findViewById(R.id.button_calculate);
        cardViewResult = rootView.findViewById(R.id.card_result);
    }

    private void setupCalculateButton() {
        buttonCalculate.setOnClickListener(v -> handleCalculateClick());
    }

    private void setupAutoCalculateOnWeight() {
        editTextCalfWeight.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.equals(current)) return;
                current = input;
                if (input.length() >= 3 && hasRequiredFieldsForAutoCalc()) handleCalculateClick();
                else clearResult();
            }
        });
    }

    private void setupAutoCalculateOnArrobaPrice() {
        editTextArrobaPrice.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.equals(current)) return;
                current = input;
                if (hasRequiredFieldsForAutoCalc()) handleCalculateClick();
                else clearResult();
            }
        });
    }

    private void setupAutoCalculateOnPercentage() {
        editTextPremiumPercentage.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.equals(current)) return;
                current = input;
                if (hasRequiredFieldsForAutoCalc()) handleCalculateClick();
                else clearResult();
            }
        });
    }

    private void setupAutoCalculateOnQuantity() {
        editTextQuantity.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.equals(current)) return;
                current = input;
                if (hasRequiredFieldsForAutoCalc()) handleCalculateClick();
                else clearResult();
            }
        });
    }

    private boolean hasRequiredFieldsForAutoCalc() {
        return !isEmptyOrBlank(extractTextFrom(editTextCalfWeight))
                && !isEmptyOrBlank(extractTextFrom(editTextPremiumPercentage))
                && !isEmptyOrBlank(extractTextFrom(editTextArrobaPrice))
                && !isEmptyOrBlank(extractTextFrom(editTextQuantity));
    }

    private void handleCalculateClick() {
        String weight = extractTextFrom(editTextCalfWeight);
        String percentage = extractTextFrom(editTextPremiumPercentage);
        String arrobaPrice = extractTextFrom(editTextArrobaPrice);
        String quantity = extractTextFrom(editTextQuantity);
        if (!areInputsValid(weight, percentage, arrobaPrice, quantity)) return;
        processCalculationAndDisplayResult(weight, percentage, arrobaPrice, quantity);
    }

    private boolean areInputsValid(String weight, String percentage, String arrobaPrice, String quantity) {
        if (isEmptyOrBlank(weight) || isEmptyOrBlank(percentage) || isEmptyOrBlank(arrobaPrice) || isEmptyOrBlank(quantity)) {
            displayErrorMessage(R.string.error_value_solicited);
            hideResultCards();
            return false;
        }
        return true;
    }

    private boolean isEmptyOrBlank(@NonNull String text) { return text.isBlank(); }

    private void processCalculationAndDisplayResult(String weightInput, String percentageInput, String arrobaPriceInput, String quantityInput) {
        try {
            BigDecimal pesoKg = parseToBigDecimal(weightInput);
            BigDecimal percentualAgio = parseToBigDecimal(percentageInput);
            BigDecimal precoPorArroba = parseToBigDecimal(arrobaPriceInput);
            BigDecimal quantidade = parseToBigDecimal(quantityInput);
            BigDecimal valorBezerro = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
            BigDecimal valorTotal = calcularValorTotalComQuantidade(valorBezerro, quantidade);
            showSuccessfulCalculation(valorBezerro, valorTotal);
        } catch (NumberFormatException e) { showFailedCalculation(); }
    }

    @NonNull private BigDecimal parseToBigDecimal(String s) { return new BigDecimal(s); }

    private BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterArrobasRestantesParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
    }

    private BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal base = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal agio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return base.add(agio).setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    private BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        return funrural.add(TAXA_FIXA);
    }

    private BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal total = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal restantes = obterArrobasRestantesParaAbate(pesoKg);
        return total.divide(restantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal calcularValorBasePesoComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fator = obterFatorMultiplicadorAgio(percentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fator, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterFatorMultiplicadorAgio(BigDecimal percentualAgio) {
        return CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal comAgio = calcularValorBasePesoComAgio(precoPorArroba, percentualAgio);
        BigDecimal semAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return comAgio.subtract(semAgio);
    }

    private BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal total = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal restantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return total.divide(restantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterValorReferenciaAgioPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxa = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agio = calcularAgioPorArrobaNoPesoBase(precoPorArroba, percentualAgio);
        return taxa.add(agio);
    }

    private BigDecimal calcularValorAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal restantes = obterArrobasRestantesParaAbate(pesoKg);
        return restantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal ref = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxa = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return ref.subtract(taxa);
    }

    public BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        return verificarSeEstaNoPesoBaseOuAcima(pesoKg)
                ? calcularValorAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio)
                : calcularValorAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private boolean verificarSeEstaNoPesoBaseOuAcima(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private BigDecimal calcularValorAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal atual = pesoKg;
        while (atual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaReferenciaAgio(atual, precoPorArroba, percentualAgio));
            atual = calcularProximoPesoSuperior(atual);
        }
        return acumulado.add(calcularValorAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private BigDecimal calcularDiferencaReferenciaAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal ref = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxa = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return ref.subtract(taxa);
    }

    private BigDecimal calcularProximoPesoSuperior(BigDecimal pesoKg) {
        BigDecimal arrobas = converterKgParaArrobas(pesoKg);
        BigDecimal prox = arredondarArrobasParaCima(arrobas);
        BigDecimal pesoProx = prox.multiply(PESO_ARROBA_KG);
        return limitarPesoAoPesoBase(pesoProx);
    }

    private BigDecimal arredondarArrobasParaCima(BigDecimal arrobas) {
        BigDecimal arred = arrobas.setScale(0, RoundingMode.CEILING);
        if (arred.compareTo(arrobas) == 0) arred = arred.add(BigDecimal.ONE);
        return arred;
    }

    private BigDecimal limitarPesoAoPesoBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) > 0 ? PESO_BASE_KG : pesoKg;
    }

    private BigDecimal calcularValorTotalComQuantidade(BigDecimal valorBezerro, BigDecimal quantidade) {
        return valorBezerro.multiply(quantidade).setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    private void showSuccessfulCalculation(BigDecimal calfValue, BigDecimal totalValue) {
        calculatedCalfValue = calfValue;
        calculatedTotalValue = totalValue;
        textViewCalfValue.setText(formatCurrency(calfValue));
        textViewTotalValue.setText(formatCurrency(totalValue));
        showResultCard();
    }

    private void showFailedCalculation() {
        displayErrorMessage("Valores inválidos");
        hideResultCards();
    }

    private String formatCurrency(BigDecimal v) { return "R$ " + CURRENCY_FORMATTER.format(v); }

    private void showResultCard() { cardViewResult.setVisibility(View.VISIBLE); }
    private void hideResultCard() { cardViewResult.setVisibility(View.GONE); }
    private void hideResultCards() { hideResultCard(); }

    private void clearResult() {
        hideResultCards();
        calculatedCalfValue = BigDecimal.ZERO;
        calculatedTotalValue = BigDecimal.ZERO;
    }

    @NonNull private String extractTextFrom(@NonNull EditText e) { return e.getText().toString().trim(); }

    private void displayErrorMessage(int resId) {
        displayErrorMessage(getResources().getString(resId));
    }

    private void displayErrorMessage(String msg) {
        View root = getView() != null ? getView().getRootView() : requireView();
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }
}