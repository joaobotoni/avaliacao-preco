package com.botoni.avaliacaodepreco;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.adapter.RecomendacaoAdapter;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.Recomendacao;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;

import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends Fragment {

    // ========== CONSTANTES DE CÁLCULO DE BEZERRO ==========
    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADO = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA_ABATE = new BigDecimal("69.70");
    private static final BigDecimal TAXA_FUNRURAL = new BigDecimal("0.015");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode MODO_ARREDONDAMENTO = RoundingMode.HALF_EVEN;


    // ========== CONSTANTES DE FORMATAÇÃO ==========
    private static final DecimalFormatSymbols SIMBOLOS_BRASILEIROS = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat FORMATADOR_MOEDA = new DecimalFormat("#,##0.00", SIMBOLOS_BRASILEIROS);

    // ========== VIEWS - BEZERRO ==========
    private EditText camposPesoBezerro;
    private EditText campoPercentualAgio;
    private EditText campoPrecoArroba;
    private EditText campoQuantidade;
    private Button botaoCalcular;
    private TextView textoValorBezerro;
    private TextView textoValorTotal;
    private CardView cardResultado;

    // ========== VIEWS - FRETE ==========
    private AutoCompleteTextView campoTipoTransporte;
    TextInputEditText campoDistanciaKm;
    private CardView cardInfoCapacidade;
    private TextView textoCapacidadeBois;
    private TextView textoCapacidadeVacas;
    private TextView textoCapacidadeBezerros;
    private CardView cardResultadoFrete;
    TextView textoTipoTransporteFrete;
    TextView textoDistanciaFrete;
    TextView textoValorFrete;

    // ========== VIEWS - RECOMENDAÇÃO ==========
    private CardView cardRecomendacaoTransporte;
    private RecyclerView listaRecomendacoes;
    private TextView textoMotivoRecomendacao;

    // ========== DEPENDÊNCIAS (Adicionais para Recomendação) ==========
    private List<TipoVeiculoFrete> tiposVeiculo;
    private List<CategoriaFrete> categorias;
    private List<CapacidadeFrete> capacidadesFrete;
    private CategoriaFrete categoriaAtual = null;

    // ========== DEPENDÊNCIAS ==========
    private AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ========== ESTADO ==========
    BigDecimal valorBezerroCalculado = BigDecimal.ZERO;
    BigDecimal valorTotalCalculado = BigDecimal.ZERO;

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        inicializarComponentes(view);
        configurarCalculoBezerro();
//        configurarCalculoFrete();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ========== INICIALIZAÇÃO ==========

    private void inicializarComponentes(@NonNull View view) {
        database = AppDatabase.getDatabase(requireContext());
        vincularViewsBezerro(view);
        vincularViewsFrete(view);
        vincularViewsRecomendacao(view);
        configurarRecyclerView();
        carregarDados();
    }

    private void vincularViewsBezerro(@NonNull View raiz) {
        campoPrecoArroba = raiz.findViewById(R.id.et_preco_arroba);
        campoPercentualAgio = raiz.findViewById(R.id.et_percentual_agio);
        campoQuantidade = raiz.findViewById(R.id.et_quantidade_animais);
        camposPesoBezerro = raiz.findViewById(R.id.et_peso_animal);
        botaoCalcular = raiz.findViewById(R.id.btn_calcular);

        cardResultado = raiz.findViewById(R.id.card_resultado);
        textoValorBezerro = raiz.findViewById(R.id.tv_valor_cabeca);
        textoValorTotal = raiz.findViewById(R.id.tv_valor_total);
    }

    private void vincularViewsFrete(@NonNull View raiz) {

        campoTipoTransporte = raiz.findViewById(R.id.actv_categoria_animal);
        campoDistanciaKm = raiz.findViewById(R.id.et_distancia_km);

        cardResultadoFrete = raiz.findViewById(R.id.card_resultado_frete);
        textoDistanciaFrete = raiz.findViewById(R.id.tv_distancia_frete);
        textoValorFrete = raiz.findViewById(R.id.tv_valor_frete);

    }

    private void vincularViewsRecomendacao(@NonNull View raiz) {
        cardRecomendacaoTransporte = raiz.findViewById(R.id.card_recomendacao_transporte);
        listaRecomendacoes = raiz.findViewById(R.id.rv_recomendacoes_transporte);
        textoMotivoRecomendacao = raiz.findViewById(R.id.tv_motivo_recomendacao);
    }

    // ========== SETUP - CÁLCULO DE BEZERRO ==========

    private void configurarCalculoBezerro() {
        botaoCalcular.setOnClickListener(v -> executarCalculoBezerro());
        configurarObservadoresCalculoAutomatico();
        configurarObservadoresRecomendacao();
    }

    private void configurarObservadoresCalculoAutomatico() {
        TextWatcher observadorCalculoAuto = new ObservadorTextoSimples(() -> {
            if (possuiTodosCamposBezerroPreenchidos()) {
                executarCalculoBezerro();
            } else {
                limparResultadoBezerro();
            }
        });

        camposPesoBezerro.addTextChangedListener(observadorCalculoAuto);
        campoPrecoArroba.addTextChangedListener(observadorCalculoAuto);
        campoPercentualAgio.addTextChangedListener(observadorCalculoAuto);
        campoQuantidade.addTextChangedListener(observadorCalculoAuto);
    }

    private void configurarObservadoresRecomendacao() {
        TextWatcher observadorRecomendacao = new ObservadorTextoSimples(this::atualizarRecomendacao);
        campoQuantidade.addTextChangedListener(observadorRecomendacao);
        campoTipoTransporte.addTextChangedListener(observadorRecomendacao);
    }

    private void configurarRecyclerView() {
        listaRecomendacoes.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaRecomendacoes.setHasFixedSize(true);
    }

    private void carregarDados() {
        executor.execute(() -> {
            tiposVeiculo = database.tipoVeiculoFreteDao().getAll();
            capacidadesFrete = database.capacidadeFreteDao().getAll();
            requireActivity().runOnUiThread(this::configurarAutoComplete);
        });
    }

    private void configurarAutoComplete() {
        executor.execute(() -> {
            categorias = database.categoriaFreteDao().getAll();
            requireActivity().runOnUiThread(() -> {
                String[] descricoes = categorias.stream().map(CategoriaFrete::getDescricao).toArray(String[]::new);
                ArrayAdapter<String> adaptador = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, descricoes);
                campoTipoTransporte.setAdapter(adaptador);

                campoTipoTransporte.setOnItemClickListener((p, v, pos, id) -> {
                    categoriaAtual = categorias.get(pos);
                    atualizarRecomendacao();
                });
            });
        });
    }

    private void atualizarRecomendacao() {
        ocultarView(cardRecomendacaoTransporte);
        if (categoriaAtual == null || capacidadesFrete == null || tiposVeiculo == null) return;

        Integer qtd = converterParaInt(campoQuantidade);
        if (qtd == null || qtd <= 0) return;

        List<Recomendacao> lista = calcularRecomendacaoInteligente(qtd, categoriaAtual.getId());
        if (lista.isEmpty()) {
            textoMotivoRecomendacao.setText(String.format(Locale.getDefault(),
                    "Nenhuma recomendação encontrada para %d %s.", qtd, categoriaAtual.getDescricao().toLowerCase()));
            textoMotivoRecomendacao.setVisibility(View.VISIBLE);
            return;
        }

        listaRecomendacoes.setAdapter(new RecomendacaoAdapter(lista));
        int totalVeiculos = lista.stream().mapToInt(Recomendacao::getQtdeRecomendada).sum();
        String msg = String.format(Locale.getDefault(),
                "Para %d %s, requer %d veículo(s).", qtd,
                categoriaAtual.getDescricao().toLowerCase(), totalVeiculos);

        textoMotivoRecomendacao.setText(msg);
        textoMotivoRecomendacao.setVisibility(View.VISIBLE);
        mostrarView(cardRecomendacaoTransporte);
    }

    private List<Recomendacao> calcularRecomendacaoInteligente(int totalAnimais, Long idCategoria) {
        if (totalAnimais <= 0 || capacidadesFrete == null || capacidadesFrete.isEmpty()) {
            return new ArrayList<>();
        }

        List<CapacidadeFrete> capacidadesOrdenadas = filtrarEOrdenarCapacidades(capacidadesFrete, idCategoria);
        if (capacidadesOrdenadas.isEmpty()) {
            return new ArrayList<>();
        }

        return encontrarVeiculoUnicoIdeal(totalAnimais, capacidadesOrdenadas)
                .map(veiculoIdeal -> List.of(new Recomendacao(1, obterNomeVeiculo(tiposVeiculo, veiculoIdeal.getIdTipoVeiculoFrete()))))
                .orElseGet(() -> calcularCombinacaoVeiculos(totalAnimais, capacidadesOrdenadas, tiposVeiculo));
    }

    private static List<CapacidadeFrete> filtrarEOrdenarCapacidades(List<CapacidadeFrete> capacidades, Long idCategoria) {
        return capacidades.stream()
                .filter(c -> c.getIdCategoriaFrete().equals(idCategoria))
                .sorted(Comparator.comparingInt(CapacidadeFrete::getQtdeFinal).reversed())
                .collect(Collectors.toList());
    }

    private static Optional<CapacidadeFrete> encontrarVeiculoUnicoIdeal(int totalAnimais, List<CapacidadeFrete> capacidadesOrdenadas) {
        return capacidadesOrdenadas.stream()
                .filter(c -> totalAnimais >= c.getQtdeInicial() && totalAnimais <= c.getQtdeFinal())
                .findFirst();
    }

    private static List<Recomendacao> calcularCombinacaoVeiculos(int totalAnimais, List<CapacidadeFrete> capacidadesOrdenadas, List<TipoVeiculoFrete> tiposVeiculo) {
        List<Recomendacao> resultado = new ArrayList<>();
        int restantes = totalAnimais;

        CapacidadeFrete maiorVeiculo = capacidadesOrdenadas.get(0);
        int maxCapacidade = maiorVeiculo.getQtdeFinal();

        if (maxCapacidade <= 0) {
            return new ArrayList<>();
        }

        int veiculosCompletos = restantes / maxCapacidade;
        if (veiculosCompletos > 0) {
            resultado.add(new Recomendacao(veiculosCompletos, obterNomeVeiculo(tiposVeiculo, maiorVeiculo.getIdTipoVeiculoFrete())));
            restantes %= maxCapacidade;
        }

        if (restantes > 0) {
            int finalRestantes = restantes;
            CapacidadeFrete veiculoParaRestante = capacidadesOrdenadas.stream()
                    .filter(c -> finalRestantes >= c.getQtdeInicial())
                    .filter(c -> finalRestantes <= c.getQtdeFinal())
                    .findFirst()
                    .orElse(maiorVeiculo);

            resultado.add(new Recomendacao(1, obterNomeVeiculo(tiposVeiculo, veiculoParaRestante.getIdTipoVeiculoFrete())));
        }

        return agruparRecomendacoes(resultado);
    }

    private static List<Recomendacao> agruparRecomendacoes(List<Recomendacao> recommendations) {
        return recommendations.stream()
                .collect(Collectors.groupingBy(Recomendacao::getTipoTransporte,
                        Collectors.summingInt(Recomendacao::getQtdeRecomendada)))
                .entrySet().stream()
                .map(e -> new Recomendacao(e.getValue(), e.getKey()))
                .sorted(Comparator.comparing(Recomendacao::getTipoTransporte))
                .collect(Collectors.toList());
    }

    private static String obterNomeVeiculo(List<TipoVeiculoFrete> tiposVeiculo, Long idTipo) {
        return tiposVeiculo == null ? "Desconhecido" :
                tiposVeiculo.stream()
                        .filter(t -> t.getId().longValue() == idTipo)
                        .map(TipoVeiculoFrete::getDescricao)
                        .findFirst()
                        .orElse("Desconhecido");
    }
    private boolean possuiTodosCamposBezerroPreenchidos() {
        return estaCampoPreenchido(camposPesoBezerro)
                && estaCampoPreenchido(campoPercentualAgio)
                && estaCampoPreenchido(campoPrecoArroba)
                && estaCampoPreenchido(campoQuantidade);
    }

    private void executarCalculoBezerro() {
        String peso = obterTextoDe(camposPesoBezerro);
        String percentual = obterTextoDe(campoPercentualAgio);
        String precoArroba = obterTextoDe(campoPrecoArroba);
        String quantidade = obterTextoDe(campoQuantidade);

        if (!validarTodosCamposPreenchidos(peso, percentual, precoArroba, quantidade)) {
            return;
        }

        try {
            BigDecimal pesoKg = new BigDecimal(peso);
            BigDecimal percentualAgio = new BigDecimal(percentual);
            BigDecimal precoPorArroba = new BigDecimal(precoArroba);
            BigDecimal qtd = new BigDecimal(quantidade);

            BigDecimal valorBezerro = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
            BigDecimal valorTotal = valorBezerro.multiply(qtd).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);

            exibirResultadoBezerro(valorBezerro, valorTotal);
        } catch (NumberFormatException e) {
            exibirMensagemErro("Valores inválidos");
            ocultarResultadoBezerro();
        }
    }

    private boolean validarTodosCamposPreenchidos(String... entradas) {
        for (String entrada : entradas) {
            if (entrada.isBlank()) {
                exibirMensagemErro(R.string.erro_campo_vazio);
                ocultarResultadoBezerro();
                return false;
            }
        }
        return true;
    }

    // ========== CÁLCULOS DE BEZERRO ==========

    private BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        if (estaNoPesoBaseOuAcima(pesoKg)) {
            return calcularAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        }
        return calcularAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private BigDecimal calcularAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private BigDecimal calcularAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaAgioNoPeso(pesoAtual, precoPorArroba, percentualAgio));
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        return acumulado.add(calcularAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private BigDecimal calcularDiferencaAgioNoPeso(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
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

    // ========== CÁLCULOS AUXILIARES DE BEZERRO ==========

    private BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal obterArrobasRestantesParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
    }

    private BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        return funrural.add(TAXA_FIXA_ABATE);
    }

    private BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal totalTaxas = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal obterValorReferenciaAgioPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaExatoPesoBase(precoPorArroba, percentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    private BigDecimal calcularAgioPorArrobaExatoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorTotalAgio = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return valorTotalAgio.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorComAgio = calcularValorPesoBaseComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private BigDecimal calcularValorPesoBaseComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fatorMultiplicador = obterFatorMultiplicadorAgio(percentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fatorMultiplicador, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal obterFatorMultiplicadorAgio(BigDecimal percentualAgio) {
        return CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
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

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + FORMATADOR_MOEDA.format(valor);
    }

    private String formatarMoeda(double valor) {
        return formatarMoeda(BigDecimal.valueOf(valor));
    }

    // ========== UTILIDADES DE VIEWS ==========

    @NonNull
    private String obterTextoDe(@NonNull EditText campo) {
        return campo.getText().toString().trim();
    }

    private boolean estaCampoPreenchido(@NonNull EditText campo) {
        return !obterTextoDe(campo).isBlank();
    }

    // ========== EXIBIÇÃO DE RESULTADOS ==========

    private void exibirResultadoBezerro(BigDecimal valorBezerro, BigDecimal valorTotal) {
        valorBezerroCalculado = valorBezerro;
        valorTotalCalculado = valorTotal;
        textoValorBezerro.setText(formatarMoeda(valorBezerro));
        textoValorTotal.setText(formatarMoeda(valorTotal));
        exibirResultadoBezerro();
    }

    private void exibirMensagemErro(int resId) {
        exibirMensagemErro(getResources().getString(resId));
    }

    private void exibirMensagemErro(String mensagem) {
        Snackbar.make(requireView(), mensagem, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }

    // ========== CONTROLE DE VISIBILIDADE ==========

    private void exibirResultadoBezerro() {
        cardResultado.setVisibility(View.VISIBLE);
    }

    private void mostrarView(View v) {
        v.setVisibility(View.VISIBLE);
    }

    private void ocultarView(View v) {
        v.setVisibility(View.GONE);
    }

    private void ocultarResultadoBezerro() {
        cardResultado.setVisibility(View.GONE);
    }

    private void exibirResultadoFrete() {
        cardResultadoFrete.setVisibility(View.VISIBLE);
    }

    private void ocultarResultadoFrete() {
        cardResultadoFrete.setVisibility(View.GONE);
    }

    private void exibirInfoCapacidade() {
        cardInfoCapacidade.setVisibility(View.VISIBLE);
    }

    private void ocultarInfoCapacidade() {
        cardInfoCapacidade.setVisibility(View.GONE);
    }

    private void limparResultadoBezerro() {
        ocultarResultadoBezerro();
        valorBezerroCalculado = BigDecimal.ZERO;
        valorTotalCalculado = BigDecimal.ZERO;
    }

    // ========== CLASSE INTERNA ==========

    private Integer converterParaInt(EditText et) {
        try {
            String t = obterTextoDe(et);
            return t.isEmpty() ? null : Integer.parseInt(t);
        } catch (Exception e) {
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
}