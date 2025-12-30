package com.botoni.avaliacaodepreco.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
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
import com.botoni.avaliacaodepreco.di.PermissionProvider;
import com.botoni.avaliacaodepreco.domain.entities.Directions;
import com.botoni.avaliacaodepreco.domain.entities.Recomendacao;
import com.botoni.avaliacaodepreco.ui.adapter.CategoriaAdapter;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
import com.botoni.avaliacaodepreco.ui.adapter.RecomendacaoAdapter;
import com.botoni.avaliacaodepreco.ui.views.InputWatchers;
import com.botoni.avaliacaodepreco.ui.views.SearchWatcher;
import com.botoni.avaliacaodepreco.utils.PdfReport;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
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
public class MainFragment extends Fragment implements DirectionsProvider, AddressProvider {

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
    private static final String ESTADO_PERCENTUAL_AGIO = "inputPercentualAgio";

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

    private TextInputEditText inputOrigem;
    private TextInputEditText inputDestino;
    private TextInputLayout layoutOrigem;
    private MaterialCardView cardAddLocalizacao;
    private RecyclerView recyclerLocalizacoes;
    private LocationAdapter adaptadorLocalizacoes;
    private BottomSheetBehavior<FrameLayout> bottomSheetLocalizacao;
    private Button btnLocalizacao;

    private AutoCompleteTextView autoCompleteCategoria;
    private TextInputEditText inputPrecoArroba;
    private TextInputEditText inputPercentualAgio;
    private TextInputEditText inputPesoAnimal;
    private TextInputEditText inputQuantidadeAnimais;
    private CardView cardResultadoBezerro;
    private TextView textValorPorCabeca;
    private TextView textValorPorKg;
    private TextView textValorTotalBezerro;

    private CardView cardAjusteKm;
    private EditText inputKmAdicional;
    private Button btnAdd5Km;
    private Button btnAdd10Km;
    private Button btnAdd20Km;
    private Button btnConfirmarAjuste;

    private CardView cardRecomendacaoTransporte;
    private RecyclerView recyclerRecomendacoes;
    private TextView textMotivoRecomendacao;
    private RecomendacaoAdapter adaptadorRecomendacoes;

    private CardView cardContainerRota;
    private LinearLayout containerRota;
    private CardView cardOrigemRota;
    private CardView cardDestinoRota;
    private View cardDistanciaInside;
    private View cardDistanciaOutside;
    private TextView textLabelOrigem;
    private TextView textValorOrigem;
    private TextView textLabelDestino;
    private TextView textValorDestino;
    private TextView textValorDistanciaInside;
    private TextView textValorDistanciaOutside;
    private TextView textValorFrete;
    private TextView textValorTotalFinal;

    private CardView cardValorFinal;
    private TextView textValorTotal;
    private TextView textValorFinalPorKg;

    private BottomSheetBehavior<FrameLayout> bottomSheetCompartilhamento;
    private ImageButton whatsapp;
    private ImageButton telegram;
    private ImageButton googleDrive;

    private Button buttonEnviarDoc;

    private File pdfFileAtual = null;

