package com.botoni.avaliacaodepreco;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.Contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainFragment extends Fragment {

    // Constantes de Formatação
    private static final DecimalFormatSymbols BRAZILIAN_SYMBOLS = new DecimalFormatSymbols(new Locale("pt", "br"));
    private static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("#,##0.00", BRAZILIAN_SYMBOLS);
    private static final DecimalFormat PERCENTAGE_FORMATTER = new DecimalFormat("+#,##0.00;-#,##0.00", BRAZILIAN_SYMBOLS);

    // Constantes do Negócio
    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADO = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA = new BigDecimal("69.70");
    private static final BigDecimal TAXA_FUNRURAL = new BigDecimal("0.015");
    private static final BigDecimal CEM = new BigDecimal("100");

    // Constantes de Precisão
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    // Componentes da Interface - Card Principal
    private TextView textViewResultValue;
    private EditText editTextArrobaPrice;
    private EditText editTextCalfWeight;
    private EditText editTextPremiumPercentage;
    private Button buttonCalculate;
    private CardView cardViewResult;

    // Componentes da Interface - Card de Análise
    private CardView cardViewAnalysis;
    private EditText editTextProposedValue;
    private LinearLayout containerVarResult;
    private TextView textViewVarPercentage;

    // Valor calculado para referência
    private BigDecimal calculatedTotalValue = BigDecimal.ZERO;

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewComponents(view);
        setupCalculateButton();
        setupProposedValueWatcher();
    }

    private void bindViewComponents(@NonNull View rootView) {
        editTextCalfWeight = rootView.findViewById(R.id.field_weight);
        editTextPremiumPercentage = rootView.findViewById(R.id.field_percentage);
        editTextArrobaPrice = rootView.findViewById(R.id.field_arroba_price);
        textViewResultValue = rootView.findViewById(R.id.text_result_value);
        buttonCalculate = rootView.findViewById(R.id.button_calculate);
        cardViewResult = rootView.findViewById(R.id.card_result);

        cardViewAnalysis = rootView.findViewById(R.id.card_analysis);
        editTextProposedValue = rootView.findViewById(R.id.field_proposed_value);
        containerVarResult = rootView.findViewById(R.id.container_var_result);
        textViewVarPercentage = rootView.findViewById(R.id.text_var_percentage);
    }

    private void setupCalculateButton() {
        buttonCalculate.setOnClickListener(view -> handleCalculateClick());
    }

    private void setupProposedValueWatcher() {
        editTextProposedValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Não necessário
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Não necessário
            }

            @Override
            public void afterTextChanged(Editable s) {
                calculateAndDisplayVarPercentage();
            }
        });
    }

    private void handleCalculateClick() {
        String weightInput = extractTextFrom(editTextCalfWeight);
        String percentageInput = extractTextFrom(editTextPremiumPercentage);
        String arrobaPriceInput = extractTextFrom(editTextArrobaPrice);

        if (!areInputsValid(weightInput, percentageInput, arrobaPriceInput)) {
            return;
        }

        processCalculationAndDisplayResult(weightInput, percentageInput, arrobaPriceInput);
    }

    private boolean areInputsValid(String weightInput, String percentageInput, String arrobaPriceInput) {
        if (isEmptyOrBlank(weightInput) || isEmptyOrBlank(percentageInput) || isEmptyOrBlank(arrobaPriceInput)) {
            displayErrorMessage(R.string.error_value_solicited);
            hideResultCards();
            return false;
        }
        return true;
    }

    @Contract(pure = true)
    private boolean isEmptyOrBlank(@NonNull String text) {
        return text.isBlank();
    }

    private void processCalculationAndDisplayResult(String weightInput, String percentageInput, String arrobaPriceInput) {
        try {
            BigDecimal pesoKg = parseToBigDecimal(weightInput);
            BigDecimal percentualAgio = parseToBigDecimal(percentageInput);
            BigDecimal precoPorArroba = parseToBigDecimal(arrobaPriceInput);

            BigDecimal valorTotal = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
            showSuccessfulCalculation(valorTotal);
        } catch (NumberFormatException exception) {
            showFailedCalculation();
        }
    }

    @NonNull
    @Contract("_ -> new")
    private BigDecimal parseToBigDecimal(String numericInput) {
        return new BigDecimal(numericInput);
    }

    private BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterArrobasRestantesParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
    }

    // === CÁLCULO DO VALOR BASE ===

    private BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    // === GESTÃO DE TAXAS ===

    private BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal taxaFunrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba)
                .multiply(TAXA_FUNRURAL);
        return taxaFunrural.add(TAXA_FIXA);
    }

    private BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal totalTaxas = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    // === CÁLCULO DO ÁGIO NO PESO BASE ===

    private BigDecimal calcularValorBasePesoComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fatorAgio = obterFatorMultiplicadorAgio(percentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fatorAgio, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterFatorMultiplicadorAgio(BigDecimal percentualAgio) {
        return CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorComAgio = calcularValorBasePesoComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioTotal = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return agioTotal.divide(arrobasRestantes, ESCALA_CALCULO, ROUNDING_MODE);
    }

    private BigDecimal obterValorReferenciaAgioPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(precoPorArroba, percentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    // === CÁLCULO DO ÁGIO ACIMA DO PESO BASE ===

    private BigDecimal calcularValorAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal referenciaBase = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return referenciaBase.subtract(taxaPorArroba);
    }

    // === CÁLCULO PRINCIPAL DO ÁGIO ===
    public BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        if (verificarSeEstaNoPesoBaseOuAcima(pesoKg)) {
            return calcularValorAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        }
        return calcularValorAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private boolean verificarSeEstaNoPesoBaseOuAcima(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private BigDecimal calcularValorAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioAcumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            BigDecimal diferencaReferencia = calcularDiferencaReferenciaAgio(pesoAtual, precoPorArroba, percentualAgio);
            agioAcumulado = agioAcumulado.add(diferencaReferencia);
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        BigDecimal agioNoPesoBase = calcularValorAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio);
        return agioAcumulado.add(agioNoPesoBase);
    }

    private BigDecimal calcularDiferencaReferenciaAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal referenciaBase = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return referenciaBase.subtract(taxaPorArroba);
    }

    private BigDecimal calcularProximoPesoSuperior(BigDecimal pesoKg) {
        BigDecimal arrobas = converterKgParaArrobas(pesoKg);
        BigDecimal proximoArrobas = arredondarArrobasParaCima(arrobas);
        BigDecimal proximoPesoKg = proximoArrobas.multiply(PESO_ARROBA_KG);
        return limitarPesoAoPesoBase(proximoPesoKg);
    }

    private BigDecimal arredondarArrobasParaCima(BigDecimal arrobas) {
        BigDecimal arrobasArredondadas = arrobas.setScale(0, RoundingMode.CEILING);
        if (arrobasArredondadas.compareTo(arrobas) == 0) {
            arrobasArredondadas = arrobasArredondadas.add(BigDecimal.ONE);
        }
        return arrobasArredondadas;
    }

    private BigDecimal limitarPesoAoPesoBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) > 0 ? PESO_BASE_KG : pesoKg;
    }

    // === CÁLCULO DO VAR% ===

    private void calculateAndDisplayVarPercentage() {
        String proposedValueInput = extractTextFrom(editTextProposedValue);

        if (isEmptyOrBlank(proposedValueInput)) {
            hideVarResult();
            return;
        }

        try {
            BigDecimal proposedValue = parseToBigDecimal(proposedValueInput);
            BigDecimal varPercentage = calculateVarPercentage(calculatedTotalValue, proposedValue);
            displayVarPercentage(varPercentage);
            showVarResult();
        } catch (NumberFormatException exception) {
            hideVarResult();
        }
    }

    private BigDecimal calculateVarPercentage(BigDecimal calculatedValue, BigDecimal proposedValue) {
        if (calculatedValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal difference = proposedValue.subtract(calculatedValue);
        BigDecimal percentage = difference.divide(calculatedValue, ESCALA_CALCULO, ROUNDING_MODE)
                .multiply(CEM);

        return percentage.setScale(ESCALA_RESULTADO, ROUNDING_MODE);
    }

    @SuppressLint("SetTextI18n")
    private void displayVarPercentage(BigDecimal percentage) {
        String formattedPercentage = PERCENTAGE_FORMATTER.format(percentage) + "%";
        textViewVarPercentage.setText(formattedPercentage);

        int color = percentage.compareTo(BigDecimal.ZERO) >= 0 ? Color.rgb(34, 139, 34) : Color.rgb(220, 20, 60);
        textViewVarPercentage.setTextColor(color);
    }

    private void showVarResult() {
        containerVarResult.setVisibility(View.VISIBLE);
    }

    private void hideVarResult() {
        containerVarResult.setVisibility(View.GONE);
    }


    private void showSuccessfulCalculation(BigDecimal totalValue) {
        calculatedTotalValue = totalValue;
        displayFormattedResult(totalValue);
        showResultCard();
        showAnalysisCard();

        editTextProposedValue.setText("");
        hideVarResult();
    }

    private void showFailedCalculation() {
        displayErrorMessage("Valores inválidos");
        hideResultCards();
    }

    @SuppressLint("DefaultLocale")
    private void displayFormattedResult(BigDecimal value) {
        String formattedValue = formatCurrency(value);
        textViewResultValue.setText(formattedValue);
    }

    @NonNull
    private String formatCurrency(BigDecimal value) {
        return "R$ " + CURRENCY_FORMATTER.format(value);
    }

    private void showResultCard() {
        cardViewResult.setVisibility(View.VISIBLE);
    }

    private void hideResultCard() {
        cardViewResult.setVisibility(View.GONE);
    }

    private void showAnalysisCard() {
        cardViewAnalysis.setVisibility(View.VISIBLE);
    }

    private void hideAnalysisCard() {
        cardViewAnalysis.setVisibility(View.GONE);
    }

    private void hideResultCards() {
        hideResultCard();
        hideAnalysisCard();
    }

    @NonNull
    private String extractTextFrom(@NonNull EditText editText) {
        return editText.getText().toString().trim();
    }

    private void displayErrorMessage(int messageResourceId) {
        String errorMessage = getResources().getString(messageResourceId);
        displayErrorMessage(errorMessage);
    }

    private void displayErrorMessage(String errorMessage) {
        View rootView = getRootViewSafely();
        showErrorSnackbar(rootView, errorMessage);
    }

    private View getRootViewSafely() {
        assert getView() != null;
        return getView().getRootView();
    }

    private void showErrorSnackbar(View rootView, String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }
}