package com.botoni.avaliacaodepreco;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MainFragment extends Fragment {
    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADO = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA_ABATE = new BigDecimal("69.70");
    private static final BigDecimal TAXA_FUNRURAL = new BigDecimal("0.015");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode MODO_ARREDONDAMENTO = RoundingMode.HALF_EVEN;
    private static final DecimalFormatSymbols SIMBOLOS_BR = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat FORMATADOR_MOEDA = new DecimalFormat("#,##0.00", SIMBOLOS_BR);

    private TextInputEditText precoArroba;
    private TextInputEditText percentualAgio;
    private TextInputEditText quantidadeAnimais;
    private TextInputEditText pesoAnimal;
    private AutoCompleteTextView categoria;
    private TextInputEditText distanciaKm;
    private MaterialButton botaoCalcular;
    private TextView valorPorCabeca;
    private TextView valorTotal;
    private CardView resultadoValor;
    private CardView recomendacaoTransporte;
    private TextView transporteRecomendado;
    private TextView quantidadeRecomendada;
    private TextView unidadeRecomendacao;
    private CardView resultadoFrete;
    private TextView tipoTransporte;
    private TextView distanciaFrete;
    private TextView valorFrete;
    private AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    BigDecimal valorCalculadoPorCabeca = BigDecimal.ZERO;
    BigDecimal valorTotalCalculado = BigDecimal.ZERO;

    private List<TipoVeiculoFrete> tiposVeiculoList;
    private List<CategoriaFrete> categorias;

    private CategoriaFrete categoriaSelecionada = null;

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        inicializarComponentes(view);
        configurarCalculoAnimal();
        configurarSpinnerCategoriaFrete();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void inicializarComponentes(@NonNull View view) {
        database = AppDatabase.getDatabase(requireContext());
        vincularViews(view);
    }

    private void vincularViews(@NonNull View view) {
        precoArroba = view.findViewById(R.id.et_preco_arroba);
        percentualAgio = view.findViewById(R.id.et_percentual_agio);
        quantidadeAnimais = view.findViewById(R.id.et_quantidade_animais);
        pesoAnimal = view.findViewById(R.id.et_peso_animal);
        categoria = view.findViewById(R.id.actv_categoria_animal);
        distanciaKm = view.findViewById(R.id.et_distancia_km);
        valorPorCabeca = view.findViewById(R.id.tv_valor_cabeca);
        valorTotal = view.findViewById(R.id.tv_valor_total);
        resultadoValor = view.findViewById(R.id.card_resultado);
        resultadoFrete = view.findViewById(R.id.card_resultado_frete);
        tipoTransporte = view.findViewById(R.id.tv_tipo_transporte);
        distanciaFrete = view.findViewById(R.id.tv_distancia_frete);
        valorFrete = view.findViewById(R.id.tv_valor_frete);
        botaoCalcular = view.findViewById(R.id.btn_calcular);
    }

    private void configurarCalculoAnimal() {
        botaoCalcular.setOnClickListener(v -> calcularValorAnimal());
        configurarCalculoAutomatico();
    }

    private void configurarCalculoAutomatico() {
        TextWatcher observer = new ObservadorTextoSimples(() -> {
            if (todosCamposPreenchidos()) {
                calcularValorAnimal();
            } else {
                limparResultadoValor();
            }
        });

        adicionarObservadorEmCampos(observer, pesoAnimal, precoArroba, percentualAgio, quantidadeAnimais);
    }

    private void adicionarObservadorEmCampos(TextWatcher observer, TextInputEditText... campos) {
        for (TextInputEditText campo : campos) {
            campo.addTextChangedListener(observer);
        }
    }

    private boolean todosCamposPreenchidos() {
        return campoTemTexto(pesoAnimal)
                && campoTemTexto(precoArroba)
                && campoTemTexto(percentualAgio)
                && campoTemTexto(quantidadeAnimais);
    }

    private void calcularValorAnimal() {
        String pesoStr = textoDe(pesoAnimal);
        String percentualStr = textoDe(percentualAgio);
        String precoStr = textoDe(precoArroba);
        String qtdStr = textoDe(quantidadeAnimais);

        if (!validarCampos(pesoStr, percentualStr, precoStr, qtdStr)) {
            return;
        }

        try {
            BigDecimal pesoKg = new BigDecimal(pesoStr);
            BigDecimal percentualAgio = new BigDecimal(percentualStr);
            BigDecimal precoPorArroba = new BigDecimal(precoStr);
            BigDecimal quantidade = new BigDecimal(qtdStr);

            BigDecimal valorCabeca = calcularValorPorCabeca(pesoKg, precoPorArroba, percentualAgio);
            BigDecimal valorTotal = valorCabeca.multiply(quantidade)
                    .setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);

            exibirResultado(valorCabeca, valorTotal);
        } catch (NumberFormatException e) {
            mostrarErro("Valores inválidos");
            ocultarResultadoValor();
        }
    }

    private BigDecimal calcularValorPorCabeca(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBase(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularAgioTotal(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularValorBase(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return kgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private BigDecimal calcularAgioTotal(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        if (pesoIgualOuAcimaBase(pesoKg)) {
            return calcularAgioAcimaBase(pesoKg, precoPorArroba, percentualAgio);
        }
        return calcularAgioAbaixoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private BigDecimal calcularAgioAcimaBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasFaltantes = arrobasParaAbate(pesoKg);
        return arrobasFaltantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal referencia = valorReferenciaAgioBase(precoPorArroba, percentualAgio);
        BigDecimal taxaRestante = taxaPorArrobaRestante(pesoKg, precoPorArroba);
        return referencia.subtract(taxaRestante);
    }

    private BigDecimal calcularAgioAbaixoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(diferencaAgio(pesoAtual, precoPorArroba, percentualAgio));
            pesoAtual = proximoPesoArredondado(pesoAtual);
        }

        return acumulado.add(calcularAgioAcimaBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private BigDecimal diferencaAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal referencia = valorReferenciaAgioBase(precoPorArroba, percentualAgio);
        BigDecimal taxa = taxaPorArrobaRestante(pesoKg, precoPorArroba);
        return referencia.subtract(taxa);
    }

    private BigDecimal proximoPesoArredondado(BigDecimal pesoKg) {
        BigDecimal arrobas = kgParaArrobas(pesoKg);
        BigDecimal proxima = arredondarArrobasCima(arrobas);
        BigDecimal pesoProximo = proxima.multiply(PESO_ARROBA_KG);
        return pesoProximo.compareTo(PESO_BASE_KG) > 0 ? PESO_BASE_KG : pesoProximo;
    }

    private BigDecimal kgParaArrobas(BigDecimal kg) {
        return kg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal arrobasParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADO.subtract(kgParaArrobas(pesoKg));
    }

    private BigDecimal totalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        return funrural.add(TAXA_FIXA_ABATE);
    }

    private BigDecimal taxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal totalTaxas = totalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = arrobasParaAbate(pesoKg);
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal valorReferenciaAgioBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxaBase = taxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioExato = agioPorArrobaExatoBase(precoPorArroba, percentualAgio);
        return taxaBase.add(agioExato);
    }

    private BigDecimal agioPorArrobaExatoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal totalAgio = valorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasBase = arrobasParaAbate(PESO_BASE_KG);
        return totalAgio.divide(arrobasBase, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal valorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorComAgio = valorBaseComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = kgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private BigDecimal valorBaseComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobas = kgParaArrobas(PESO_BASE_KG);
        BigDecimal divisor = CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
        return arrobas.multiply(precoPorArroba).divide(divisor, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal arredondarArrobasCima(BigDecimal arrobas) {
        BigDecimal arredondado = arrobas.setScale(0, RoundingMode.CEILING);
        if (arredondado.compareTo(arrobas) == 0) {
            arredondado = arredondado.add(BigDecimal.ONE);
        }
        return arredondado;
    }

    private boolean pesoIgualOuAcimaBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private void exibirResultado(BigDecimal porCabeca, BigDecimal total) {
        valorCalculadoPorCabeca = porCabeca;
        valorTotalCalculado = total;

        valorPorCabeca.setText(formatarMoeda(porCabeca));
        valorTotal.setText(formatarMoeda(total));

        exibirCard(resultadoValor);
    }

    private void limparResultadoValor() {
        ocultarCard(resultadoValor);
        valorCalculadoPorCabeca = BigDecimal.ZERO;
        valorTotalCalculado = BigDecimal.ZERO;
    }


    private void configurarSpinnerCategoriaFrete() {
        executarOperacaoAsync(() -> database.categoriaFreteDao().getAll(), this::preencherSpinnerComCategorias);
    }

    private void preencherSpinnerComCategorias(List<CategoriaFrete> categoriasList) {
        this.categorias = categoriasList;
        List<String> descricoes = extrairDescricoesDasCategorias();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, descricoes);
        categoria.setAdapter(adapter);
        categoria.setOnItemClickListener((parent, view, position, id) -> {
            categoriaSelecionada = categoriasList.get(position);
        });
    }

    private List<String> extrairDescricoesDasCategorias() {
        return categorias.stream()
                .map(CategoriaFrete::getDescricao)
                .collect(Collectors.toList());
    }

    private void exibirCard(CardView card) {
        card.setVisibility(View.VISIBLE);
    }

    private void ocultarCard(CardView card) {
        card.setVisibility(View.GONE);
    }

    private void exibirRecomendacaoTransporte() {
        exibirCard(recomendacaoTransporte);
    }

    private void ocultarRecomendacaoTransporte() {
        ocultarCard(recomendacaoTransporte);
    }

    private void exibirResultadoFrete() {
        exibirCard(resultadoFrete);
    }

    private void ocultarResultadoFrete() {
        ocultarCard(resultadoFrete);
    }

    private void ocultarResultadoValor() {
        ocultarCard(resultadoValor);
    }

    private String textoDe(TextInputEditText campo) {
        return campo.getText() != null ? campo.getText().toString().trim() : "";
    }

    private boolean campoTemTexto(TextInputEditText campo) {
        return !textoDe(campo).isBlank();
    }

    private boolean validarCampos(String... valores) {
        for (String v : valores) {
            if (v.isBlank()) {
                mostrarErro(R.string.erro_campo_vazio);
                ocultarResultadoValor();
                return false;
            }
        }
        return true;
    }

    private void mostrarErro(String mensagem) {
        Snackbar.make(requireView(), mensagem, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }

    private void mostrarErro(int resId) {
        mostrarErro(getString(resId));
    }

    private String formatarMoeda(BigDecimal valor) {
        return getString(R.string.formato_moeda, FORMATADOR_MOEDA.format(valor));
    }

    private Integer formatarInteiro(TextInputEditText input) {
        return formatarInteiro(input, "Campo obrigatório", "Digite um número inteiro válido");
    }

    private Integer formatarInteiro(TextInputEditText input, String msgVazio, String msgInvalido) {
        String texto = textoDe(input).trim();
        if (texto.isEmpty()) {
            mostrarErro(msgVazio);
            return null;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            mostrarErro(msgInvalido);
            return null;
        }
    }

    private static class ObservadorTextoSimples implements TextWatcher {
        private final Runnable acao;

        private ObservadorTextoSimples(Runnable acao) {
            this.acao = acao;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            acao.run();
        }
    }

    private <T> void executarOperacaoAsync(Supplier<T> operacao, Consumer<T> aoSucesso) {
        executarOperacaoAsync(operacao, aoSucesso, this::tratarErroGenerico);
    }

    private <T> void executarOperacaoAsync(Supplier<T> operacao, Consumer<T> aoSucesso, Consumer<Throwable> aoErro) {
        CompletableFuture.supplyAsync(operacao, executor)
                .thenAccept(resultado -> executarNaUiThread(() -> aoSucesso.accept(resultado)))
                .exceptionally(erro -> {
                    executarNaUiThread(() -> aoErro.accept(erro));
                    return null;
                });
    }

    private void executarNaUiThread(Runnable acao) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(acao);
        }
    }

    private void tratarErroGenerico(Throwable erro) {
        String mensagem = erro.getMessage() != null ? erro.getMessage() : "Erro ao processar operação";
        mostrarErro(mensagem);
    }

}