    private final PermissionProvider locationPermissionProvider = () -> new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private final PermissionProvider storagePermissionProvider = () -> new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
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
        vincularViewsBottonSheetToShare(raiz);
    }

    private void vincularViewsLocalizacao(View raiz) {
        inputOrigem = raiz.findViewById(R.id.origem_input);
        inputDestino = raiz.findViewById(R.id.destino_input);
        layoutOrigem = raiz.findViewById(R.id.origem_input_layout);
        cardAddLocalizacao = raiz.findViewById(R.id.adicionar_localizacao_card);
        recyclerLocalizacoes = raiz.findViewById(R.id.localizacoes_recycler_view);
        btnLocalizacao = raiz.findViewById(R.id.button_fragment_location);
    }

    private void vincularViewsCalculo(View raiz) {
        autoCompleteCategoria = raiz.findViewById(R.id.auto_complete_text_view_categoria_animal);
        inputPrecoArroba = raiz.findViewById(R.id.text_input_edit_text_preco_arroba);
        inputPesoAnimal = raiz.findViewById(R.id.text_input_edit_text_peso_animal);
        inputQuantidadeAnimais = raiz.findViewById(R.id.text_input_edit_text_quantidade_animais);
        inputPercentualAgio = raiz.findViewById(R.id.text_input_edit_text_percentual_agio);
        cardResultadoBezerro = raiz.findViewById(R.id.material_card_view_resultado);
        textValorPorCabeca = raiz.findViewById(R.id.text_view_valor_por_cabeca);
        textValorPorKg = raiz.findViewById(R.id.text_view_valor_por_kg);
        textValorTotalBezerro = raiz.findViewById(R.id.text_view_valor_total);
    }

    private void vincularViewsRecomendacao(View raiz) {
        cardRecomendacaoTransporte = raiz.findViewById(R.id.card_recomendacao_transporte);
        recyclerRecomendacoes = raiz.findViewById(R.id.rv_recomendacoes_transporte);
        textMotivoRecomendacao = raiz.findViewById(R.id.tv_motivo_recomendacao);
    }

    private void vincularViewsAjusteDistancia(View raiz) {
        cardAjusteKm = raiz.findViewById(R.id.material_card_view_ajuste_km);
        inputKmAdicional = raiz.findViewById(R.id.text_input_edit_text_km_adicional);
        btnAdd5Km = raiz.findViewById(R.id.adicionar_5km_button);
        btnAdd10Km = raiz.findViewById(R.id.adicionar_10km_button);
        btnAdd20Km = raiz.findViewById(R.id.local_partida_adicionar_20km_button);
        btnConfirmarAjuste = raiz.findViewById(R.id.confirmar_ajuste_button);
    }

    private void vincularViewsValorFreteFinal(View raiz) {
        cardContainerRota = raiz.findViewById(R.id.material_card_view_container_rota);
        containerRota = raiz.findViewById(R.id.linear_layout_container_rota_cards);
        cardOrigemRota = raiz.findViewById(R.id.material_card_view_origem);
        cardDestinoRota = raiz.findViewById(R.id.material_card_view_destino);

        cardDistanciaInside = raiz.findViewById(R.id.card_distance_inside);
        cardDistanciaOutside = raiz.findViewById(R.id.card_distance_outside);

        cardValorFinal = raiz.findViewById(R.id.material_card_view_valor_final);
        textLabelOrigem = raiz.findViewById(R.id.text_view_label_origem);
        textValorOrigem = raiz.findViewById(R.id.text_view_valor_origem);
        textLabelDestino = raiz.findViewById(R.id.text_view_label_destino);
        textValorDestino = raiz.findViewById(R.id.text_view_valor_destino);


        if (cardDistanciaInside != null) {
            textValorDistanciaInside = cardDistanciaInside.findViewById(R.id.text_view_valor_distancia);
        }
        if (cardDistanciaOutside != null) {
            textValorDistanciaOutside = cardDistanciaOutside.findViewById(R.id.text_view_valor_distancia);
        }

        textValorTotal = raiz.findViewById(R.id.text_view_valor_total_final);
        textValorFrete = raiz.findViewById(R.id.text_view_valor_frete_final);
        textValorTotalFinal = raiz.findViewById(R.id.text_view_valor_total_com_frete);
        textValorFinalPorKg = raiz.findViewById(R.id.text_view_valor_final_por_kg);
    }

    private void vincularViewsBottonSheetToShare(View view) {
        whatsapp = view.findViewById(R.id.button_whatsapp);
        telegram = view.findViewById(R.id.button_telegram);
        googleDrive = view.findViewById(R.id.buton_google_drive);
        buttonEnviarDoc = view.findViewById(R.id.material_button_enviar);
        setOnClickListenerSendDoc(whatsapp, "com.whatsapp");
        setOnClickListenerSendDoc(telegram, "org.telegram.messenger");
        setOnClickListenerSendDoc(googleDrive, "com.google.android.apps.docs");
        configurarBottomSheetCompartilhamento(view);
        definirVisibilidadeView(buttonEnviarDoc, View.GONE);
    }

    private void configurarTodosComponentesUI(View view) {
        if (!estaFragmentoAtivo()) return;
        solicitarPermissoesLocalizacao();
        configurarComponentesCalculo();
        configurarComponentesLocalizacao(view);
        configurarComponentesNavegacao();
        configurarCartaoValorFinal();
    }

    private void configurarBottomSheetCompartilhamento(View view) {
        FrameLayout bottomSheet = view.findViewById(R.id.share_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheetCompartilhamento = BottomSheetBehavior.from(bottomSheet);
            bottomSheetCompartilhamento.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheetCompartilhamento.setHideable(true);
            bottomSheetCompartilhamento.setPeekHeight(0);

            Optional.ofNullable(buttonEnviarDoc).ifPresent(botao ->
                    botao.setOnClickListener(v -> {
                        if (gerarPdfComDadosReais()) {
                            expandirBottomSheetCompartilhamento();
                        } else {
                            exibirMensagemErro(R.string.erro_gerar_relatorio);
                        }
                    })
            );
        }
    }

    private void setOnClickListenerSendDoc(ImageButton button, String pack) {
        button.setOnClickListener(v -> {
            if (pdfFileAtual == null || !pdfFileAtual.exists()) {
                if (!gerarPdfComDadosReais()) {
                    exibirMensagemErro(R.string.erro_gerar_relatorio);
                    return;
                }
            }

            try {
                Uri pdfUri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.botoni.avaliacaodepreco.provider",
                        pdfFileAtual
                );

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, pdfUri);
                intent.putExtra(Intent.EXTRA_TEXT, "Aqui está o relatório de avaliação de preço 📄");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(Intent.createChooser(intent, "Compartilhar PDF"));
            } catch (IllegalArgumentException e) {
                exibirMensagemErro(R.string.erro_gerar_relatorio);
            } catch (ActivityNotFoundException e) {
                exibirMensagemErro(R.string.erro_app_nao_instalado);
            }
        });
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
                        atualizarVisibilidadeComponentesCondicionais();
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
        salvarValorCampoTexto(estadoSaida, inputPrecoArroba, ESTADO_PRECO_ARROBA);
        salvarValorCampoTexto(estadoSaida, inputPesoAnimal, ESTADO_PESO_ANIMAL);
        salvarValorCampoTexto(estadoSaida, inputQuantidadeAnimais, ESTADO_QUANTIDADE_ANIMAL);
        salvarValorCampoTexto(estadoSaida, inputPercentualAgio, ESTADO_PERCENTUAL_AGIO);
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
        restaurarValorCampoTexto(estadoSalvo, inputPrecoArroba, ESTADO_PRECO_ARROBA);
        restaurarValorCampoTexto(estadoSalvo, inputPesoAnimal, ESTADO_PESO_ANIMAL);
        restaurarValorCampoTexto(estadoSalvo, inputQuantidadeAnimais, ESTADO_QUANTIDADE_ANIMAL);
        restaurarValorCampoTexto(estadoSalvo, inputPercentualAgio, ESTADO_PERCENTUAL_AGIO);
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
                    Optional.ofNullable(autoCompleteCategoria)
                            .ifPresent(autoComplete -> autoComplete.setText(categoria.getDescricao(), false));
                    atualizarVisibilidadeComponentesCondicionais();
                    atualizarRecomendacoesVeiculos();
                });
    }

    private void configurarSelecaoCategoria() {
        Optional.ofNullable(autoCompleteCategoria).ifPresent(autoComplete ->
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
        Optional.ofNullable(autoCompleteCategoria)
                .ifPresent(autoComplete -> autoComplete.setText(descricao, false));
    }

    private void aplicarAdaptadorCategorias() {
        if (autoCompleteCategoria != null && !categorias.isEmpty()) {
            CategoriaAdapter adaptador = new CategoriaAdapter(requireContext(), categorias);
            autoCompleteCategoria.setAdapter(adaptador);
        }
    }

    private void anexarOuvintesCalculo() {
        anexarOuvinteInput(inputPrecoArroba, this::realizarCalculoBezerro);
        anexarOuvinteInput(inputPesoAnimal, this::realizarCalculoBezerro);
        anexarOuvinteInput(inputQuantidadeAnimais, this::processarMudancaQuantidade);
        anexarOuvinteInput(inputPercentualAgio, this::realizarCalculoBezerro);

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
        BigDecimal precoArroba = converterDecimalDeInput(inputPrecoArroba);
        BigDecimal pesoAnimal = converterDecimalDeInput(inputPesoAnimal);
        Integer quantidade = converterInteiroDeInput(inputQuantidadeAnimais);

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
        BigDecimal valorPorCabeca = calcularValorTotalBezerro(pesoAnimal, precoArroba, converterDecimalDeInput(inputPercentualAgio));
        BigDecimal valorPorKg = calcularValorTotalPorKg(pesoAnimal, precoArroba, converterDecimalDeInput(inputPercentualAgio));
        BigDecimal valorTotal = calcularValorTotalTodosBezerros(valorPorCabeca, quantidade);

        atualizarTextosResultadoCalculo(valorPorCabeca, valorPorKg, valorTotal);
        exibirResultadoCalculo();
        configurarCartaoValorFinal();
    }

    private void atualizarTextosResultadoCalculo(BigDecimal valorCabeca, BigDecimal valorKg, BigDecimal valorTotal) {
        definirValorTextView(textValorPorCabeca, formatarParaMoeda(valorCabeca));
        definirValorTextView(textValorPorKg, formatarParaMoeda(valorKg));
        definirValorTextView(textValorTotalBezerro, formatarParaMoeda(valorTotal));
    }

    private void exibirResultadoCalculo() {
        definirVisibilidadeView(cardResultadoBezerro, View.VISIBLE);
    }

    private void esconderResultadoCalculo() {
        definirVisibilidadeView(cardResultadoBezerro, View.GONE);
    }

    private static BigDecimal calcularValorTotalTodosBezerros(BigDecimal valor, int quantidade) {
        return valor.multiply(new BigDecimal(quantidade)).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, inputPercentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorTotalPorKg(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorTotal = calcularValorTotalBezerro(pesoKg, precoPorArroba, inputPercentualAgio);
        return valorTotal.divide(pesoKg, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private static BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        if (estaNoOuAcimaPesoBase(pesoKg)) {
            return calcularAgioAcimaPesoBase(pesoKg, precoPorArroba, inputPercentualAgio);
        }
        return calcularAgioAbaixoPesoBase(pesoKg, precoPorArroba, inputPercentualAgio);
    }

    private static BigDecimal calcularAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(pesoKg, precoPorArroba, inputPercentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioNoPesoBase(precoPorArroba, inputPercentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private static BigDecimal calcularAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaAgioNoPeso(pesoAtual, precoPorArroba, inputPercentualAgio));
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        return acumulado.add(calcularAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, inputPercentualAgio));
    }

    private static BigDecimal calcularDiferencaAgioNoPeso(BigDecimal pesoKg, BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioNoPesoBase(precoPorArroba, inputPercentualAgio);
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

    private static BigDecimal obterValorReferenciaAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaExatamenteNoPesoBase(precoPorArroba, inputPercentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaExatamenteNoPesoBase(BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorAgioTotal = calcularValorAgioNoPesoBase(precoPorArroba, inputPercentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return valorAgioTotal.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal valorComAgio = calcularValorPesoBaseComAgio(precoPorArroba, inputPercentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private static BigDecimal calcularValorPesoBaseComAgio(BigDecimal precoPorArroba, BigDecimal inputPercentualAgio) {
        BigDecimal arrobasBase = converterKgParaArrobas(PESO_BASE_KG);
        BigDecimal fatorMultiplicador = obterFatorMultiplicadorAgio(inputPercentualAgio);
        return arrobasBase.multiply(precoPorArroba).divide(fatorMultiplicador, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal obterFatorMultiplicadorAgio(BigDecimal inputPercentualAgio) {
        return CEM.subtract(inputPercentualAgio).divide(CEM, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
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

        Integer quantidade = converterInteiroDeInput(inputQuantidadeAnimais);
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
        Optional.ofNullable(recyclerRecomendacoes).ifPresent(rv -> {
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

        definirValorTextView(textMotivoRecomendacao, mensagemResumo);
    }

    private String formatarResumoRecomendacao(int quantidade, String categoria, int veiculos) {
        return String.format(Locale.getDefault(),
                getString(R.string.info_ecomendacao_transporte_msg),
                quantidade, categoria, veiculos);
    }

    private void exibirMensagemRecomendacoesVazia(int quantidade) {
        String descricaoCategoria = extrairDescricaoCategoria().toLowerCase();
        String mensagem = formatarMensagemRecomendacoesVazia(quantidade, descricaoCategoria);

        definirValorTextView(textMotivoRecomendacao, mensagem);
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
        Optional.ofNullable(recyclerRecomendacoes).ifPresent(rv ->
                rv.setAdapter(new RecomendacaoAdapter(new ArrayList<>())));
    }

    private void exibirExibicaoRecomendacoes() {
        definirVisibilidadeView(cardRecomendacaoTransporte, View.VISIBLE);
    }

    private void esconderExibicaoRecomendacoes() {
        definirVisibilidadeView(cardRecomendacaoTransporte, View.GONE);
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
                        if (estaFragmentoAtivo() && bottomSheetLocalizacao != null) {
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
            bottomSheetLocalizacao = BottomSheetBehavior.from(container);
            definirEstadoBottomSheet(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void configurarRecyclerViewLocalizacoes() {
        Optional.ofNullable(recyclerLocalizacoes).ifPresent(rv -> {
            adaptadorLocalizacoes = new LocationAdapter(enderecos, this::processarSelecaoEndereco);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(adaptadorLocalizacoes);
        });
    }

    private void configurarRecyclerViewRecomendacoes() {
        Optional.ofNullable(recyclerRecomendacoes).ifPresent(rv -> {
            adaptadorRecomendacoes = new RecomendacaoAdapter(new ArrayList<>());
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(adaptadorRecomendacoes);
        });
    }

    private void configurarCliqueCartaoAdicionarLocalizacao() {
        Optional.ofNullable(cardAddLocalizacao).ifPresent(cartao ->
                cartao.setOnClickListener(v -> expandirBottomSheet()));
    }

    private void configurarComportamentoCampoOrigem() {
        if (inputOrigem == null || layoutOrigem == null) return;

        configurarTratamentoFocoCampoOrigem();
        configurarBuscaCampoOrigem();
        configurarBotaoLimparCampoOrigem();
    }

    private void configurarTratamentoFocoCampoOrigem() {
        Optional.ofNullable(inputOrigem).ifPresent(campo ->
                campo.setOnFocusChangeListener((v, temFoco) -> processarMudancaFocoCampoOrigem(temFoco)));
    }

    private void processarMudancaFocoCampoOrigem(boolean temFoco) {
        definirEstadoBottomSheet(temFoco ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN);
        definirVisibilidadeCursorCampoOrigem(temFoco);
    }

    @SuppressLint("PrivateResource")
    private void configurarBotaoLimparCampoOrigem() {
        Optional.ofNullable(layoutOrigem).ifPresent(layout -> {
            layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            layout.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
            definirVisibilidadeBotaoLimparCampoOrigem(false);
            layout.setEndIconOnClickListener(v -> resetarEnderecoOrigem());
        });
    }

    private void configurarBuscaCampoOrigem() {
        Optional.ofNullable(inputOrigem).ifPresent(campo ->
                campo.addTextChangedListener(new SearchWatcher(this::realizarBuscaEndereco)));
    }

    private void configurarComportamentoCampoDestino() {
        Optional.ofNullable(inputDestino).ifPresent(campo -> {
            campo.setHint(LOCALIZACAO_PADRAO);
            campo.setEnabled(false);
            campo.setCursorVisible(false);
        });
    }

    private void configurarCartaoValorFinal() {
        boolean temOrigem = enderecoOrigem != null;
        boolean temDestino = enderecoDestino != null;
        boolean temRotaCompleta = temOrigem && temDestino;
        boolean temDistancia = distancia > 0;
        boolean temRecomendacoes = !recomendacoes.isEmpty();

        definirVisibilidadeView(cardOrigemRota, temOrigem ? View.VISIBLE : View.GONE);
        definirVisibilidadeView(cardDestinoRota, temDestino ? View.VISIBLE : View.GONE);
        definirVisibilidadeView(containerRota, temRotaCompleta ? View.VISIBLE : View.GONE);

        definirVisibilidadeView(cardContainerRota, temRotaCompleta ? View.VISIBLE : View.GONE);

        if (temRotaCompleta && temDistancia) {
            atualizarTextoDistancia(textValorDistanciaInside, distancia);
            definirVisibilidadeView(cardDistanciaInside, View.VISIBLE);
        } else {
            definirVisibilidadeView(cardDistanciaInside, View.GONE);
        }

        if (temDistancia && !temRotaCompleta) {
            atualizarTextoDistancia(textValorDistanciaOutside, distancia);
            definirVisibilidadeView(cardDistanciaOutside, View.VISIBLE);
        } else {
            definirVisibilidadeView(cardDistanciaOutside, View.GONE);
        }

        if (temOrigem) {
            processarEnderecoCartaoFinal(enderecoOrigem, textValorOrigem);
        }
        if (temDestino) {
            processarEnderecoCartaoFinal(enderecoDestino, textValorDestino);
        }

        if (temDistancia && temRecomendacoes) {
            BigDecimal valorFrete = calcularValorFreteTotal();
            BigDecimal valorTotalBezerro = obterValorTotalBezerro();
            BigDecimal valorTotalComFrete = valorTotalBezerro.add(valorFrete);

            definirValorTextView(textValorFrete, formatarParaMoeda(valorFrete));
            definirValorTextView(textValorTotal, formatarParaMoeda(valorTotalBezerro));
            definirValorTextView(textValorTotalFinal, formatarParaMoeda(valorTotalComFrete));

            calcularValorFinalPorKg(valorTotalComFrete,
                    converterInteiroDeInput(inputQuantidadeAnimais),
                    converterDecimalDeInput(inputPesoAnimal),
                    textValorFinalPorKg);

            definirVisibilidadeView(cardValorFinal, View.VISIBLE);
            definirVisibilidadeView(buttonEnviarDoc, View.VISIBLE);
        } else {
            definirVisibilidadeView(cardValorFinal, View.GONE);
            definirVisibilidadeView(buttonEnviarDoc, View.GONE);
        }
    }

    private void atualizarTextoDistancia(TextView textView, double distancia) {
        if (textView != null) {
            String texto = String.format(Locale.getDefault(), "%.2f km", distancia);
            textView.setText(texto);
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
        String textoTotal = Optional.ofNullable(textValorTotalBezerro)
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
        distancia = 0;
        limparEstadoCampoOrigem();
        atualizarVisibilidadeComponentesCondicionais();
        configurarCartaoValorFinal();
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
        configurarBotaoDistanciaFixa(btnAdd5Km, 5);
        configurarBotaoDistanciaFixa(btnAdd10Km, 10);
        configurarBotaoDistanciaFixa(btnAdd20Km, 20);
        configurarBotaoDistanciaPersonalizada();
    }

    private void configurarBotaoDistanciaFixa(Button botao, double quilometros) {
        Optional.ofNullable(botao).ifPresent(btn ->
                btn.setOnClickListener(v -> ajustarDistanciaPor(quilometros)));
    }

    private void configurarBotaoDistanciaPersonalizada() {
        Optional.ofNullable(btnConfirmarAjuste).ifPresent(botao ->
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
        String valorEntrada = extrairValorEditText(inputKmAdicional);

        if (valorEntrada.isEmpty()) {
            exibirMensagemErro(R.string.erro_campo_vazio);
            return;
        }

        try {
            double quilometros = Double.parseDouble(valorEntrada);
            if (quilometros > 0) {
                ajustarDistanciaPor(quilometros);
                configurarCartaoValorFinal();
                limparValorEditText(inputKmAdicional);
            } else {
                exibirMensagemErro(R.string.erro_valor_invalido);
            }
        } catch (NumberFormatException e) {
            exibirMensagemErro(R.string.erro_valor_invalido);
        }
    }

    private void solicitarPermissoesLocalizacao() {
        locationPermissionProvider.request(permissions, requireContext());
    }

    private void processarResultadoPermissoes(Map<String, Boolean> resultados) {
        locationPermissionProvider.onResult(
                resultados,
                true,
                this::obterLocalizacaoUsuario,
                this::exibirMensagemPermissaoNegada
        );

        storagePermissionProvider.onResult(
                resultados,
                true,
                () -> {},
                this::exibirMensagemPermissaoNegada
        );
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void obterLocalizacaoUsuario() {
        if (locationPermissionProvider.isGranted(requireContext())) {
            clienteLocalizacao.getLastLocation()
                    .addOnSuccessListener(requireActivity(), this::tratarResultadoLocalizacaoUsuario);
        }
    }


    private boolean gerarPdfComDadosReais() {
        try {
            String categoria = Optional.ofNullable(categoriaAtual)
                    .map(CategoriaFrete::getDescricao)
                    .orElse("Não informado");

            BigDecimal precoArroba = converterDecimalDeInput(inputPrecoArroba);
            BigDecimal percentualAgio = converterDecimalDeInput(inputPercentualAgio);
            BigDecimal pesoAnimal = converterDecimalDeInput(inputPesoAnimal);
            Integer quantidade = converterInteiroDeInput(inputQuantidadeAnimais);

            if (precoArroba == null || pesoAnimal == null || quantidade == null) {
                return false;
            }

            BigDecimal valorPorCabeca = calcularValorTotalBezerro(pesoAnimal, precoArroba, percentualAgio);
            BigDecimal valorPorKg = calcularValorTotalPorKg(pesoAnimal, precoArroba, percentualAgio);
            BigDecimal valorTotal = calcularValorTotalTodosBezerros(valorPorCabeca, quantidade);
            BigDecimal valorFrete = calcularValorFreteTotal();
            BigDecimal valorFinalTotal = valorTotal.add(valorFrete);

            BigDecimal pesoTotal = pesoAnimal.multiply(new BigDecimal(quantidade));
            BigDecimal valorFinalPorKg = valorFinalTotal.divide(pesoTotal, ESCALA_RESULTADO, MODO_ARREDONDAMENTO);

            String origem = Optional.ofNullable(enderecoOrigem)
                    .map(this::format)
                    .orElse("Não informado");

            String destino = Optional.ofNullable(enderecoDestino)
                    .map(this::format)
                    .orElse("Não informado");

            PdfReport report = PdfReport.builder()
                    .categoriaAnimal(categoria)
                    .precoArroba(precoArroba.doubleValue())
                    .percentualAgio(percentualAgio != null ? percentualAgio.doubleValue() : 0)
                    .pesoAnimal(pesoAnimal.doubleValue())
                    .quantidadeAnimais(quantidade)
                    .valorPorCabeca(valorPorCabeca.doubleValue())
                    .valorPorKg(valorPorKg.doubleValue())
                    .valorTotal(valorTotal.doubleValue())
                    .origem(origem)
                    .destino(destino)
                    .distancia(distancia)
                    .valorFrete(valorFrete.doubleValue())
                    .valorFinalTotal(valorFinalTotal.doubleValue())
                    .valorFinalPorKg(valorFinalPorKg.doubleValue())
                    .build();

            this.pdfFileAtual = report.create(requireContext());
            return true;
        } catch (Exception e) {
            this.pdfFileAtual = null;
            return false;
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
        Optional.ofNullable(btnLocalizacao).ifPresent(botao ->
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
        Integer quantidade = converterInteiroDeInput(inputQuantidadeAnimais);
        boolean temQuantidadeValida = ehQuantidadeValida(quantidade);
        boolean temCategoria = categoriaAtual != null;
        boolean temEndereco = enderecoOrigem != null && enderecoDestino != null;

        boolean podeExibirComponentesRota = temQuantidadeValida && temCategoria;
        atualizarVisibilidadeCartaoAdicionarLocalizacao(podeExibirComponentesRota);
        definirVisibilidadeView(cardAjusteKm, podeExibirComponentesRota ? View.VISIBLE : View.GONE);
        definirVisibilidadeView(btnLocalizacao, temEndereco ? View.VISIBLE : View.GONE);
        boolean podeCalcularValorFinal = (temEndereco || distancia > 0);
        atualizarVisibilidadeCartaoValorFinal(podeCalcularValorFinal);
    }

    private void atualizarVisibilidadeCartaoAdicionarLocalizacao(boolean deveExibir) {
        if (cardAddLocalizacao == null && getView() != null) {
            cardAddLocalizacao = getView().findViewById(R.id.adicionar_localizacao_card);
            if (cardAddLocalizacao != null) {
                configurarCliqueCartaoAdicionarLocalizacao();
            }
        }

        if (cardAddLocalizacao == null) {
            return;
        }
        definirVisibilidadeView(cardAddLocalizacao, deveExibir ? View.VISIBLE : View.GONE);
    }


    private void atualizarVisibilidadeCartaoValorFinal(boolean deveExibir) {
        int visibilidade = deveExibir ? View.VISIBLE : View.GONE;
        definirVisibilidadeView(cardContainerRota, visibilidade);
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
        Optional.ofNullable(bottomSheetLocalizacao).ifPresent(sheet -> sheet.setState(estado));
    }

    private void expandirBottomSheetCompartilhamento() {
        if (bottomSheetCompartilhamento != null) {
            bottomSheetCompartilhamento.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void expandirBottomSheet() {
        definirEstadoBottomSheet(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private void colapsarBottomSheet() {
        definirEstadoBottomSheet(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void definirTextoCampoOrigem(String texto) {
        Optional.ofNullable(inputOrigem).ifPresent(campo -> campo.setText(texto));
    }

    private void definirDicaCampoOrigem(String dica) {
        Optional.ofNullable(inputOrigem).ifPresent(campo -> campo.setHint(dica));
    }

    private void definirCampoOrigemHabilitado(boolean habilitado) {
        Optional.ofNullable(inputOrigem).ifPresent(campo -> campo.setEnabled(habilitado));
    }

    private void definirCampoOrigemFocavel(boolean focavel) {
        Optional.ofNullable(inputOrigem).ifPresent(campo -> {
            campo.setFocusable(focavel);
            campo.setFocusableInTouchMode(focavel);
        });
    }

    private void definirVisibilidadeCursorCampoOrigem(boolean visivel) {
        Optional.ofNullable(inputOrigem).ifPresent(campo -> campo.setCursorVisible(visivel));
    }

    private void definirVisibilidadeBotaoLimparCampoOrigem(boolean visivel) {
        Optional.ofNullable(layoutOrigem).ifPresent(layout -> layout.setEndIconVisible(visivel));
    }

    private void limparFocoCampoOrigem() {
        Optional.ofNullable(inputOrigem).ifPresent(View::clearFocus);
    }

    private void limparFocoCampoDestino() {
        Optional.ofNullable(inputDestino).ifPresent(View::clearFocus);
    }

    private void limparErroCampoOrigem() {
        Optional.ofNullable(layoutOrigem).ifPresent(layout -> layout.setError(null));
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
        adaptadorLocalizacoes = null;
        adaptadorRecomendacoes = null;

        bottomSheetLocalizacao = null;
        bottomSheetCompartilhamento = null;

        recyclerLocalizacoes = null;
        recyclerRecomendacoes = null;

        inputOrigem = null;
        inputDestino = null;
        layoutOrigem = null;
        cardAddLocalizacao = null;
        btnLocalizacao = null;

        autoCompleteCategoria = null;
        inputPrecoArroba = null;
        inputPesoAnimal = null;
        inputQuantidadeAnimais = null;
        inputPercentualAgio = null;
        cardResultadoBezerro = null;
        textValorPorCabeca = null;
        textValorPorKg = null;
        textValorTotalBezerro = null;

        cardRecomendacaoTransporte = null;
        textMotivoRecomendacao = null;

        cardAjusteKm = null;
        inputKmAdicional = null;
        btnAdd5Km = null;
        btnAdd10Km = null;
        btnAdd20Km = null;
        btnConfirmarAjuste = null;

        cardContainerRota = null;
        containerRota = null;
        cardOrigemRota = null;
        cardDestinoRota = null;
        cardDistanciaInside = null;
        cardDistanciaOutside = null;
        textLabelOrigem = null;
        textValorOrigem = null;
        textLabelDestino = null;
        textValorDestino = null;
        textValorDistanciaInside = null;
        textValorDistanciaOutside = null;
        textValorFrete = null;
        textValorTotalFinal = null;
        cardValorFinal = null;
        textValorTotal = null;
        textValorFinalPorKg = null;

        buttonEnviarDoc = null;
        whatsapp = null;
        telegram = null;
        googleDrive = null;
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