package com.botoni.avaliacaodepreco.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
import com.botoni.avaliacaodepreco.ui.adapter.RecomendacaoAdapter;
import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.Recomendacao;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;
import com.botoni.avaliacaodepreco.utils.LocationService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainFragment extends Fragment {

    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADO = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA_ABATE = new BigDecimal("69.70");
    private static final BigDecimal TAXA_FUNRURAL = new BigDecimal("0.015");
    private static final BigDecimal AGIO = new BigDecimal("30");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode MODO_ARREDONDAMENTO = RoundingMode.HALF_EVEN;
    private static final DecimalFormatSymbols SIMBOLOS_BRASILEIROS = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat FORMATADOR_MOEDA = new DecimalFormat("#,##0.00", SIMBOLOS_BRASILEIROS);
    private EditText camposPesoBezerro, campoPrecoArroba, campoQuantidade;
    private Button botaoCalcular;
    private TextView textoValorBezerro, textoValorPorKg, textoValorTotal, textoMotivoRecomendacao;
    private CardView cardResultado, cardFrete, cardRecomendacaoTransporte;
    private AutoCompleteTextView campoTipoTransporte;
    private RecyclerView listaRecomendacoes, listaLocalizacao;
    private EditText origem, destino;
    private FrameLayout bottomSheet;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private AppDatabase database;
    private LocationService locationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LocationAdapter locationAdapter;
    private final List<Address> listAddress = new ArrayList<>();
    private List<TipoVeiculoFrete> tiposVeiculo = new ArrayList<>();
    private List<CategoriaFrete> categorias = new ArrayList<>();
    private List<CapacidadeFrete> capacidadesFrete = new ArrayList<>();
    private CategoriaFrete categoriaAtual;
    BigDecimal valorBezerroCalculado = BigDecimal.ZERO;
    BigDecimal valorTotalCalculado = BigDecimal.ZERO;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (Boolean.FALSE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION)) ||
                                Boolean.FALSE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                            showPermissionDeniedDialog();
                        }
                    });

    public MainFragment() {
        super(R.layout.fragment_main);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        inicializarComponentes(view);
        configurarListeners();
        carregarDadosIniciais();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void inicializarComponentes(@NonNull View view) {
        database = AppDatabase.getDatabase(requireContext());
        locationService = new LocationService(requireContext());

        vincularViews(view);
        configurarRecyclerViews();
        configurarBottomSheet();
        verificarPermissoes();
    }

    private void vincularViews(@NonNull View view) {
        // Bezerro
        camposPesoBezerro = view.findViewById(R.id.et_peso_animal);
        campoPrecoArroba = view.findViewById(R.id.et_preco_arroba);
        campoQuantidade = view.findViewById(R.id.et_quantidade_animais);
        botaoCalcular = view.findViewById(R.id.btn_send);
        cardResultado = view.findViewById(R.id.card_resultado);
        textoValorBezerro = view.findViewById(R.id.tv_valor_cabeca);
        textoValorTotal = view.findViewById(R.id.tv_valor_total);
        textoValorPorKg = view.findViewById(R.id.tv_valor_kg);

        // Frete
        campoTipoTransporte = view.findViewById(R.id.actv_categoria_animal);
        cardFrete = view.findViewById(R.id.card_frete);

        // Recomendação
        cardRecomendacaoTransporte = view.findViewById(R.id.card_recomendacao_transporte);
        listaRecomendacoes = view.findViewById(R.id.rv_recomendacoes_transporte);
        textoMotivoRecomendacao = view.findViewById(R.id.tv_motivo_recomendacao);

        // Localização
        origem = view.findViewById(R.id.et_origem);
        destino = view.findViewById(R.id.et_destino);
        bottomSheet = view.findViewById(R.id.bottom_sheet_padrao);
        listaLocalizacao = view.findViewById(R.id.rv_localizacoes);
    }

    private void configurarRecyclerViews() {
        listaRecomendacoes.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaRecomendacoes.setHasFixedSize(true);

        locationAdapter = new LocationAdapter(listAddress);
        listaLocalizacao.setLayoutManager(new LinearLayoutManager(requireContext()));
        listaLocalizacao.setAdapter(locationAdapter);
    }

    private void configurarBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void configurarListeners() {
        botaoCalcular.setOnClickListener(v -> executarCalculoBezerro());
        cardFrete.setOnClickListener(v ->
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED));

        TextWatcher calculoAutoWatcher = criarTextWatcher(() -> {
            if (todosCamposPreenchidos()) {
                executarCalculoBezerro();
            } else {
                limparResultado();
            }
        });

        camposPesoBezerro.addTextChangedListener(calculoAutoWatcher);
        campoPrecoArroba.addTextChangedListener(calculoAutoWatcher);
        campoQuantidade.addTextChangedListener(calculoAutoWatcher);

        TextWatcher recomendacaoWatcher = criarTextWatcher(this::atualizarRecomendacao);
        campoQuantidade.addTextChangedListener(recomendacaoWatcher);
        campoTipoTransporte.addTextChangedListener(recomendacaoWatcher);

        TextWatcher localizacaoWatcher = criarQueryWatcher(query -> {
            List<Address> addresses = locationService.getAddressWithQuery(query);
            atualizarLocalizacoes(addresses);
        });

        origem.addTextChangedListener(localizacaoWatcher);
        destino.addTextChangedListener(localizacaoWatcher);

        campoTipoTransporte.setOnItemClickListener((parent, view, position, id) -> {
            categoriaAtual = categorias.get(position);
            atualizarRecomendacao();
        });
    }

    private void carregarDadosIniciais() {
        executor.execute(() -> {
            tiposVeiculo = database.tipoVeiculoFreteDao().getAll();
            capacidadesFrete = database.capacidadeFreteDao().getAll();
            categorias = database.categoriaFreteDao().getAll();

            requireActivity().runOnUiThread(this::configurarAutoComplete);
        });
    }

    private void configurarAutoComplete() {
        String[] descricoes = categorias.stream()
                .map(CategoriaFrete::getDescricao)
                .toArray(String[]::new);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, descricoes);
        campoTipoTransporte.setAdapter(adapter);
    }

    private void executarCalculoBezerro() {
        String peso = getText(camposPesoBezerro);
        String precoArroba = getText(campoPrecoArroba);
        String quantidade = getText(campoQuantidade);

        if (!validarCampos(peso, precoArroba, quantidade)) {
            return;
        }

        try {
            BigDecimal pesoKg = new BigDecimal(peso);
            BigDecimal precoPorArroba = new BigDecimal(precoArroba);
            BigDecimal qtd = new BigDecimal(quantidade);

            BigDecimal valorBezerro = calcularValorBezerro(pesoKg, precoPorArroba);
            BigDecimal valorPorKg = calcularValorPorKg(pesoKg, precoPorArroba);
            BigDecimal valorTotal = valorBezerro.multiply(qtd)
                    .setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);

            exibirResultado(valorBezerro, valorTotal, valorPorKg);
        } catch (NumberFormatException e) {
            mostrarErro("Valores inválidos");
            ocultarResultado();
        }
    }

    private BigDecimal calcularValorBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal valorBase = converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
        BigDecimal valorAgio = calcularAgio(pesoKg, precoPorArroba);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularValorPorKg(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return calcularValorBezerro(pesoKg, precoPorArroba)
                .divide(pesoKg, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularAgio(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        if (pesoKg.compareTo(PESO_BASE_KG) >= 0) {
            return calcularAgioAcimaPesoBase(pesoKg, precoPorArroba);
        }
        return calcularAgioAbaixoPesoBase(pesoKg, precoPorArroba);
    }

    private BigDecimal calcularAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal valorRef = calcularValorReferenciaAgio(precoPorArroba);
        BigDecimal taxaPorArroba = calcularTaxaPorArroba(pesoKg, precoPorArroba);
        BigDecimal agioPorArroba = valorRef.subtract(taxaPorArroba);
        BigDecimal arrobasRestantes = ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private BigDecimal calcularAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            BigDecimal valorRef = calcularValorReferenciaAgio(precoPorArroba);
            BigDecimal taxa = calcularTaxaPorArroba(pesoAtual, precoPorArroba);
            acumulado = acumulado.add(valorRef.subtract(taxa));

            BigDecimal arrobas = converterKgParaArrobas(pesoAtual);
            BigDecimal proximasArrobas = arrobas.setScale(0, RoundingMode.CEILING).add(BigDecimal.ONE);
            pesoAtual = proximasArrobas.multiply(PESO_ARROBA_KG).min(PESO_BASE_KG);
        }

        return acumulado.add(calcularAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba));
    }

    private BigDecimal calcularValorReferenciaAgio(BigDecimal precoPorArroba) {
        BigDecimal taxaPorArroba = calcularTaxaPorArroba(PESO_BASE_KG, precoPorArroba);
        BigDecimal fatorAgio = CEM.subtract(AGIO).divide(CEM, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal valorComAgio = arrobasBase.multiply(precoPorArroba).divide(fatorAgio, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
        BigDecimal valorSemAgio = arrobasBase.multiply(precoPorArroba);
        BigDecimal agioTotal = valorComAgio.subtract(valorSemAgio);
        BigDecimal arrobasRestantes = ARROBAS_ABATE_ESPERADO.subtract(arrobasBase);
        BigDecimal agioPorArroba = agioTotal.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);

        return taxaPorArroba.add(agioPorArroba);
    }

    private BigDecimal calcularTaxaPorArroba(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        BigDecimal totalTaxas = funrural.add(TAXA_FIXA_ABATE);
        BigDecimal arrobasRestantes = ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private void atualizarRecomendacao() {
        ocultarView(cardRecomendacaoTransporte);

        if (categoriaAtual == null || capacidadesFrete.isEmpty() || tiposVeiculo.isEmpty()) {
            return;
        }

        Integer qtd = parseIntSafe(campoQuantidade);
        if (qtd == null || qtd <= 0) return;

        List<Recomendacao> recomendacoes = calcularRecomendacoes(qtd, categoriaAtual.getId());

        if (recomendacoes.isEmpty()) {
            textoMotivoRecomendacao.setText(String.format(Locale.getDefault(),
                    "Nenhuma recomendação encontrada para %d %s.", qtd,
                    categoriaAtual.getDescricao().toLowerCase()));
            return;
        }

        exibirRecomendacoes(recomendacoes, qtd);
    }

    private List<Recomendacao> calcularRecomendacoes(int totalAnimais, Long idCategoria) {
        List<CapacidadeFrete> capacidadesOrdenadas = capacidadesFrete.stream()
                .filter(c -> c.getIdCategoriaFrete().equals(idCategoria))
                .sorted(Comparator.comparingInt(CapacidadeFrete::getQtdeFinal).reversed())
                .collect(Collectors.toList());

        if (capacidadesOrdenadas.isEmpty()) return new ArrayList<>();

        return encontrarVeiculoIdeal(totalAnimais, capacidadesOrdenadas)
                .map(veiculo -> List.of(new Recomendacao(1, getNomeVeiculo(tiposVeiculo, veiculo.getIdTipoVeiculoFrete()))))
                .orElseGet(() -> calcularCombinacao(totalAnimais, capacidadesOrdenadas));
    }

    private Optional<CapacidadeFrete> encontrarVeiculoIdeal(int total, List<CapacidadeFrete> capacidades) {
        return capacidades.stream()
                .filter(c -> total >= c.getQtdeInicial() && total <= c.getQtdeFinal())
                .findFirst();
    }

    private List<Recomendacao> calcularCombinacao(int total, List<CapacidadeFrete> capacidades) {
        List<Recomendacao> resultado = new ArrayList<>();
        int restantes = total;

        CapacidadeFrete maior = capacidades.get(0);
        int maxCap = maior.getQtdeFinal();

        if (maxCap > 0) {
            int veiculosCompletos = restantes / maxCap;
            if (veiculosCompletos > 0) {
                resultado.add(new Recomendacao(veiculosCompletos, getNomeVeiculo(tiposVeiculo, maior.getIdTipoVeiculoFrete())));
                restantes %= maxCap;
            }
        }

        if (restantes > 0) {
            int finalRestantes = restantes;
            CapacidadeFrete veiculoRestante = capacidades.stream()
                    .filter(c -> finalRestantes >= c.getQtdeInicial() && finalRestantes <= c.getQtdeFinal())
                    .findFirst()
                    .orElse(maior);

            resultado.add(new Recomendacao(1, getNomeVeiculo(tiposVeiculo, veiculoRestante.getIdTipoVeiculoFrete())));
        }

        return agruparRecomendacoes(resultado);
    }

    private List<Recomendacao> agruparRecomendacoes(List<Recomendacao> recomendacoes) {
        return recomendacoes.stream()
                .collect(Collectors.groupingBy(Recomendacao::getTipoTransporte,
                        Collectors.summingInt(Recomendacao::getQtdeRecomendada)))
                .entrySet().stream()
                .map(e -> new Recomendacao(e.getValue(), e.getKey()))
                .sorted(Comparator.comparing(Recomendacao::getTipoTransporte))
                .collect(Collectors.toList());
    }

    private static String getNomeVeiculo(List<TipoVeiculoFrete> tiposVeiculo, Long idTipo) {
        return tiposVeiculo == null ? "Desconhecido" :
                tiposVeiculo.stream()
                        .filter(t -> t.getId().longValue() == idTipo)
                        .map(TipoVeiculoFrete::getDescricao)
                        .findFirst()
                        .orElse("Desconhecido");
    }

    private void atualizarLocalizacoes(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            Log.d("Localizacao", "Nenhum endereço encontrado");
            return;
        }
        listAddress.clear();
        listAddress.addAll(addresses);
        locationAdapter.notifyDataSetChanged();
    }

    private void verificarPermissoes() {
        if (!hasLocationPermissions()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.permission_denied_title)
                .setMessage(R.string.permission_denied)
                .show();
    }

    private TextWatcher criarTextWatcher(Runnable acao) {
        return new TextWatcher() {
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
        };
    }

    private TextWatcher criarQueryWatcher(QueryCallback callback) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 1) {
                    callback.onQuery(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private String getText(EditText editText) {
        return editText.getText().toString().trim();
    }

    private boolean isFilled(EditText editText) {
        return !getText(editText).isEmpty();
    }

    private boolean todosCamposPreenchidos() {
        return isFilled(camposPesoBezerro) && isFilled(campoPrecoArroba) && isFilled(campoQuantidade);
    }

    private boolean validarCampos(String... campos) {
        for (String campo : campos) {
            if (campo.isEmpty()) {
                mostrarErro(R.string.erro_campo_vazio);
                ocultarResultado();
                return false;
            }
        }
        return true;
    }

    private Integer parseIntSafe(EditText editText) {
        try {
            String text = getText(editText);
            return text.isEmpty() ? null : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + FORMATADOR_MOEDA.format(valor);
    }

    private void exibirResultado(BigDecimal valorBezerro, BigDecimal valorTotal, BigDecimal valorPorKg) {
        valorBezerroCalculado = valorBezerro;
        valorTotalCalculado = valorTotal;
        textoValorBezerro.setText(formatarMoeda(valorBezerro));
        textoValorTotal.setText(formatarMoeda(valorTotal));
        textoValorPorKg.setText(formatarMoeda(valorPorKg));
        mostrarView(cardResultado);
    }

    private void exibirRecomendacoes(List<Recomendacao> recomendacoes, int qtd) {
        listaRecomendacoes.setAdapter(new RecomendacaoAdapter(recomendacoes));
        int totalVeiculos = recomendacoes.stream()
                .mapToInt(Recomendacao::getQtdeRecomendada)
                .sum();

        String msg = String.format(Locale.getDefault(),
                "Para %d %s(s), requer %d veículo(s).", qtd,
                categoriaAtual.getDescricao().toLowerCase(), totalVeiculos);

        textoMotivoRecomendacao.setText(msg);
        mostrarView(textoMotivoRecomendacao);
        mostrarView(cardRecomendacaoTransporte);
        mostrarView(cardFrete);
    }

    private void mostrarErro(int resId) {
        mostrarErro(getString(resId));
    }

    private void mostrarErro(String mensagem) {
        Snackbar.make(requireView(), mensagem, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(Color.RED)
                .setTextColor(Color.WHITE)
                .show();
    }

    private void mostrarView(View view) {
        view.setVisibility(View.VISIBLE);
    }

    private void ocultarView(View view) {
        view.setVisibility(View.GONE);
    }

    private void ocultarResultado() {
        ocultarView(cardResultado);
    }

    private void limparResultado() {
        ocultarResultado();
        valorBezerroCalculado = BigDecimal.ZERO;
        valorTotalCalculado = BigDecimal.ZERO;
    }

    @FunctionalInterface
    private interface QueryCallback {
        void onQuery(String query);
    }
}