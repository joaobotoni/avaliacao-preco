package com.botoni.avaliacaodepreco.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.Frete;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;
import com.botoni.avaliacaodepreco.di.AddressProvider;
import com.botoni.avaliacaodepreco.di.DirectionsProvider;
import com.botoni.avaliacaodepreco.di.LocationPermissionProvider;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.botoni.avaliacaodepreco.domain.Recomendacao;
import com.botoni.avaliacaodepreco.ui.adapter.CategoriaAdapter;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
import com.botoni.avaliacaodepreco.ui.adapter.RecomendacaoAdapter;
import com.botoni.avaliacaodepreco.ui.views.InputWatchers;
import com.botoni.avaliacaodepreco.ui.views.SearchWatcher;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class MainFragment extends Fragment implements DirectionsProvider, LocationPermissionProvider, AddressProvider {

    private static final String LOCALIZACAO_PADRAO = "Cuiabá";
    private static final int TAMANHO_POOL_THREADS = 4;
    private static final int MAX_RESULTADOS_BUSCA = 10;
    private static final double DISTANCIA_MAXIMA_TABELA = 300.0;

    private static final String CHAVE_ORIGEM = "origem";
    private static final String CHAVE_DESTINO = "destino";
    private static final String CHAVE_ATUALIZAR_ORIGEM = "atualizarOrigem";
    private static final String RESULTADO_CHAVE_ORIGEM = "chaveOrigem";
    private static final String RESULTADO_CHAVE_DESTINO = "chaveDestino";
    private static final String RESULTADO_CHAVE_ATUALIZAR_ORIGEM = "chaveAtualizarOrigem";

    private static final String ESTADO_ORIGEM = "novaOrigem";
    private static final String ESTADO_DESTINO = "novoDestino";
    private static final String ESTADO_PRECO_ARROBA = "precoArroba";
    private static final String ESTADO_PESO_ANIMAL = "pesoAnimal";
    private static final String ESTADO_QUANTIDADE_ANIMAL = "quantidadeAnimal";
    private static final String ESTADO_ID_CATEGORIA = "idCategoria";
    private static final String ESTADO_DISTANCIA = "distancia";
    private static final String ESTADO_PERCENTUAL_AGIO = "percentualAgio";

    private static final BigDecimal PESO_ARROBA_KG = new BigDecimal("30.0");
    private static final BigDecimal ARROBAS_ABATE_ESPERADAS = new BigDecimal("21.00");
    private static final BigDecimal PESO_BASE_KG = new BigDecimal("180.0");
    private static final BigDecimal TAXA_FIXA_ABATE = new BigDecimal("69.70");
    private static final BigDecimal IMPOSTO_FUNRURAL = new BigDecimal("0.015");

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_CALCULO = 15;
    private static final int ESCALA_RESULTADO = 2;
    private static final RoundingMode MODO_ARREDONDAMENTO = RoundingMode.HALF_EVEN;

    private static final DecimalFormatSymbols SIMBOLOS_BRASILEIROS = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat FORMATADOR_MOEDA = new DecimalFormat("#,##0.00", SIMBOLOS_BRASILEIROS);

    private static final Map<String, BigDecimal> TAXAS_KM_ADICIONAL = criarTaxasKmAdicional();

    private ExecutorService servicoExecutor;
    private Handler manipuladorPrincipal;
    private Geocoder geocodificador;
    private FusedLocationProviderClient clienteLocalizacao;
    private AppDatabase bancoDados;

    private Address enderecoOrigem;
    private Address enderecoDestino;
    private String codigoPaisUsuario;
    private volatile double distancia;

    private final AtomicBoolean estaNavegando = new AtomicBoolean(false);
    private final AtomicBoolean estaCalculandoRota = new AtomicBoolean(false);
    private final AtomicBoolean estaCalculandoRecomendacoes = new AtomicBoolean(false);

    private final List<Address> enderecos = Collections.synchronizedList(new ArrayList<>());
    private List<TipoVeiculoFrete> tiposVeiculo = new ArrayList<>();
    private List<CategoriaFrete> categorias = new ArrayList<>();
    private List<CapacidadeFrete> capacidadesFrete = new ArrayList<>();
    private List<Frete> tabelaFrete = new ArrayList<>();
    private CategoriaFrete categoriaAtual;
    private List<Recomendacao> recomendacoes = new ArrayList<>();

    private Map<Long, TipoVeiculoFrete> cacheVeiculos = new ConcurrentHashMap<>();
    private Map<Long, List<CapacidadeFrete>> cacheCapacidadesPorCategoria = new ConcurrentHashMap<>();
    private Map<Long, List<Frete>> cacheFretePorTipoVeiculo = new ConcurrentHashMap<>();

    private TextInputEditText campoOrigem;
    private TextInputEditText campoDestino;
    private TextInputLayout layoutCampoOrigem;
    private MaterialCardView cartaoAdicionarLocalizacao;
    private RecyclerView recyclerViewLocalizacoes;
    private LocationAdapter adaptadorLocalizacoes;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private Button botaoLocalizacao;

    private AutoCompleteTextView autoCompleteCategoriaAnimal;
    private TextInputEditText campoPrecoArroba;
    private TextInputEditText percentualAgio;
    private TextInputEditText campoPesoAnimal;
    private TextInputEditText campoQuantidadeAnimais;
    private CardView cartaoResultadoBezerro;
    private TextView textoValorPorCabeca;
    private TextView textoValorPorKg;
    private TextView textoValorTotalBezerro;

    private CardView cartaoAjusteKm;
    private EditText campoKmAdicional;
    private Button botaoAdicionar5Km;
    private Button botaoAdicionar10Km;
    private Button botaoAdicionar20Km;
    private Button botaoConfirmarAjuste;

    private CardView cartaoRecomendacaoTransporte;
    private RecyclerView recyclerViewRecomendacoes;
    private TextView textoMotivoRecomendacao;
    private RecomendacaoAdapter adaptadorRecomendacoes;

    private CardView cartaoValorFreteFinal;
    private CardView cartaoResumoRota;
    private TextView textoLabelOrigem;
    private TextView textoValorOrigem;
    private TextView textoLabelDestino;
    private TextView textoValorDestino;
    private TextView textoValorDistancia;
    private TextView textoValorFrete;
    private TextView textoValorTotalFinal;

    private CardView valueFinalCard;
    private TextView valorTotalTextBz;
    private TextView valorFinalPorKgText;

    private final ActivityResultLauncher<String[]> lancadorPermissoes = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::processarResultadoPermissoes
    );

    @Override
    public void onCreate(@Nullable Bundle estadoSalvo) {
        super.onCreate(estadoSalvo);
        inicializarDependenciasBase();
        registrarOuvintesResultadoFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle estadoSalvo) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle estadoSalvo) {
        super.onViewCreated(view, estadoSalvo);
        vincularTodasViews(view);
        configurarTodosComponentesUI(view);
        carregarDadosBancoERestaurarEstado(estadoSalvo);
        view.post(this::sincronizarUIComEstado);
    }

    @Override
    public void onResume() {
        super.onResume();
        estaNavegando.set(false);
        if (getView() != null) {
            getView().post(this::sincronizarUIComEstado);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        estaNavegando.set(false);
        estaCalculandoRota.set(false);
        estaCalculandoRecomendacoes.set(false);
        getParentFragmentManager().clearFragmentResultListener(RESULTADO_CHAVE_ORIGEM);
        getParentFragmentManager().clearFragmentResultListener(RESULTADO_CHAVE_DESTINO);
        liberarReferenciasViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        limparRecursos();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estadoSaida) {
        super.onSaveInstanceState(estadoSaida);
        persistirEstadoAtual(estadoSaida);
    }

    private static Map<String, BigDecimal> criarTaxasKmAdicional() {
        Map<String, BigDecimal> taxas = new HashMap<>();
        taxas.put("TRUK", new BigDecimal("9.30"));
        taxas.put("CARRETA BAIXA", new BigDecimal("13.00"));
        taxas.put("CARRETA ALTA", new BigDecimal("15.00"));
        taxas.put("CARRETA TRES EIXOS", new BigDecimal("17.00"));
        return taxas;
    }

    private void inicializarDependenciasBase() {
        Context contexto = requireContext();
        servicoExecutor = Executors.newFixedThreadPool(TAMANHO_POOL_THREADS);
        manipuladorPrincipal = new Handler(Looper.getMainLooper());
        clienteLocalizacao = LocationServices.getFusedLocationProviderClient(contexto);
        geocodificador = new Geocoder(contexto, new Locale("pt", "BR"));
        bancoDados = AppDatabase.getDatabase(contexto);
    }

    private void vincularTodasViews(View raiz) {
        vincularViewsLocalizacao(raiz);
        vincularViewsCalculo(raiz);
        vincularViewsRecomendacao(raiz);
        vincularViewsAjusteDistancia(raiz);
        vincularViewsValorFreteFinal(raiz);
    }

    private void vincularViewsLocalizacao(View raiz) {
        campoOrigem = raiz.findViewById(R.id.origem_input);
        campoDestino = raiz.findViewById(R.id.destino_input);
        layoutCampoOrigem = raiz.findViewById(R.id.origem_input_layout);
        cartaoAdicionarLocalizacao = raiz.findViewById(R.id.adicionar_localizacao_card);
        recyclerViewLocalizacoes = raiz.findViewById(R.id.localizacoes_recycler_view);
        botaoLocalizacao = raiz.findViewById(R.id.button_fragment_location);
    }

    private void vincularViewsCalculo(View raiz) {
        autoCompleteCategoriaAnimal = raiz.findViewById(R.id.categoria_animal_input);
        campoPrecoArroba = raiz.findViewById(R.id.preco_arroba_input);
        campoPesoAnimal = raiz.findViewById(R.id.peso_animal_input);
        campoQuantidadeAnimais = raiz.findViewById(R.id.quantidade_animais_input);
        cartaoResultadoBezerro = raiz.findViewById(R.id.resultado_card);
        textoValorPorCabeca = raiz.findViewById(R.id.valor_por_cabeca_text);
        textoValorPorKg = raiz.findViewById(R.id.valor_por_kg_text);
        textoValorTotalBezerro = raiz.findViewById(R.id.valor_total_text);
        percentualAgio = raiz.findViewById(R.id.percentual_agio_input);
    }

    private void vincularViewsRecomendacao(View raiz) {
        cartaoRecomendacaoTransporte = raiz.findViewById(R.id.card_recomendacao_transporte);
        recyclerViewRecomendacoes = raiz.findViewById(R.id.rv_recomendacoes_transporte);
        textoMotivoRecomendacao = raiz.findViewById(R.id.tv_motivo_recomendacao);
    }

    private void vincularViewsAjusteDistancia(View raiz) {
        cartaoAjusteKm = raiz.findViewById(R.id.ajuste_km_card);
        campoKmAdicional = raiz.findViewById(R.id.km_adicional_input);
        botaoAdicionar5Km = raiz.findViewById(R.id.adicionar_5km_button);
        botaoAdicionar10Km = raiz.findViewById(R.id.adicionar_10km_button);
        botaoAdicionar20Km = raiz.findViewById(R.id.local_partida_adicionar_20km_button);
        botaoConfirmarAjuste = raiz.findViewById(R.id.confirmar_ajuste_button);
    }

    private void vincularViewsValorFreteFinal(View raiz) {
        cartaoValorFreteFinal = raiz.findViewById(R.id.valor_frete_final_card);
        cartaoResumoRota = raiz.findViewById(R.id.rota_resumo_card);
        textoLabelOrigem = raiz.findViewById(R.id.origem_label_text);
        textoValorOrigem = raiz.findViewById(R.id.origem_valor_text);
        textoLabelDestino = raiz.findViewById(R.id.destino_label_text);
        textoValorDestino = raiz.findViewById(R.id.destino_valor_text);
        textoValorDistancia = raiz.findViewById(R.id.distancia_valor_text);
        textoValorFrete = raiz.findViewById(R.id.valor_frete_text);
        textoValorTotalFinal = raiz.findViewById(R.id.valor_total_final_text);
        valorTotalTextBz = raiz.findViewById(R.id.valor_total_text_final);
        valorFinalPorKgText = raiz.findViewById(R.id.valor_final_por_kg_text);
        valueFinalCard = raiz.findViewById(R.id.value_final_card);
    }

    private void configurarTodosComponentesUI(View view) {
        if (!estaFragmentoAtivo()) return;
        solicitarPermissoesLocalizacao();
        configurarComponentesCalculo();
        configurarComponentesLocalizacao(view);
        configurarComponentesNavegacao();
        configurarCartaoValorFinal();
    }

    private void configurarComponentesCalculo() {
        configurarSelecaoCategoria();
        anexarOuvintesCalculo();
    }

    private void configurarComponentesLocalizacao(View view) {
        configurarComportamentoBottomSheet(view);
        configurarRecyclerViewLocalizacoes();
        configurarRecyclerViewRecomendacoes();
        configurarCliqueCartaoAdicionarLocalizacao();
        configurarComportamentoCampoOrigem();
        configurarComportamentoCampoDestino();
    }

    private void configurarComponentesNavegacao() {
        configurarBotoesAjusteDistancia();
        configurarNavegacaoFragmentoLocalizacao();
    }

    private void carregarDadosBancoERestaurarEstado(@Nullable Bundle estadoSalvo) {
        executarEmBackground(() -> {
            try {
                if (!estaFragmentoAtivo()) return;
                carregarTodasEntidadesBanco();
                if (!estaFragmentoAtivo()) return;
                construirCachesEntidades();
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo() && getView() != null && getContext() != null) {
                        aplicarAdaptadorCategorias();
                        restaurarEstadoPersistido(estadoSalvo);
                        inicializarDestinoPadrao();
                        realizarCalculoBezerro();
                        atualizarRecomendacoesVeiculos();
                        configurarCartaoValorFinal();
                    }
                });
            } catch (Exception e) {
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo() && getContext() != null) {
                        exibirMensagemErro(R.string.erro_generico);
                    }
                });
            }
        });
    }

    private void carregarTodasEntidadesBanco() {
        tiposVeiculo = bancoDados.tipoVeiculoFreteDao().getAll();
        categorias = bancoDados.categoriaFreteDao().getAll();
        capacidadesFrete = bancoDados.capacidadeFreteDao().getAll();
        tabelaFrete = bancoDados.freteDao().getAll();
    }

    private void construirCachesEntidades() {
        construirCacheVeiculos();
        construirCacheCapacidades();
        construirCacheFrete();
    }

    private void construirCacheVeiculos() {
        cacheVeiculos = tiposVeiculo.stream()
                .collect(Collectors.toMap(v -> Long.valueOf(v.getId()), v -> v));
    }

    private void construirCacheCapacidades() {
        cacheCapacidadesPorCategoria = capacidadesFrete.stream()
                .collect(Collectors.groupingBy(CapacidadeFrete::getIdCategoriaFrete));
        cacheCapacidadesPorCategoria.values().forEach(lista ->
                lista.sort(Comparator.comparingInt(CapacidadeFrete::getQtdeFinal).reversed()));
    }

    private void construirCacheFrete() {
        cacheFretePorTipoVeiculo = tabelaFrete.stream()
                .collect(Collectors.groupingBy(Frete::getIdTipoVeiculoFrete));
        cacheFretePorTipoVeiculo.values().forEach(lista ->
                lista.sort(Comparator.comparingDouble(Frete::getKmInicial)));
    }

    private void persistirEstadoAtual(@NonNull Bundle estadoSaida) {
        persistirValoresInput(estadoSaida);
        persistirEnderecos(estadoSaida);
        persistirDistancia(estadoSaida);
    }

    private void persistirValoresInput(@NonNull Bundle estadoSaida) {
        salvarValorCampoTexto(estadoSaida, campoPrecoArroba, ESTADO_PRECO_ARROBA);
        salvarValorCampoTexto(estadoSaida, campoPesoAnimal, ESTADO_PESO_ANIMAL);
        salvarValorCampoTexto(estadoSaida, campoQuantidadeAnimais, ESTADO_QUANTIDADE_ANIMAL);
        salvarValorCampoTexto(estadoSaida, percentualAgio, ESTADO_PERCENTUAL_AGIO);
        Optional.ofNullable(categoriaAtual)
                .map(CategoriaFrete::getId)
                .ifPresent(id -> estadoSaida.putLong(ESTADO_ID_CATEGORIA, id));
    }

    private void persistirEnderecos(Bundle estadoSaida) {
        Optional.ofNullable(enderecoOrigem)
                .ifPresent(endereco -> estadoSaida.putParcelable(ESTADO_ORIGEM, endereco));
        Optional.ofNullable(enderecoDestino)
                .ifPresent(endereco -> estadoSaida.putParcelable(ESTADO_DESTINO, endereco));
    }

    private void persistirDistancia(Bundle estadoSaida) {
        if (distancia > 0) {
            estadoSaida.putDouble(ESTADO_DISTANCIA, distancia);
        }
    }

    private void salvarValorCampoTexto(Bundle estadoSaida, TextInputEditText campo, String chave) {
        Optional.ofNullable(campo)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .filter(texto -> !texto.trim().isEmpty())
                .ifPresent(texto -> estadoSaida.putString(chave, texto));
    }

    private void restaurarEstadoPersistido(@Nullable Bundle estadoSalvo) {
        if (estadoSalvo == null) return;
        restaurarEnderecosDoBundle(estadoSalvo);
        restaurarValoresInputDoBundle(estadoSalvo);
        restaurarDistanciaDoBundle(estadoSalvo);
        if (possuiEnderecosRotaValidos() && distancia == 0) {
            iniciarCalculoRota();
        } else if (!possuiEnderecosRotaValidos() && distancia > 0) {
            distancia = 0;
        }
    }

    private void restaurarEnderecosDoBundle(Bundle estadoSalvo) {
        extrairEnderecoDoBundle(estadoSalvo, ESTADO_ORIGEM).ifPresent(endereco -> {
            enderecoOrigem = endereco;
            aplicarEnderecoOrigemNaUI(endereco);
            atualizarVisibilidadeComponentesCondicionais();
        });
        extrairEnderecoDoBundle(estadoSalvo, ESTADO_DESTINO).ifPresent(endereco -> {
            enderecoDestino = endereco;
        });
    }

    private void restaurarValoresInputDoBundle(Bundle estadoSalvo) {
        restaurarValorCampoTexto(estadoSalvo, campoPrecoArroba, ESTADO_PRECO_ARROBA);
        restaurarValorCampoTexto(estadoSalvo, campoPesoAnimal, ESTADO_PESO_ANIMAL);
        restaurarValorCampoTexto(estadoSalvo, campoQuantidadeAnimais, ESTADO_QUANTIDADE_ANIMAL);
        restaurarValorCampoTexto(estadoSalvo, percentualAgio, ESTADO_PERCENTUAL_AGIO); // NOVO
        long idCategoria = estadoSalvo.getLong(ESTADO_ID_CATEGORIA, -1);
        if (idCategoria != -1) {
            restaurarSelecaoCategoria(idCategoria);
        }
    }

    private void restaurarDistanciaDoBundle(Bundle estadoSalvo) {
        double distanciaSalva = estadoSalvo.getDouble(ESTADO_DISTANCIA, 0);
        if (distanciaSalva > 0) {
            distancia = distanciaSalva;
        }
    }

    private void restaurarValorCampoTexto(Bundle estadoSalvo, TextInputEditText campo, String chave) {
        Optional.ofNullable(estadoSalvo.getString(chave))
                .ifPresent(textoSalvo -> Optional.ofNullable(campo)
                        .ifPresent(c -> c.setText(textoSalvo)));
    }

    private void restaurarSelecaoCategoria(long idCategoria) {
        categorias.stream()
                .filter(categoria -> categoria.getId() == idCategoria)
                .findFirst()
                .ifPresent(categoria -> {
                    categoriaAtual = categoria;
                    Optional.ofNullable(autoCompleteCategoriaAnimal)
                            .ifPresent(autoComplete -> autoComplete.setText(categoria.getDescricao(), false));
                    atualizarRecomendacoesVeiculos();
                });
    }

    private void configurarSelecaoCategoria() {
        Optional.ofNullable(autoCompleteCategoriaAnimal).ifPresent(autoComplete ->
                autoComplete.setOnItemClickListener((parent, view, posicao, id) -> {
                    if (ehPosicaoCategoriaValida(posicao)) {
                        processarSelecaoCategoria(posicao);
                    }
                }));
    }

    private boolean ehPosicaoCategoriaValida(int posicao) {
        return posicao >= 0 && posicao < categorias.size();
    }

    private void processarSelecaoCategoria(int posicao) {
        categoriaAtual = categorias.get(posicao);
        atualizarTextoAutoCompleteCategoria(categoriaAtual.getDescricao());
        atualizarVisibilidadeComponentesCondicionais();
        atualizarRecomendacoesVeiculos();
    }

    private void atualizarTextoAutoCompleteCategoria(String descricao) {
        Optional.ofNullable(autoCompleteCategoriaAnimal)
                .ifPresent(autoComplete -> autoComplete.setText(descricao, false));
    }

    private void aplicarAdaptadorCategorias() {
        if (autoCompleteCategoriaAnimal != null && !categorias.isEmpty()) {
            CategoriaAdapter adaptador = new CategoriaAdapter(requireContext(), categorias);
            autoCompleteCategoriaAnimal.setAdapter(adaptador);
        }
    }

    private void anexarOuvintesCalculo() {
        anexarOuvinteInput(campoPrecoArroba, this::realizarCalculoBezerro);
        anexarOuvinteInput(campoPesoAnimal, this::realizarCalculoBezerro);
        anexarOuvinteInput(campoQuantidadeAnimais, this::processarMudancaQuantidade);
        anexarOuvinteInput(percentualAgio, this::realizarCalculoBezerro);

    }

    private void processarMudancaQuantidade() {
        realizarCalculoBezerro();
        atualizarRecomendacoesVeiculos();
        atualizarVisibilidadeComponentesCondicionais();
    }

    private void anexarOuvinteInput(TextInputEditText campo, Runnable acao) {
        Optional.ofNullable(campo).ifPresent(c -> c.addTextChangedListener(new InputWatchers(acao)));
    }

    private void realizarCalculoBezerro() {
        BigDecimal precoArroba = converterDecimalDeInput(campoPrecoArroba);
        BigDecimal pesoAnimal = converterDecimalDeInput(campoPesoAnimal);
        Integer quantidade = converterInteiroDeInput(campoQuantidadeAnimais);

        if (saoInputsCalculoValidos(precoArroba, pesoAnimal, quantidade)) {
            executarCalculoEExibir(precoArroba, pesoAnimal, quantidade);
        } else {
            esconderResultadoCalculo();
        }
    }

    private boolean saoInputsCalculoValidos(BigDecimal preco, BigDecimal peso, Integer quantidade) {
        return preco != null && preco.compareTo(BigDecimal.ZERO) > 0
                && peso != null && peso.compareTo(BigDecimal.ZERO) > 0
                && quantidade != null && quantidade > 0;
    }

    private void executarCalculoEExibir(BigDecimal precoArroba, BigDecimal pesoAnimal, Integer quantidade) {
        BigDecimal valorPorCabeca = calcularValorTotalBezerro(pesoAnimal, precoArroba, converterDecimalDeInput(percentualAgio));
        BigDecimal valorPorKg = calcularValorTotalPorKg(pesoAnimal, precoArroba, converterDecimalDeInput(percentualAgio));
        BigDecimal valorTotal = calcularValorTotalTodosBezerros(valorPorCabeca, quantidade);

        atualizarTextosResultadoCalculo(valorPorCabeca, valorPorKg, valorTotal);
        exibirResultadoCalculo();
        configurarCartaoValorFinal();
    }

    private void atualizarTextosResultadoCalculo(BigDecimal valorCabeca, BigDecimal valorKg, BigDecimal valorTotal) {
        definirValorTextView(textoValorPorCabeca, formatarParaMoeda(valorCabeca));
        definirValorTextView(textoValorPorKg, formatarParaMoeda(valorKg));
        definirValorTextView(textoValorTotalBezerro, formatarParaMoeda(valorTotal));
    }

    private void exibirResultadoCalculo() {
        definirVisibilidadeView(cartaoResultadoBezerro, View.VISIBLE);
    }

    private void esconderResultadoCalculo() {
        definirVisibilidadeView(cartaoResultadoBezerro, View.GONE);
    }

    private static BigDecimal calcularValorTotalTodosBezerros(BigDecimal valor, int quantidade) {
        return valor.multiply(new BigDecimal(quantidade)).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorTotalPorKg(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorTotal = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
        return valorTotal.divide(pesoKg, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private static BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        if (estaNoOuAcimaPesoBase(pesoKg)) {
            return calcularAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        }
        return calcularAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private static BigDecimal calcularAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private static BigDecimal calcularAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaAgioNoPeso(pesoAtual, precoPorArroba, percentualAgio));
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        return acumulado.add(calcularAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private static BigDecimal calcularDiferencaAgioNoPeso(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private static BigDecimal calcularProximoPesoSuperior(BigDecimal pesoKg) {
        BigDecimal arrobas = converterKgParaArrobas(pesoKg);
        BigDecimal proximasArrobas = arredondarArrobasParaCima(arrobas);
        BigDecimal proximoPeso = proximasArrobas.multiply(PESO_ARROBA_KG);
        return limitarPesoAoPesoBase(proximoPeso);
    }

    private static BigDecimal converterKgParaArrobas(BigDecimal pesoKg) {
        return pesoKg.divide(PESO_ARROBA_KG, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal obterArrobasRestantesParaAbate(BigDecimal pesoKg) {
        return ARROBAS_ABATE_ESPERADAS.subtract(converterKgParaArrobas(pesoKg));
    }

    private static BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADAS.multiply(precoPorArroba).multiply(IMPOSTO_FUNRURAL);
        return funrural.add(TAXA_FIXA_ABATE);
    }

    private static BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal taxasTotal = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return taxasTotal.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal obterValorReferenciaAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaExatamenteNoPesoBase(precoPorArroba, percentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaExatamenteNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorAgioTotal = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return valorAgioTotal.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal valorComAgio = calcularValorPesoBaseComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private static BigDecimal calcularValorPesoBaseComAgio(BigDecimal precoPorArroba, BigDecimal percentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fatorMultiplicador = obterFatorMultiplicadorAgio(percentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fatorMultiplicador, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal obterFatorMultiplicadorAgio(BigDecimal percentualAgio) {
        return CEM.subtract(percentualAgio).divide(CEM, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal arredondarArrobasParaCima(BigDecimal arrobas) {
        BigDecimal arredondado = arrobas.setScale(0, RoundingMode.CEILING);
        if (arredondado.compareTo(arrobas) == 0) {
            arredondado = arredondado.add(BigDecimal.ONE);
        }
        return arredondado;
    }

    private static BigDecimal limitarPesoAoPesoBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) > 0 ? PESO_BASE_KG : pesoKg;
    }

    private static boolean estaNoOuAcimaPesoBase(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private void atualizarRecomendacoesVeiculos() {
        if (!podeCalcularRecomendacoes()) {
            esconderExibicaoRecomendacoes();
            return;
        }

        Integer quantidade = converterInteiroDeInput(campoQuantidadeAnimais);
        if (!ehQuantidadeValida(quantidade)) {
            esconderExibicaoRecomendacoes();
            return;
        }

        if (!estaCalculandoRecomendacoes.compareAndSet(false, true)) {
            return;
        }

        final int contagemAnimais = quantidade;
        final Long idCategoria = categoriaAtual.getId();

        executarEmBackground(() -> {
            try {
                List<Recomendacao> calculadas = computarRecomendacoesVeiculos(contagemAnimais, idCategoria);

                executarNaThreadPrincipal(() -> {
                    try {
                        if (estaFragmentoAtivo() && getView() != null) {
                            recomendacoes = calculadas;
                            renderizarRecomendacoes(calculadas, contagemAnimais);
                        }
                    } finally {
                        estaCalculandoRecomendacoes.set(false);
                    }
                });
            } catch (Exception e) {
                executarNaThreadPrincipal(() -> {
                    try {
                        if (estaFragmentoAtivo()) {
                            exibirMensagemErro(R.string.erro_generico);
                            esconderExibicaoRecomendacoes();
                        }
                    } finally {
                        estaCalculandoRecomendacoes.set(false);
                    }
                });
            }
        });
    }

    private boolean podeCalcularRecomendacoes() {
        return categoriaAtual != null
                && !capacidadesFrete.isEmpty()
                && !tiposVeiculo.isEmpty()
                && cacheCapacidadesPorCategoria.containsKey(categoriaAtual.getId());
    }

    private boolean ehQuantidadeValida(Integer quantidade) {
        return quantidade != null && quantidade > 0;
    }

    private List<Recomendacao> computarRecomendacoesVeiculos(int totalAnimais, Long idCategoria) {
        List<CapacidadeFrete> capacidadesDisponiveis = cacheCapacidadesPorCategoria.getOrDefault(idCategoria, new ArrayList<>());

        assert capacidadesDisponiveis != null;
        if (capacidadesDisponiveis.isEmpty()) {
            return new ArrayList<>();
        }

        return encontrarVeiculoIdealUnico(totalAnimais, capacidadesDisponiveis)
                .map(capacidade -> criarRecomendacaoVeiculo(1, capacidade.getIdTipoVeiculoFrete()))
                .map(List::of)
                .orElseGet(() -> computarCombinacaoMultiplosVeiculos(totalAnimais, capacidadesDisponiveis));
    }

    private Optional<CapacidadeFrete> encontrarVeiculoIdealUnico(int total, List<CapacidadeFrete> capacidades) {
        return capacidades.stream()
                .filter(capacidade -> total >= capacidade.getQtdeInicial() && total <= capacidade.getQtdeFinal())
                .findFirst();
    }

    private List<Recomendacao> computarCombinacaoMultiplosVeiculos(int total, List<CapacidadeFrete> capacidades) {
        Map<Long, Integer> distribuicaoVeiculos = new HashMap<>();
        int animaisRestantes = total;

        CapacidadeFrete maiorCapacidade = capacidades.get(0);
        int capacidadeMaxima = maiorCapacidade.getQtdeFinal();

        if (capacidadeMaxima <= 0) {
            return new ArrayList<>();
        }

        if (capacidadeMaxima > 0 && animaisRestantes > capacidadeMaxima) {
            int contagemVeiculosCompletos = animaisRestantes / capacidadeMaxima;
            distribuicaoVeiculos.put(maiorCapacidade.getIdTipoVeiculoFrete(), contagemVeiculosCompletos);
            animaisRestantes = animaisRestantes % capacidadeMaxima;
        }

        if (animaisRestantes > 0) {
            CapacidadeFrete veiculoParcial = encontrarVeiculoIdealUnico(animaisRestantes, capacidades)
                    .orElse(maiorCapacidade);

            Long idTipo = veiculoParcial.getIdTipoVeiculoFrete();
            distribuicaoVeiculos.merge(idTipo, 1, Integer::sum);
        }

        return distribuicaoVeiculos.entrySet().stream()
                .map(entrada -> criarRecomendacaoVeiculo(entrada.getValue(), entrada.getKey()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Recomendacao::getTipoTransporte))
                .collect(Collectors.toList());
    }

    private Recomendacao criarRecomendacaoVeiculo(int quantidade, Long idTipoVeiculo) {
        return Optional.ofNullable(cacheVeiculos.get(idTipoVeiculo))
                .map(veiculo -> new Recomendacao(quantidade, veiculo.getDescricao()))
                .orElse(null);
    }

    private void renderizarRecomendacoes(List<Recomendacao> recomendacoes, int quantidadeTotal) {
        if (recomendacoes.isEmpty()) {
            exibirMensagemRecomendacoesVazia(quantidadeTotal);
            return;
        }

        aplicarAdaptadorRecomendacoes(recomendacoes);
        exibirResumoRecomendacoes(recomendacoes, quantidadeTotal);
        exibirExibicaoRecomendacoes();
        configurarCartaoValorFinal();
    }

    private void aplicarAdaptadorRecomendacoes(List<Recomendacao> recomendacoes) {
        adaptadorRecomendacoes = new RecomendacaoAdapter(recomendacoes);
        Optional.ofNullable(recyclerViewRecomendacoes).ifPresent(rv -> {
            if (rv.getLayoutManager() == null) {
                rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
            rv.setAdapter(adaptadorRecomendacoes);
        });
    }

    private void exibirResumoRecomendacoes(List<Recomendacao> recomendacoes, int quantidadeTotal) {
        int totalVeiculos = recomendacoes.stream()
                .mapToInt(Recomendacao::getQtdeRecomendada)
                .sum();

        String descricaoCategoria = extrairDescricaoCategoria().toLowerCase();
        String mensagemResumo = formatarResumoRecomendacao(quantidadeTotal, descricaoCategoria, totalVeiculos);

        definirValorTextView(textoMotivoRecomendacao, mensagemResumo);
    }

    private String formatarResumoRecomendacao(int quantidade, String categoria, int veiculos) {
        return String.format(Locale.getDefault(),
                getString(R.string.info_ecomendacao_transporte_msg),
                quantidade, categoria, veiculos);
    }

    private void exibirMensagemRecomendacoesVazia(int quantidade) {
        String descricaoCategoria = extrairDescricaoCategoria().toLowerCase();
        String mensagem = formatarMensagemRecomendacoesVazia(quantidade, descricaoCategoria);

        definirValorTextView(textoMotivoRecomendacao, mensagem);
        limparListaRecomendacoes();
        exibirExibicaoRecomendacoes();
    }

    private String formatarMensagemRecomendacoesVazia(int quantidade, String categoria) {
        return String.format(Locale.getDefault(),
                getString(R.string.info_recomendacao_transporte_sem_msg),
                quantidade, categoria);
    }

    private String extrairDescricaoCategoria() {
        return Optional.ofNullable(categoriaAtual)
                .map(CategoriaFrete::getDescricao)
                .orElse("");
    }

    private void limparListaRecomendacoes() {
        Optional.ofNullable(recyclerViewRecomendacoes).ifPresent(rv ->
                rv.setAdapter(new RecomendacaoAdapter(new ArrayList<>())));
    }

    private void exibirExibicaoRecomendacoes() {
        definirVisibilidadeView(cartaoRecomendacaoTransporte, View.VISIBLE);
    }

    private void esconderExibicaoRecomendacoes() {
        definirVisibilidadeView(cartaoRecomendacaoTransporte, View.GONE);
    }

    private void registrarOuvintesResultadoFragment() {
        registrarOuvinteResultadoOrigem();
        registrarOuvinteResultadoDestino();
        registrarOuvinteAtualizarOrigem();
    }

    private void registrarOuvinteResultadoOrigem() {
        getParentFragmentManager().setFragmentResultListener(RESULTADO_CHAVE_ORIGEM, this,
                (chaveRequisicao, bundle) -> {
                    if (!estaFragmentoAtivo() || getView() == null) return;

                    extrairEnderecoDoBundle(bundle, CHAVE_ORIGEM).ifPresent(endereco -> {
                        if (estaFragmentoAtivo() && getView() != null) {
                            processarSelecaoOrigem(endereco);
                        }
                    });
                });
    }

    private void registrarOuvinteResultadoDestino() {
        getParentFragmentManager().setFragmentResultListener(RESULTADO_CHAVE_DESTINO, this,
                (chaveRequisicao, bundle) -> {
                    if (!estaFragmentoAtivo() || getView() == null) return;

                    extrairEnderecoDoBundle(bundle, CHAVE_DESTINO).ifPresent(endereco -> {
                        if (estaFragmentoAtivo() && getView() != null) {
                            processarSelecaoDestino(endereco);
                        }
                    });
                });
    }

    private void registrarOuvinteAtualizarOrigem() {
        getParentFragmentManager().setFragmentResultListener(RESULTADO_CHAVE_ATUALIZAR_ORIGEM, this,
                (chaveRequisicao, bundle) -> {
                    if (!estaFragmentoAtivo() || getView() == null) return;
                    boolean deveExpandir = bundle.getBoolean(CHAVE_ATUALIZAR_ORIGEM, false);
                    if (deveExpandir) getView().post(() -> {
                        if (estaFragmentoAtivo() && bottomSheet != null) {
                            expandirBottomSheet();
                        }
                    });
                });
    }

    private void processarSelecaoOrigem(Address endereco) {
        enderecoOrigem = endereco;
        aplicarEnderecoOrigemNaUI(endereco);
        atualizarVisibilidadeComponentesCondicionais();
        configurarCartaoValorFinal();
        if (enderecoDestino != null) {
            iniciarCalculoRota();
        }
    }

    private void processarSelecaoDestino(Address endereco) {
        enderecoDestino = endereco;

        if (enderecoOrigem != null) {
            iniciarCalculoRota();
        }
    }

    private void configurarComportamentoBottomSheet(View view) {
        FrameLayout container = view.findViewById(R.id.localizacao_bottom_sheet_container);
        if (container != null) {
            bottomSheet = BottomSheetBehavior.from(container);
            definirEstadoBottomSheet(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void configurarRecyclerViewLocalizacoes() {
        Optional.ofNullable(recyclerViewLocalizacoes).ifPresent(rv -> {
            adaptadorLocalizacoes = new LocationAdapter(enderecos, this::processarSelecaoEndereco);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(adaptadorLocalizacoes);
        });
    }

    private void configurarRecyclerViewRecomendacoes() {
        Optional.ofNullable(recyclerViewRecomendacoes).ifPresent(rv -> {
            adaptadorRecomendacoes = new RecomendacaoAdapter(new ArrayList<>());
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(adaptadorRecomendacoes);
        });
    }

    private void configurarCliqueCartaoAdicionarLocalizacao() {
        Optional.ofNullable(cartaoAdicionarLocalizacao).ifPresent(cartao ->
                cartao.setOnClickListener(v -> expandirBottomSheet()));
    }

    private void configurarComportamentoCampoOrigem() {
        if (campoOrigem == null || layoutCampoOrigem == null) return;

        configurarTratamentoFocoCampoOrigem();
        configurarBuscaCampoOrigem();
        configurarBotaoLimparCampoOrigem();
    }

    private void configurarTratamentoFocoCampoOrigem() {
        Optional.ofNullable(campoOrigem).ifPresent(campo ->
                campo.setOnFocusChangeListener((v, temFoco) -> processarMudancaFocoCampoOrigem(temFoco)));
    }

    private void processarMudancaFocoCampoOrigem(boolean temFoco) {
        definirEstadoBottomSheet(temFoco ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN);
        definirVisibilidadeCursorCampoOrigem(temFoco);
    }

    @SuppressLint("PrivateResource")
    private void configurarBotaoLimparCampoOrigem() {
        Optional.ofNullable(layoutCampoOrigem).ifPresent(layout -> {
            layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            layout.setEndIconDrawable(com.google.android.material. R.drawable.mtrl_ic_cancel);
            definirVisibilidadeBotaoLimparCampoOrigem(false);
            layout.setEndIconOnClickListener(v -> resetarEnderecoOrigem());
        });
    }

    private void configurarBuscaCampoOrigem() {
        Optional.ofNullable(campoOrigem).ifPresent(campo ->
                campo.addTextChangedListener(new SearchWatcher(this::realizarBuscaEndereco)));
    }

    private void configurarComportamentoCampoDestino() {
        Optional.ofNullable(campoDestino).ifPresent(campo -> {
            campo.setHint(LOCALIZACAO_PADRAO);
            campo.setEnabled(false);
            campo.setCursorVisible(false);
        });
    }

    private void configurarCartaoValorFinal() {
        processarEnderecoCartaoFinal(enderecoOrigem, textoValorOrigem);
        processarEnderecoCartaoFinal(enderecoDestino, textoValorDestino);
        processarDistanciaCartaoFinal(distancia, textoValorDistancia);

        if (possuiEnderecosRotaValidos() && distancia > 0 && !recomendacoes.isEmpty()) {
            BigDecimal valorFrete = calcularValorFreteTotal();
            definirValorTextView(textoValorFrete, formatarParaMoeda(valorFrete));
            BigDecimal valorTotalBezerro = obterValorTotalBezerro();
            BigDecimal valorTotal = valorTotalBezerro.add(valorFrete);
            calcularValorFinalPorKg(valorTotal, converterInteiroDeInput(campoQuantidadeAnimais), converterDecimalDeInput(campoPesoAnimal), valorFinalPorKgText);
            definirValorTextView(valorTotalTextBz, formatarParaMoeda(valorTotalBezerro));
            definirValorTextView(textoValorTotalFinal, formatarParaMoeda(valorTotal));
            definirVisibilidadeView(cartaoValorFreteFinal, View.VISIBLE);
            definirVisibilidadeView(valueFinalCard, View.VISIBLE);
        } else {
            definirVisibilidadeView(cartaoValorFreteFinal, View.GONE);
        }
    }


    private void calcularValorFinalPorKg(BigDecimal valorTotal, Integer quantidade, BigDecimal pesoAnimal, TextView textView) {
        if (textView == null || valorTotal == null || quantidade == null || pesoAnimal == null) {
            return;
        }

        if (valorTotal.compareTo(BigDecimal.ZERO) <= 0 || quantidade <= 0 || pesoAnimal.compareTo(BigDecimal.ZERO) <= 0) {
            definirValorTextView(textView, formatarParaMoeda(BigDecimal.ZERO));
            return;
        }

        BigDecimal quantidadeBD = new BigDecimal(quantidade);
        BigDecimal pesoTotal = quantidadeBD.multiply(pesoAnimal);
        BigDecimal resultado = valorTotal.divide(pesoTotal, ESCALA_RESULTADO, MODO_ARREDONDAMENTO);

        definirValorTextView(textView, formatarParaMoeda(resultado));
    }

    private void processarEnderecoCartaoFinal(Address endereco, TextView campoTexto) {
        Optional.ofNullable(endereco).ifPresent(e -> campoTexto.setText(format(e)));
    }

    private void processarDistanciaCartaoFinal(double distancia, TextView campoTexto) {
        Optional.of(distancia).ifPresent(d -> campoTexto.setText(String.format("%.2f km", d)));
    }

    private BigDecimal calcularValorFreteTotal() {
        if (recomendacoes.isEmpty() || distancia <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal freteTotal = BigDecimal.ZERO;
        for (Recomendacao recomendacao : recomendacoes) {
            TipoVeiculoFrete tipoVeiculo = encontrarTipoVeiculoPorDescricao(recomendacao.getTipoTransporte());
            if (tipoVeiculo != null) {
                BigDecimal valorFrete = encontrarValorFretePorVeiculoEDistancia(tipoVeiculo.getId(), distancia);
                BigDecimal totalVeiculo = valorFrete.multiply(new BigDecimal(recomendacao.getQtdeRecomendada()));
                freteTotal = freteTotal.add(totalVeiculo);
            }
        }
        return freteTotal.setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private TipoVeiculoFrete encontrarTipoVeiculoPorDescricao(String descricao) {
        return tiposVeiculo.stream()
                .filter(v -> v.getDescricao().equals(descricao))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal encontrarValorFretePorVeiculoEDistancia(long idTipoVeiculo, double distancia) {
        List<Frete> fretes = cacheFretePorTipoVeiculo.get(idTipoVeiculo);
        if (fretes == null || fretes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        TipoVeiculoFrete tipoVeiculo = cacheVeiculos.get(idTipoVeiculo);
        if (tipoVeiculo == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal valorBase;
        if (distancia <= DISTANCIA_MAXIMA_TABELA) {
            valorBase = BigDecimal.valueOf(fretes.stream()
                    .filter(f -> distancia >= f.getKmInicial() && distancia <= f.getKmFinal())
                    .findFirst()
                    .map(Frete::getValor)
                    .orElse(0.0));
        } else {
            valorBase = BigDecimal.valueOf(fretes.stream()
                    .filter(f -> f.getKmInicial() == 251 && f.getKmFinal() == 300)
                    .findFirst()
                    .map(Frete::getValor)
                    .orElse(0.0));
            BigDecimal valorAdicional = calcularValorKmAdicional(distancia, tipoVeiculo.getDescricao());
            valorBase = valorBase.add(valorAdicional);
        }
        return valorBase.setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal calcularValorKmAdicional(double distanciaTotal, String descricaoTipoVeiculo) {
        if (distanciaTotal <= DISTANCIA_MAXIMA_TABELA) {
            return BigDecimal.ZERO;
        }
        double kmExtra = distanciaTotal - DISTANCIA_MAXIMA_TABELA;
        BigDecimal taxaPorKm = TAXAS_KM_ADICIONAL.getOrDefault(descricaoTipoVeiculo, BigDecimal.ZERO);
        assert taxaPorKm != null;
        return taxaPorKm.multiply(new BigDecimal(kmExtra)).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private BigDecimal obterValorTotalBezerro() {
        String textoTotal = Optional.ofNullable(textoValorTotalBezerro)
                .map(TextView::getText)
                .map(Object::toString)
                .map(texto -> texto.replace("R$", "").trim())
                .map(texto -> texto.replace(".", ""))
                .map(texto -> texto.replace(",", "."))
                .orElse("0");
        try {
            return new BigDecimal(textoTotal);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void processarSelecaoEndereco(Address endereco) {
        if (!estaFragmentoAtivo()) return;

        enderecoOrigem = endereco;
        notificarEnderecoOrigemSelecionado(endereco);
        aplicarEnderecoOrigemNaUI(endereco);
        colapsarBottomSheet();
        atualizarVisibilidadeComponentesCondicionais();
        configurarCartaoValorFinal();

        if (enderecoDestino != null) {
            iniciarCalculoRota();
        }
    }

    private void inicializarDestinoPadrao() {
        realizarGeocodificacao(LOCALIZACAO_PADRAO, null,
                this::tratarResultadoDestinoPadrao,
                this::exibirMensagemErro);
    }

    private void tratarResultadoDestinoPadrao(List<Address> enderecos) {
        enderecoDestino = first(enderecos);
        notificarEnderecoDestinoSelecionado(enderecoDestino);
    }

    private void resetarEnderecoOrigem() {
        enderecoOrigem = null;
        limparEstadoCampoOrigem();
        atualizarVisibilidadeComponentesCondicionais();
    }

    private void limparEstadoCampoOrigem() {
        definirTextoCampoOrigem("");
        definirDicaCampoOrigem(getString(R.string.rotulo_endereco_origem));
        definirCampoOrigemHabilitado(true);
        definirCampoOrigemFocavel(true);
        definirVisibilidadeBotaoLimparCampoOrigem(false);
    }

    private void realizarBuscaEndereco(String consulta) {
        if (consulta == null || consulta.trim().isEmpty()) {
            atualizarResultadosBusca(new ArrayList<>());
            return;
        }

        limparErroCampoOrigem();
        realizarGeocodificacao(consulta, codigoPaisUsuario,
                this::atualizarResultadosBusca,
                this::tratarErroBusca);
    }

    private void tratarErroBusca(Integer idRecursoErro) {
        atualizarResultadosBusca(new ArrayList<>());
        exibirMensagemErro(idRecursoErro);
    }

    private void realizarGeocodificacao(String consulta, String codigoPais,
                                        Consumer<List<Address>> aoSucesso,
                                        Consumer<Integer> aoErro) {
        if (consulta == null || consulta.trim().isEmpty()) {
            executarNaThreadPrincipal(() -> {
                if (estaFragmentoAtivo()) {
                    aoErro.accept(R.string.erro_campo_vazio);
                }
            });
            return;
        }

        executarEmBackground(() -> {
            try {
                if (geocodificador == null || !estaFragmentoAtivo()) {
                    return;
                }

                List<Address> resultados = geocodificador.getFromLocationName(consulta, MAX_RESULTADOS_BUSCA);
                if (!estaFragmentoAtivo()) {
                    return;
                }

                List<Address> filtrados = filter(
                        resultados != null ? resultados : new ArrayList<>(),
                        codigoPais);

                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo() && getView() != null) {
                        aoSucesso.accept(filtrados);
                    }
                });
            } catch (IOException e) {
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo() && getContext() != null) {
                        aoErro.accept(R.string.erro_servico_localizacao);
                    }
                });
            } catch (Exception e) {
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo() && getContext() != null) {
                        aoErro.accept(R.string.erro_generico);
                    }
                });
            }
        });
    }

    private void realizarGeocodificacaoReversa(double latitude, double longitude,
                                               Consumer<List<Address>> aoSucesso,
                                               Consumer<Integer> aoErro) {
        executarEmBackground(() -> {
            try {
                List<Address> resultados = geocodificador.getFromLocation(latitude, longitude, 1);
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo()) {
                        aoSucesso.accept(resultados != null ? resultados : new ArrayList<>());
                    }
                });
            } catch (IOException e) {
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo()) {
                        aoErro.accept(R.string.erro_servico_localizacao);
                    }
                });
            }
        });
    }

    private void atualizarResultadosBusca(List<Address> resultados) {
        if (resultados == null) {
            resultados = new ArrayList<>();
        }

        final List<Address> resultadosFinais = resultados;

        executarNaThreadPrincipal(() -> {
            if (!estaFragmentoAtivo() || getView() == null) {
                return;
            }

            synchronized (enderecos) {
                enderecos.clear();
                enderecos.addAll(resultadosFinais);
            }

            Optional.ofNullable(adaptadorLocalizacoes)
                    .ifPresent(adaptador -> {
                        try {
                            adaptador.notifyDataSetChanged();
                        } catch (Exception e) {
                            exibirMensagemErro(R.string.erro_update_adapter);
                        }
                    });
        });
    }

    private void iniciarCalculoRota() {
        if (!podeCalcularRota()) return;
        if (!estaCalculandoRota.compareAndSet(false, true)) return;

        try {
            LatLng origem = extrairLatLng(enderecoOrigem);
            LatLng destino = extrairLatLng(enderecoDestino);

            buscarDirecoesRota(origem, destino,
                    this::tratarSucessoCalculoRota,
                    this::tratarErroCalculoRota);
        } catch (IllegalArgumentException e) {
            estaCalculandoRota.set(false);
            exibirMensagemErro(R.string.erro_coordenadas_invalidas);
        }
    }

    private boolean podeCalcularRota() {
        return estaFragmentoAtivo() && possuiEnderecosRotaValidos();
    }

    private boolean possuiEnderecosRotaValidos() {
        return enderecoOrigem != null && enderecoDestino != null;
    }

    private LatLng extrairLatLng(Address endereco) {
        double lat = endereco.getLatitude();
        double lng = endereco.getLongitude();

        if (lat == 0.0 && lng == 0.0) {
            throw new IllegalArgumentException("Invalid coordinates: 0.0, 0.0");
        }

        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordinates out of range");
        }

        return new LatLng(lat, lng);
    }

    private void buscarDirecoesRota(LatLng origem, LatLng destino,
                                    Consumer<Directions> aoSucesso,
                                    Consumer<Integer> aoErro) {
        executarEmBackground(() -> {
            try {
                Context contexto = getContext();
                if (contexto == null) {
                    estaCalculandoRota.set(false);
                    return;
                }

                String url = build(origem, destino, contexto);
                String json = fetch(url);
                parse(json, aoSucesso, aoErro);
            } catch (Exception e) {
                executarNaThreadPrincipal(() -> {
                    if (estaFragmentoAtivo()) {
                        aoErro.accept(R.string.erro_rede_rotas);
                    }
                    estaCalculandoRota.set(false);
                });
            }
        });
    }

    private void tratarSucessoCalculoRota(Directions direcoes) {
        executarNaThreadPrincipal(() -> {
            if (estaFragmentoAtivo()) {
                aplicarDistanciaRota(direcoes);
                configurarCartaoValorFinal();
            }
            estaCalculandoRota.set(false);
        });
    }

    private void aplicarDistanciaRota(Directions direcoes) {
        distancia = analisarValorDistancia(direcoes.getDistance());
    }

    private double analisarValorDistancia(String textoDistancia) {
        try {
            return Double.parseDouble(textoDistancia.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void tratarErroCalculoRota(Integer idRecursoErro) {
        executarNaThreadPrincipal(() -> {
            if (estaFragmentoAtivo()) {
                exibirMensagemErro(idRecursoErro);
            }
            estaCalculandoRota.set(false);
        });
    }

    private void configurarBotoesAjusteDistancia() {
        configurarBotaoDistanciaFixa(botaoAdicionar5Km, 5);
        configurarBotaoDistanciaFixa(botaoAdicionar10Km, 10);
        configurarBotaoDistanciaFixa(botaoAdicionar20Km, 20);
        configurarBotaoDistanciaPersonalizada();
    }

    private void configurarBotaoDistanciaFixa(Button botao, double quilometros) {
        Optional.ofNullable(botao).ifPresent(btn ->
                btn.setOnClickListener(v -> ajustarDistanciaPor(quilometros)));
    }

    private void configurarBotaoDistanciaPersonalizada() {
        Optional.ofNullable(botaoConfirmarAjuste).ifPresent(botao ->
                botao.setOnClickListener(v -> aplicarAjusteDistanciaPersonalizado()));
    }

    @SuppressLint("StringFormatMatches")
    private void ajustarDistanciaPor(double quilometros) {
        if (quilometros <= 0) return;
        distancia += quilometros;
        configurarCartaoValorFinal();
        String mensagem = getString(R.string.sucesso_distancia_atualizada, distancia);
        exibirMensagemSucesso(mensagem);
    }

    private void aplicarAjusteDistanciaPersonalizado() {
        String valorEntrada = extrairValorEditText(campoKmAdicional);

        if (valorEntrada.isEmpty()) {
            exibirMensagemErro(R.string.erro_campo_vazio);
            return;
        }

        try {
            double quilometros = Double.parseDouble(valorEntrada);
            if (quilometros > 0) {
                ajustarDistanciaPor(quilometros);
                configurarCartaoValorFinal();
                limparValorEditText(campoKmAdicional);
            } else {
                exibirMensagemErro(R.string.erro_valor_invalido);
            }
        } catch (NumberFormatException e) {
            exibirMensagemErro(R.string.erro_valor_invalido);
        }
    }

    private void solicitarPermissoesLocalizacao() {
        Context contexto = requireContext();
        request(lancadorPermissoes, contexto);
    }

    private void processarResultadoPermissoes(Map<String, Boolean> resultados) {
        onResult(resultados, this::obterLocalizacaoUsuario, this::exibirMensagemPermissaoNegada);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void obterLocalizacaoUsuario() {
        Context contexto = requireContext();
        if (isGranted(contexto)) {
            clienteLocalizacao.getLastLocation()
                    .addOnSuccessListener(requireActivity(), this::tratarResultadoLocalizacaoUsuario);
        }
    }

    private void tratarResultadoLocalizacaoUsuario(Location localizacao) {
        if (localizacao == null || !estaFragmentoAtivo()) return;

        realizarGeocodificacaoReversa(localizacao.getLatitude(), localizacao.getLongitude(),
                this::extrairPaisUsuarioDaLocalizacao,
                this::exibirMensagemErro);
    }

    private void extrairPaisUsuarioDaLocalizacao(List<Address> enderecos) {
        codigoPaisUsuario = code(first(enderecos));
    }

    private void exibirMensagemPermissaoNegada() {
        if (!estaFragmentoAtivo()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.erro_titulo_permissao_negada)
                .setMessage(R.string.erro_permissao_negada)
                .setPositiveButton(R.string.btn_ok, (dialogo, qual) -> dialogo.dismiss())
                .show();
    }

    private void configurarNavegacaoFragmentoLocalizacao() {
        Optional.ofNullable(botaoLocalizacao).ifPresent(botao ->
                botao.setOnClickListener(v -> navegarParaDetalhesLocalizacao()));
    }

    private void navegarParaDetalhesLocalizacao() {
        if (!podeNavegarParaLocalizacao()) return;

        estaNavegando.set(true);
        LocationFragment fragmento = LocationFragment.newInstance(enderecoOrigem, enderecoDestino);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_view, fragmento)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    private boolean podeNavegarParaLocalizacao() {
        return estaFragmentoAtivo()
                && possuiEnderecosRotaValidos()
                && !estaNavegando.get();
    }

    private void aplicarEnderecoOrigemNaUI(Address endereco) {
        if (!estaFragmentoAtivo()) return;

        definirTextoCampoOrigem(format(endereco));
        definirCampoOrigemHabilitado(false);
        definirCampoOrigemFocavel(false);
        definirVisibilidadeCursorCampoOrigem(false);
        definirVisibilidadeBotaoLimparCampoOrigem(true);
        limparFocoCampoOrigem();
    }

    private void sincronizarUIComEstado() {
        if (!estaFragmentoAtivo()) return;

        if (enderecoOrigem != null) {
            aplicarEnderecoOrigemNaUI(enderecoOrigem);
            atualizarVisibilidadeComponentesCondicionais();
        }

        limparTodosFocosInput();
    }

    private void limparTodosFocosInput() {
        limparFocoCampoOrigem();
        limparFocoCampoDestino();
    }

    private void atualizarVisibilidadeComponentesCondicionais() {
        Integer quantidade = converterInteiroDeInput(campoQuantidadeAnimais);
        boolean temQuantidadeValida = ehQuantidadeValida(quantidade);
        boolean temCategoria = categoriaAtual != null;
        boolean temOrigem = enderecoOrigem != null;
        boolean temEndereco = enderecoOrigem != null && enderecoDestino != null;

        atualizarVisibilidadeCartaoAdicionarLocalizacao(temQuantidadeValida && temCategoria);
        atualizarVisibilidadeComponentesRota(temOrigem && temQuantidadeValida && temCategoria);
        atualizarVisibilidadeCartaoValorFinal(temEndereco);
    }

    private void atualizarVisibilidadeCartaoAdicionarLocalizacao(boolean deveExibir) {
        definirVisibilidadeView(cartaoAdicionarLocalizacao, deveExibir ? View.VISIBLE : View.GONE);
    }

    private void atualizarVisibilidadeComponentesRota(boolean deveExibir) {
        int visibilidade = deveExibir ? View.VISIBLE : View.GONE;
        definirVisibilidadeView(cartaoAjusteKm, visibilidade);
        definirVisibilidadeView(botaoLocalizacao, visibilidade);
    }

    private void atualizarVisibilidadeCartaoValorFinal(boolean deveExibir) {
        int visibilidade = deveExibir ? View.VISIBLE : View.GONE;
        definirVisibilidadeView(cartaoValorFreteFinal, visibilidade);
    }

    private BigDecimal converterDecimalDeInput(TextInputEditText campo) {
        return Optional.ofNullable(campo)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .flatMap(this::analisarDecimalSeguro)
                .orElse(null);
    }

    private Optional<BigDecimal> analisarDecimalSeguro(String valor) {
        try {
            BigDecimal analisado = new BigDecimal(valor);
            return analisado.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(analisado) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Integer converterInteiroDeInput(TextInputEditText campo) {
        return Optional.ofNullable(campo)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .flatMap(this::analisarInteiroSeguro)
                .orElse(null);
    }

    private Optional<Integer> analisarInteiroSeguro(String valor) {
        try {
            int analisado = Integer.parseInt(valor);
            return analisado > 0 ? Optional.of(analisado) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Address> extrairEnderecoDoBundle(Bundle pacote, String chave) {
        return Optional.ofNullable(pacote.getParcelable(chave, Address.class));
    }

    private void notificarEnderecoOrigemSelecionado(Address endereco) {
        iniciarCalculoRota();
        publicarResultadoFragment(RESULTADO_CHAVE_ORIGEM, CHAVE_ORIGEM, endereco);
    }

    private void notificarEnderecoDestinoSelecionado(Address endereco) {
        publicarResultadoFragment(RESULTADO_CHAVE_DESTINO, CHAVE_DESTINO, endereco);
    }

    private void publicarResultadoFragment(String chaveResultado, String chavePacote, Address endereco) {
        Bundle pacote = new Bundle();
        pacote.putParcelable(chavePacote, endereco);
        getParentFragmentManager().setFragmentResult(chaveResultado, pacote);
    }

    @Override
    public List<Address> search(String consulta) {
        try {
            List<Address> resultados = geocodificador.getFromLocationName(consulta, MAX_RESULTADOS_BUSCA);
            return resultados != null ? resultados : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static String formatarParaMoeda(BigDecimal valor) {
        return "R$ " + FORMATADOR_MOEDA.format(valor);
    }

    private void definirEstadoBottomSheet(int estado) {
        Optional.ofNullable(bottomSheet).ifPresent(sheet -> sheet.setState(estado));
    }

    private void expandirBottomSheet() {
        definirEstadoBottomSheet(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private void colapsarBottomSheet() {
        definirEstadoBottomSheet(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void definirTextoCampoOrigem(String texto) {
        Optional.ofNullable(campoOrigem).ifPresent(campo -> campo.setText(texto));
    }

    private void definirDicaCampoOrigem(String dica) {
        Optional.ofNullable(campoOrigem).ifPresent(campo -> campo.setHint(dica));
    }

    private void definirCampoOrigemHabilitado(boolean habilitado) {
        Optional.ofNullable(campoOrigem).ifPresent(campo -> campo.setEnabled(habilitado));
    }

    private void definirCampoOrigemFocavel(boolean focavel) {
        Optional.ofNullable(campoOrigem).ifPresent(campo -> {
            campo.setFocusable(focavel);
            campo.setFocusableInTouchMode(focavel);
        });
    }

    private void definirVisibilidadeCursorCampoOrigem(boolean visivel) {
        Optional.ofNullable(campoOrigem).ifPresent(campo -> campo.setCursorVisible(visivel));
    }

    private void definirVisibilidadeBotaoLimparCampoOrigem(boolean visivel) {
        Optional.ofNullable(layoutCampoOrigem).ifPresent(layout -> layout.setEndIconVisible(visivel));
    }

    private void limparFocoCampoOrigem() {
        Optional.ofNullable(campoOrigem).ifPresent(View::clearFocus);
    }

    private void limparFocoCampoDestino() {
        Optional.ofNullable(campoDestino).ifPresent(View::clearFocus);
    }

    private void limparErroCampoOrigem() {
        Optional.ofNullable(layoutCampoOrigem).ifPresent(layout -> layout.setError(null));
    }

    private void definirValorTextView(TextView campoTexto, String valor) {
        Optional.ofNullable(campoTexto).ifPresent(tv -> tv.setText(valor));
    }

    private void definirVisibilidadeView(View view, int visibilidade) {
        Optional.ofNullable(view).ifPresent(v -> v.setVisibility(visibilidade));
    }

    private String extrairValorEditText(EditText campoEdicao) {
        return Optional.ofNullable(campoEdicao)
                .map(EditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .orElse("");
    }

    private void limparValorEditText(EditText campoEdicao) {
        Optional.ofNullable(campoEdicao).ifPresent(et -> et.setText(""));
    }

    private void exibirMensagemSucesso(String mensagem) {
        exibirSnackbar(mensagem, Color.parseColor("#279958"), Color.WHITE);
    }

    private void exibirMensagemErro(int idRecursoMensagem) {
        if (estaFragmentoAtivo() && getContext() != null) {
            exibirSnackbar(getContext().getString(idRecursoMensagem), Color.RED, Color.WHITE);
        }
    }

    private void exibirSnackbar(String mensagem, int corFundo, int corTexto) {
        if (estaFragmentoAtivo() && getView() != null) {
            Snackbar.make(getView(), mensagem, Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(corFundo)
                    .setTextColor(corTexto)
                    .show();
        }
    }

    private void executarEmBackground(Runnable tarefa) {
        Optional.ofNullable(servicoExecutor)
                .filter(executor -> !executor.isShutdown())
                .ifPresent(executor -> executor.execute(tarefa));
    }

    private void executarNaThreadPrincipal(Runnable acao) {
        if (!estaFragmentoAtivo()) {
            return;
        }

        Optional.ofNullable(manipuladorPrincipal)
                .filter(manipulador -> manipulador.getLooper().getThread().isAlive())
                .ifPresent(manipulador -> {
                    manipulador.post(() -> {
                        if (estaFragmentoAtivo() && getView() != null) {
                            try {
                                acao.run();
                            } catch (Exception e) {
                                onDestroy();
                            }
                        }
                    });
                });
    }

    private boolean estaFragmentoAtivo() {
        return isAdded() && !isRemoving() && !isDetached();
    }

    private void liberarReferenciasViews() {
        campoOrigem = null;
        campoDestino = null;
        layoutCampoOrigem = null;
        cartaoAdicionarLocalizacao = null;
        recyclerViewLocalizacoes = null;
        adaptadorLocalizacoes = null;
        bottomSheet = null;
        botaoLocalizacao = null;
        autoCompleteCategoriaAnimal = null;
        campoPrecoArroba = null;
        campoPesoAnimal = null;
        campoQuantidadeAnimais = null;
        cartaoResultadoBezerro = null;
        textoValorPorCabeca = null;
        textoValorPorKg = null;
        textoValorTotalBezerro = null;
        cartaoAjusteKm = null;
        campoKmAdicional = null;
        botaoAdicionar5Km = null;
        botaoAdicionar10Km = null;
        botaoAdicionar20Km = null;
        botaoConfirmarAjuste = null;
        cartaoRecomendacaoTransporte = null;
        recyclerViewRecomendacoes = null;
        textoMotivoRecomendacao = null;
        adaptadorRecomendacoes = null;
    }

    private void limparRecursos() {
        encerrarServicoExecutor();
        limparCallbacksManipuladorPrincipal();
    }

    private void encerrarServicoExecutor() {
        Optional.ofNullable(servicoExecutor)
                .filter(executor -> !executor.isShutdown())
                .ifPresent(executor -> {
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        executor.shutdownNow();
                    }
                });
    }

    private void limparCallbacksManipuladorPrincipal() {
        Optional.ofNullable(manipuladorPrincipal)
                .ifPresent(manipulador -> manipulador.removeCallbacksAndMessages(null));
    }
}