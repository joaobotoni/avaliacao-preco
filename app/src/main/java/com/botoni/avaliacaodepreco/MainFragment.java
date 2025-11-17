package com.botoni.avaliacaodepreco;

import android.annotation.SuppressLint;
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
import com.botoni.avaliacaodepreco.data.entities.TipoTransporte;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

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

    // ========== CONSTANTES DE FRETE ==========
    private static final int DISTANCIA_MAXIMA_BASE_KM = 300;
    private static final double VALOR_KM_ADICIONAL_TRUCK = 9.30;
    private static final double VALOR_KM_ADICIONAL_CAIXA_BAIXA = 13.00;
    private static final double VALOR_KM_ADICIONAL_CAIXA_ALTA = 15.00;
    private static final double VALOR_KM_ADICIONAL_TRES_EIXOS = 17.00;

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
    private TextInputEditText campoDistanciaKm;
    private CardView cardInfoCapacidade;
    private TextView textoCapacidadeBois;
    private TextView textoCapacidadeVacas;
    private TextView textoCapacidadeBezerros;
    private CardView cardResultadoFrete;
    private TextView textoTipoTransporteFrete;
    private TextView textoDistanciaFrete;
    private TextView textoValorFrete;

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
    }

    private void vincularViewsBezerro(@NonNull View raiz) {
        camposPesoBezerro = raiz.findViewById(R.id.field_weight);
        campoPercentualAgio = raiz.findViewById(R.id.field_percentage);
        campoPrecoArroba = raiz.findViewById(R.id.field_arroba_price);
        campoQuantidade = raiz.findViewById(R.id.field_qtd);
        textoValorBezerro = raiz.findViewById(R.id.text_calf_value);
        textoValorTotal = raiz.findViewById(R.id.text_result_value);
        botaoCalcular = raiz.findViewById(R.id.button_calculate);
        cardResultado = raiz.findViewById(R.id.card_result);
    }

    private void vincularViewsFrete(@NonNull View raiz) {
        campoTipoTransporte = raiz.findViewById(R.id.field_transport_type);
        campoDistanciaKm = raiz.findViewById(R.id.field_distance_km);
        cardInfoCapacidade = raiz.findViewById(R.id.card_capacity_info);
        textoCapacidadeBois = raiz.findViewById(R.id.text_capacity_bois);
        textoCapacidadeVacas = raiz.findViewById(R.id.text_capacity_vacas);
        textoCapacidadeBezerros = raiz.findViewById(R.id.text_capacity_bezerros);
        cardResultadoFrete = raiz.findViewById(R.id.card_frete_result);
        textoTipoTransporteFrete = raiz.findViewById(R.id.text_frete_transport_type);
        textoDistanciaFrete = raiz.findViewById(R.id.text_frete_distance);
        textoValorFrete = raiz.findViewById(R.id.text_frete_value);
    }

    // ========== SETUP - CÁLCULO DE BEZERRO ==========

    private void configurarCalculoBezerro() {
        botaoCalcular.setOnClickListener(v -> executarCalculoBezerro());
        configurarObservadoresCalculoAutomatico();
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
                exibirMensagemErro(R.string.error_value_solicited);
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

    // ========== SETUP - CÁLCULO DE FRETE ==========

//    private void configurarCalculoFrete() {
//        configurarDropdownTipoTransporte();
//        configurarObservadoresFrete();
//    }
//
//    private void configurarDropdownTipoTransporte() {
//        String[] tipos = {"TRUCK", "CAIXA BAIXA", "CAIXA ALTA", "3 EIXOS"};
//        ArrayAdapter<String> adaptador = new ArrayAdapter<>(
//                requireContext(),
//                android.R.layout.simple_dropdown_item_1line,
//                tipos
//        );
//        campoTipoTransporte.setAdapter(adaptador);
//    }
//
//    private void configurarObservadoresFrete() {
//        campoTipoTransporte.setOnItemClickListener((parent, view, position, id) -> {
//            String tipoSelecionado = (String) parent.getItemAtPosition(position);
//            TipoTransporte tipo = converterParaTipoTransporte(tipoSelecionado);
//            carregarCapacidadeTransporte(tipo);
//            executarCalculoFrete();
//        });
//
//        campoDistanciaKm.addTextChangedListener(new ObservadorTextoSimples(this::executarCalculoFrete));
//    }
//
//    private void executarCalculoFrete() {
//        String tipoStr = campoTipoTransporte.getText().toString().trim();
//        String distanciaStr = Objects.requireNonNull(campoDistanciaKm.getText()).toString().trim();
//
//        if (!saoEntradasFreteValidas(tipoStr, distanciaStr)) {
//            ocultarResultadoFrete();
//            return;
//        }
//
//        int distanciaKm = analisarDistancia(distanciaStr);
//        if (distanciaKm < 0) {
//            ocultarResultadoFrete();
//            return;
//        }
//
//        TipoTransporte tipo = converterParaTipoTransporte(tipoStr);
//        calcularEExibirFrete(tipo, distanciaKm);
//    }
//
//    private boolean saoEntradasFreteValidas(String tipo, String distancia) {
//        return !tipo.isEmpty() && !distancia.isEmpty();
//    }
//
//    private int analisarDistancia(String distanciaStr) {
//        try {
//            return Integer.parseInt(distanciaStr);
//        } catch (NumberFormatException e) {
//            return -1;
//        }
//    }
//
//    // ========== CÁLCULOS DE FRETE ==========
//
//    private void calcularEExibirFrete(TipoTransporte tipo, int distanciaKm) {
//        executor.execute(() -> {
//            try {
//                Double valorFrete = calcularValorFreteTotal(tipo, distanciaKm);
//
//                requireActivity().runOnUiThread(() -> {
//                    if (valorFrete != null && valorFrete > 0) {
//                        exibirResultadoFrete(tipo, distanciaKm, valorFrete);
//                    } else {
//                        exibirMensagemErro("Frete não encontrado para esta distância");
//                        ocultarResultadoFrete();
//                    }
//                });
//            } catch (Exception e) {
//                requireActivity().runOnUiThread(() -> {
//                    exibirMensagemErro("Erro ao calcular frete");
//                    ocultarResultadoFrete();
//                });
//            }
//        });
//    }
//
//    private Double calcularValorFreteTotal(TipoTransporte tipo, int distanciaKm) {
//        Double valorFrete = obterValorFreteDoBancoDados(tipo, distanciaKm);
//
//        if (valorFrete != null && valorFrete > 0) {
//            return valorFrete;
//        }
//
//        if (distanciaKm > DISTANCIA_MAXIMA_BASE_KM) {
//            return calcularFreteParaDistanciaExcedente(tipo, distanciaKm);
//        }
//
//        return null;
//    }
//
//    private Double calcularFreteParaDistanciaExcedente(TipoTransporte tipo, int distanciaKm) {
//        Double valorBase = obterValorFreteDoBancoDados(tipo, DISTANCIA_MAXIMA_BASE_KM);
//
//        if (valorBase == null) {
//            valorBase = 0.0;
//        }
//
//        int kmExcedente = distanciaKm - DISTANCIA_MAXIMA_BASE_KM;
//        double valorPorKmAdicional = obterValorPorKmAdicional(tipo);
//
//        return valorBase + (valorPorKmAdicional * kmExcedente);
//    }
//
//    private double obterValorPorKmAdicional(TipoTransporte tipo) {
//        switch (tipo) {
//            case TRUCK:
//                return VALOR_KM_ADICIONAL_TRUCK;
//            case CAIXA_BAIXA:
//                return VALOR_KM_ADICIONAL_CAIXA_BAIXA;
//            case CAIXA_ALTA:
//                return VALOR_KM_ADICIONAL_CAIXA_ALTA;
//            case TRES_EIXOS:
//                return VALOR_KM_ADICIONAL_TRES_EIXOS;
//            default:
//                return 0.0;
//        }
//    }
//
//    private Double obterValorFreteDoBancoDados(TipoTransporte tipoTransporte, int distanciaKm) {
//        FaixaDistancia faixa = database.faixaDistanciaDao().getFaixaPorDistancia(distanciaKm);
//        if (faixa == null) {
//            return null;
//        }
//        return database.tabelaFreteDao().getValorFrete(tipoTransporte, faixa.getId());
//    }
//
//    // ========== CAPACIDADE DE TRANSPORTE ==========
//
//    private void carregarCapacidadeTransporte(TipoTransporte tipo) {
//        executor.execute(() -> {
//            try {
//                CapacidadeTransporte capacidade = database.capacidadeTransporteDao().getByTipoTransporte(tipo);
//                requireActivity().runOnUiThread(() ->
//                        Optional.ofNullable(capacidade)
//                                .ifPresentOrElse(this::exibirInfoCapacidade, this::ocultarInfoCapacidade)
//                );
//            } catch (Exception e) {
//                requireActivity().runOnUiThread(this::ocultarInfoCapacidade);
//            }
//        });
//    }
//
//    // ========== CONVERSÕES E FORMATAÇÃO ==========
//
//    private TipoTransporte converterParaTipoTransporte(String texto) {
//        if (texto == null || texto.isEmpty()) {
//            return TipoTransporte.TRUCK;
//        }
//
//        switch (texto) {
//            case "CAIXA BAIXA":
//                return TipoTransporte.CAIXA_BAIXA;
//            case "CAIXA ALTA":
//                return TipoTransporte.CAIXA_ALTA;
//            case "3 EIXOS":
//                return TipoTransporte.TRES_EIXOS;
//            case "TRUCK":
//            default:
//                return TipoTransporte.TRUCK;
//        }
//    }
//
//    private String formatarTipoTransporte(TipoTransporte tipo) {
//        if (tipo == null) {
//            return "";
//        }
//
//        switch (tipo) {
//            case CAIXA_BAIXA:
//                return "CAIXA BAIXA";
//            case CAIXA_ALTA:
//                return "CAIXA ALTA";
//            case TRES_EIXOS:
//                return "3 EIXOS";
//            case TRUCK:
//            default:
//                return "TRUCK";
//        }
//    }

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

//    @SuppressLint("SetTextI18n")
//    private void exibirResultadoFrete(TipoTransporte tipo, int distanciaKm, double valor) {
//        textoTipoTransporteFrete.setText(formatarTipoTransporte(tipo));
//        textoDistanciaFrete.setText(distanciaKm + " km");
//        textoValorFrete.setText(formatarMoeda(valor));
//        exibirResultadoFrete();
//    }

    @SuppressLint("SetTextI18n")
    private void exibirInfoCapacidade(CapacidadeFrete capacidade) {
        textoCapacidadeBois.setText(String.valueOf(capacidade.getQuantidadeBois()));
        textoCapacidadeVacas.setText(String.valueOf(capacidade.getQuantidadeVacas()));
        textoCapacidadeBezerros.setText(
                capacidade.getQuantidadeBezerrosMinimo() + " - " + capacidade.getQuantidadeBezerrosMaximo()
        );
        exibirInfoCapacidade();
    }

    // ========== MENSAGENS E FEEDBACK ==========

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