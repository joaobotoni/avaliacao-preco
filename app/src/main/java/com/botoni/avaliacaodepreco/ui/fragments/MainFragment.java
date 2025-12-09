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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.annotation.RequiresPermission;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.data.database.AppDatabase;
import com.botoni.avaliacaodepreco.data.entities.CapacidadeFrete;
import com.botoni.avaliacaodepreco.data.entities.CategoriaFrete;
import com.botoni.avaliacaodepreco.data.entities.TipoVeiculoFrete;
import com.botoni.avaliacaodepreco.di.AddressProvider;
import com.botoni.avaliacaodepreco.di.DirectionsProvider;
import com.botoni.avaliacaodepreco.di.LocationPermissionProvider;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainFragment extends Fragment implements
        DirectionsProvider, LocationPermissionProvider, AddressProvider {
    private static final String DEFAULT_LOCATION = "Cuiabá";
    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_SEARCH_RESULTS = 10;
    /**
     *
     **/
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private static final String STATE_ORIGIN = "newOrigin";
    private static final String STATE_DESTINATION = "newDestination";
    /**
     *
     **/
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
    private ExecutorService executorService;
    /**
     *
     **/
    private Geocoder geocoder;
    private FusedLocationProviderClient locationClient;
    private FragmentManager manager;
    private Address originAddress;
    private Address destinationAddress;
    private String userCountryCode;
    private volatile double distance;
    private volatile boolean isNavigating = false;
    private final List<Address> addresses = new ArrayList<>();
    private TextInputEditText originInput;
    private TextInputEditText destinationInput;
    private TextInputLayout originInputLayout;
    private MaterialCardView addLocationCard;
    private RecyclerView locationRecyclerView;
    private LocationAdapter locationAdapter;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private Button buttonLocation;
    /**
     *
     **/
    private AutoCompleteTextView categoriaAnimalAutoComplete;
    private TextInputEditText precoArrobaInput;
    private TextInputEditText pesoAnimalInput;
    private TextInputEditText quantidadeAnimaisInput;
    private CardView resultadoCardBezerro;
    private TextView valorPorCabecaText;
    private TextView valorPorKgText;
    private TextView valorTotalBezerrosText;
    private CardView ajusteCardView;
    private EditText kmAdicionalInput;
    private Button adicionar5KmButton;
    private Button adicionar10KmButton;
    private Button adicionar20KmButton;
    private Button confirmAjuste;

    /**
     *
     **/
    private CardView cardRecomendacaoTransporte;
    private RecyclerView listaRecomendacoes;
    private List<TipoVeiculoFrete> tiposVeiculo = new ArrayList<>();
    private List<CategoriaFrete> categorias = new ArrayList<>();
    private List<CapacidadeFrete> capacidadesFrete = new ArrayList<>();
    private CategoriaFrete categoriaAtual;

    /**
     *
     **/
    private AppDatabase database;

    /**
     *
     **/
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::onPermissionsResult
    );


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDependencies();
        setupFragmentResultListeners();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupUI();
        restoreState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUIAfterReturn();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isNavigating = false;
        clearViewReferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveAddressState(outState);
    }

    @Override
    public List<Address> search(String query) {
        try {
            List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
            return results != null ? results : emptyList();
        } catch (IOException e) {
            return emptyList();
        }
    }

    @Override
    public String load(@NonNull Context context) {
        return DirectionsProvider.super.load(context);
    }

    @Override
    public String build(LatLng origin, LatLng destination, Context context) {
        return DirectionsProvider.super.build(origin, destination, context);
    }

    @Override
    public String fetch(String curl) throws IOException {
        return DirectionsProvider.super.fetch(curl);
    }

    @Override
    public List<LatLng> decode(String code) {
        return DirectionsProvider.super.decode(code);
    }

    @Override
    public void parse(@NonNull String json, @NonNull Consumer<Directions> success,
                      @NonNull Consumer<Integer> failure) {
        DirectionsProvider.super.parse(json, success, failure);
    }

    private void initializeDependencies() {
        database = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Context context = getContext();
        if (context != null) {
            locationClient = LocationServices.getFusedLocationProviderClient(context);
            geocoder = new Geocoder(context, new Locale("pt", "BR"));
            load(context);
        }
    }

    private void initializeViews(View root) {
        initializeLocationViews(root);
        initializeBezerroViews(root);
        initializeViewsRecomendacao(root);
        initializeDistanceViews(root);
    }

    private void initializeLocationViews(View root) {
        originInput = root.findViewById(R.id.origem_input);
        destinationInput = root.findViewById(R.id.destino_input);
        originInputLayout = root.findViewById(R.id.origem_input_layout);
        addLocationCard = root.findViewById(R.id.adicionar_localizacao_card);
        locationRecyclerView = root.findViewById(R.id.localizacoes_recycler_view);
        buttonLocation = root.findViewById(R.id.button_fragment_location);
    }

    private void initializeViewsRecomendacao(View root) {
        cardRecomendacaoTransporte = root.findViewById(R.id.card_recomendacao_transporte);
        listaRecomendacoes = root.findViewById(R.id.rv_recomendacoes_transporte);
    }

    private void initializeBezerroViews(View root) {
        categoriaAnimalAutoComplete = root.findViewById(R.id.categoria_animal_input);
        precoArrobaInput = root.findViewById(R.id.preco_arroba_input);
        pesoAnimalInput = root.findViewById(R.id.peso_animal_input);
        quantidadeAnimaisInput = root.findViewById(R.id.quantidade_animais_input);
        resultadoCardBezerro = root.findViewById(R.id.resultado_card);
        valorPorCabecaText = root.findViewById(R.id.valor_por_cabeca_text);
        valorPorKgText = root.findViewById(R.id.valor_por_kg_text);
        valorTotalBezerrosText = root.findViewById(R.id.valor_total_text);
    }

    private void initializeDistanceViews(View root) {
        ajusteCardView = root.findViewById(R.id.ajuste_km_card);
        kmAdicionalInput = root.findViewById(R.id.km_adicional_input);
        adicionar5KmButton = root.findViewById(R.id.adicionar_5km_button);
        adicionar10KmButton = root.findViewById(R.id.adicionar_10km_button);
        adicionar20KmButton = root.findViewById(R.id.local_partida_adicionar_20km_button);
        confirmAjuste = root.findViewById(R.id.confirmar_ajuste_button);
    }

    private void setupUI() {
        if (!isAdded()) return;
        requestPermissions();
        setupBezerroUI();
        setupLocationUI();
        setupNavigationUI();
    }

    private void setupBezerroUI() {
        setupAutoCompleteCategoria();
        setupCalculoBezerroListeners();
    }

    private void setupLocationUI() {
        setupBottomSheet();
        setupRecyclerView();
        setupCard();
        setupOriginInput();
        setupDestinationInput();
    }

    private void setupNavigationUI() {
        setupTransition();
        setupAdditionalButtons();
    }

    private void setupFragmentResultListeners() {
        setupOriginResultListener();
        setupDestinationResultListener();
    }

    private void setupOriginResultListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_ORIGIN, this,
                (requestKey, bundle) -> {
                    if (!isAdded()) return;
                    Address address = getAddressFromBundle(bundle, KEY_ORIGIN);
                    if (address != null) {
                        handleOriginSelected(address);
                    }
                });
    }

    private void setupDestinationResultListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_DESTINATION, this,
                (requestKey, bundle) -> {
                    if (!isAdded()) return;
                    Address address = getAddressFromBundle(bundle, KEY_DESTINATION);
                    if (address != null) {
                        handleDestinationSelected(address);
                    }
                });
    }

    private void handleOriginSelected(Address address) {
        originAddress = address;
        updateOriginUI(address);
        setAjusteCardViewVisible(View.VISIBLE);
        setButtonTransactionVisible(View.VISIBLE);
        if (destinationAddress != null) {
            calculateRoute();
        }
    }

    private void handleDestinationSelected(Address address) {
        destinationAddress = address;
        if (originAddress != null) {
            calculateRoute();
        }
    }

    private void setupAutoCompleteCategoria() {
        executeAsync(() -> {
            categorias = database.categoriaFreteDao().getAll();
            List<String> descriptions = categorias.stream()
                    .map(CategoriaFrete::getDescricao)
                    .collect(Collectors.toList());
            runOnMainThread(() -> {
                ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, descriptions);
                categoriaAnimalAutoComplete.setAdapter(arrayAdapter);
            });
        });
    }

    private void setupCalculoBezerroListeners() {
        Runnable recalcular = this::recalcularBezerro;
        addTextWatcherIfNotNull(precoArrobaInput, recalcular);
        addTextWatcherIfNotNull(pesoAnimalInput, recalcular);
        addTextWatcherIfNotNull(quantidadeAnimaisInput, recalcular);
    }

    private void addTextWatcherIfNotNull(TextInputEditText input, Runnable action) {
        if (input != null) {
            input.addTextChangedListener(new InputWatchers(action));
        }
    }

    private void recalcularBezerro() {
        BigDecimal precoArroba = extrairPrecoArroba();
        BigDecimal pesoAnimal = extrairPesoAnimal();
        Integer quantidade = extrairQuantidade();

        if (possuiValoresValidos(precoArroba, pesoAnimal, quantidade)) {
            calcularEExibir(precoArroba, pesoAnimal, quantidade);
        } else {
            ocultarResultado();
        }
    }

    private BigDecimal extrairPrecoArroba() {
        return extrairValorDecimal(precoArrobaInput);
    }

    private BigDecimal extrairPesoAnimal() {
        return extrairValorDecimal(pesoAnimalInput);
    }

    private Integer extrairQuantidade() {
        return extrairValorInteiro(quantidadeAnimaisInput);
    }

    private BigDecimal extrairValorDecimal(TextInputEditText input) {
        if (input == null) return null;
        try {
            String texto = Objects.requireNonNull(input.getText()).toString().trim();
            return texto.isEmpty() ? null : new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extrairValorInteiro(TextInputEditText input) {
        if (input == null) return null;
        try {
            String texto = Objects.requireNonNull(input.getText()).toString().trim();
            return texto.isEmpty() ? null : Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void calcularEExibir(BigDecimal precoArroba, BigDecimal pesoAnimal, Integer quantidade) {
        BigDecimal valorPorCabeca = calcularValorTotalBezerro(pesoAnimal, precoArroba, AGIO);
        BigDecimal valorPorKg = calcularValorTotalPorKg(pesoAnimal, precoArroba, AGIO);
        BigDecimal valorTotal = calcularValorTotalDeTodosBezerros(valorPorCabeca, quantidade);
        atualizarTextos(valorPorCabeca, valorPorKg, valorTotal);
        exibirResultado();
    }

    private void atualizarTextos(BigDecimal valorCabeca, BigDecimal valorKg, BigDecimal valorTotal) {
        valorPorCabecaText.setText(formatarMoeda(valorCabeca));
        valorPorKgText.setText(formatarMoeda(valorKg));
        valorTotalBezerrosText.setText(formatarMoeda(valorTotal));
    }

    private void exibirResultado() {
        setResultadoCardBezerroViewVisible(View.VISIBLE);
    }

    private void ocultarResultado() {
        setResultadoCardBezerroViewVisible(View.GONE);
    }

    private boolean possuiValoresValidos(BigDecimal preco, BigDecimal peso, Integer qtde) {
        return preco != null && peso != null && qtde != null;
    }

    private static BigDecimal calcularValorTotalDeTodosBezerros(BigDecimal value, int quantity) {
        return value.multiply(BigDecimal.valueOf(quantity));
    }

    private static BigDecimal calcularValorTotalBezerro(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                        BigDecimal percentualAgio) {
        BigDecimal valorBase = calcularValorBasePorPeso(pesoKg, precoPorArroba);
        BigDecimal valorAgio = calcularValorTotalAgio(pesoKg, precoPorArroba, percentualAgio);
        return valorBase.add(valorAgio).setScale(ESCALA_RESULTADO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorTotalPorKg(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                      BigDecimal percentualAgio) {
        BigDecimal valorTotal = calcularValorTotalBezerro(pesoKg, precoPorArroba, percentualAgio);
        return valorTotal.divide(pesoKg, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorBasePorPeso(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        return converterKgParaArrobas(pesoKg).multiply(precoPorArroba);
    }

    private static BigDecimal calcularValorTotalAgio(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                     BigDecimal percentualAgio) {
        if (estaNoPesoBaseOuAcima(pesoKg)) {
            return calcularAgioAcimaPesoBase(pesoKg, precoPorArroba, percentualAgio);
        }
        return calcularAgioAbaixoPesoBase(pesoKg, precoPorArroba, percentualAgio);
    }

    private static BigDecimal calcularAgioAcimaPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                        BigDecimal percentualAgio) {
        BigDecimal agioPorArroba = calcularAgioPorArrobaNoPesoBase(pesoKg, precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return arrobasRestantes.multiply(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaNoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                              BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(pesoKg, precoPorArroba);
        return valorReferencia.subtract(taxaPorArroba);
    }

    private static BigDecimal calcularAgioAbaixoPesoBase(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                         BigDecimal percentualAgio) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pesoAtual = pesoKg;

        while (pesoAtual.compareTo(PESO_BASE_KG) < 0) {
            acumulado = acumulado.add(calcularDiferencaAgioNoPeso(pesoAtual, precoPorArroba, percentualAgio));
            pesoAtual = calcularProximoPesoSuperior(pesoAtual);
        }

        return acumulado.add(calcularAgioAcimaPesoBase(PESO_BASE_KG, precoPorArroba, percentualAgio));
    }

    private static BigDecimal calcularDiferencaAgioNoPeso(BigDecimal pesoKg, BigDecimal precoPorArroba,
                                                          BigDecimal percentualAgio) {
        BigDecimal valorReferencia = obterValorReferenciaAgioPesoBase(precoPorArroba, percentualAgio);
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
        return ARROBAS_ABATE_ESPERADO.subtract(converterKgParaArrobas(pesoKg));
    }

    private static BigDecimal calcularTotalTaxasAbate(BigDecimal precoPorArroba) {
        BigDecimal funrural = ARROBAS_ABATE_ESPERADO.multiply(precoPorArroba).multiply(TAXA_FUNRURAL);
        return funrural.add(TAXA_FIXA_ABATE);
    }

    private static BigDecimal calcularTaxaPorArrobaRestante(BigDecimal pesoKg, BigDecimal precoPorArroba) {
        BigDecimal totalTaxas = calcularTotalTaxasAbate(precoPorArroba);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(pesoKg);
        return totalTaxas.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal obterValorReferenciaAgioPesoBase(BigDecimal precoPorArroba,
                                                               BigDecimal percentualAgio) {
        BigDecimal taxaPorArroba = calcularTaxaPorArrobaRestante(PESO_BASE_KG, precoPorArroba);
        BigDecimal agioPorArroba = calcularAgioPorArrobaExatoPesoBase(precoPorArroba, percentualAgio);
        return taxaPorArroba.add(agioPorArroba);
    }

    private static BigDecimal calcularAgioPorArrobaExatoPesoBase(BigDecimal precoPorArroba,
                                                                 BigDecimal percentualAgio) {
        BigDecimal valorTotalAgio = calcularValorAgioNoPesoBase(precoPorArroba, percentualAgio);
        BigDecimal arrobasRestantes = obterArrobasRestantesParaAbate(PESO_BASE_KG);
        return valorTotalAgio.divide(arrobasRestantes, ESCALA_CALCULO, MODO_ARREDONDAMENTO);
    }

    private static BigDecimal calcularValorAgioNoPesoBase(BigDecimal precoPorArroba,
                                                          BigDecimal percentualAgio) {
        BigDecimal valorComAgio = calcularValorPesoBaseComAgio(precoPorArroba, percentualAgio);
        BigDecimal valorSemAgio = converterKgParaArrobas(PESO_BASE_KG).multiply(precoPorArroba);
        return valorComAgio.subtract(valorSemAgio);
    }

    private static BigDecimal calcularValorPesoBaseComAgio(BigDecimal precoPorArroba,
                                                           BigDecimal percentualAgio) {
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

    private static boolean estaNoPesoBaseOuAcima(BigDecimal pesoKg) {
        return pesoKg.compareTo(PESO_BASE_KG) >= 0;
    }

    private void setupBottomSheet() {
        View view = getView();
        if (view == null) return;
        FrameLayout container = view.findViewById(R.id.localizacao_bottom_sheet_container);
        if (container == null) return;
        bottomSheet = BottomSheetBehavior.from(container);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void setupRecyclerView() {
        if (locationRecyclerView == null) return;
        locationAdapter = new LocationAdapter(addresses, this::onAddressSelected);
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        locationRecyclerView.setAdapter(locationAdapter);
    }

    private void setupCard() {
        if (addLocationCard != null) {
            addLocationCard.setOnClickListener(v ->
                    setBottomSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED));
        }
    }

    private void setupOriginInput() {
        if (originInput == null || originInputLayout == null) return;
        configureOriginFocusListener();
        configureOriginTextWatcher();
        configureOriginClearButton();
    }

    private void setupDestinationInput() {
        if (destinationInput == null) return;
        setDestinationHint(DEFAULT_LOCATION);
        setDestinationEnabled(false);
        setDestinationCursorVisible(false);
        if (destinationAddress == null) {
            loadDefaultDestination();
        }
    }

    private void configureOriginFocusListener() {
        if (originInput == null) return;
        originInput.setOnFocusChangeListener((v, hasFocus) -> {
            setBottomSheetState(hasFocus ?
                    BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN);
            setOriginCursorVisible(hasFocus);
        });
    }

    @SuppressLint("PrivateResource")
    private void configureOriginClearButton() {
        if (originInputLayout == null) return;
        originInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        originInputLayout.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
        setOriginClearButtonVisible(false);
        originInputLayout.setEndIconOnClickListener(v -> clearOrigin());
    }

    private void configureOriginTextWatcher() {
        if (originInput == null) return;
        originInput.addTextChangedListener(new SearchWatcher(this::searchAddress));
    }

    private void onAddressSelected(Address address) {
        if (!isAdded()) return;
        updateOriginAddress(address);
        notifyOriginSelected(address);
        updateOriginUI(address);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        if (originAddress != null) {
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }
    }

    private void loadDefaultDestination() {
        geocodeByNameWithFilter(DEFAULT_LOCATION, null, this::handleDefaultDestination, this::showError);
    }

    private void handleDefaultDestination(List<Address> addresses) {
        Address firstAddress = first(addresses);
        if (firstAddress != null && isAdded()) {
            updateDestinationAddress(firstAddress);
            notifyDestinationSelected(firstAddress);
        }
    }

    private void clearOrigin() {
        updateOriginAddress(null);
        resetOriginUI();
        setAjusteCardViewVisible(View.GONE);
        setButtonTransactionVisible(View.GONE);
    }

    private void searchAddress(String query) {
        clearError();
        geocodeByNameWithFilter(query, userCountryCode, this::updateSearchResults, this::handleSearchError);
    }

    private void handleSearchError(Integer errorResId) {
        updateSearchResults(emptyList());
        showError(errorResId);
    }

    private void geocodeByNameWithFilter(String query, String countryCode,
                                         Consumer<List<Address>> onSuccess,
                                         Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
                List<Address> filtered = filter(results != null ? results : emptyList(), countryCode);
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onSuccess.accept(filtered);
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.erro_servico_localizacao);
                    }
                });
            }
        });
    }

    private void reverseGeocode(double lat, double lng,
                                Consumer<List<Address>> onSuccess,
                                Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onSuccess.accept(results != null ? results : emptyList());
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.erro_servico_localizacao);
                    }
                });
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateSearchResults(List<Address> results) {
        addresses.clear();
        addresses.addAll(results);
        if (locationAdapter != null) {
            locationAdapter.notifyDataSetChanged();
        }
    }


    private void calculateRoute() {
        if (!isAdded() || originAddress == null || destinationAddress == null) return;
        LatLng origin = extractCoordinates(originAddress);
        LatLng destination = extractCoordinates(destinationAddress);
        fetchDirections(origin, destination, this::handleDirectionsSuccess, this::handleDirectionsError);
    }

    private LatLng extractCoordinates(Address address) {
        return new LatLng(address.getLatitude(), address.getLongitude());
    }

    private void fetchDirections(LatLng origin, LatLng destination,
                                 Consumer<Directions> onSuccess,
                                 Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                Context context = getContext();
                if (context == null) return;
                String url = build(origin, destination, context);
                String json = fetch(url);
                parse(json, onSuccess, onError);
            } catch (Exception e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.erro_rede_rotas);
                    }
                });
            }
        });
    }

    private void handleDirectionsSuccess(Directions directions) {
        runOnMainThread(() -> {
            if (isAdded()) {
                updateRouteInfo(directions);
            }
        });
    }

    private void updateRouteInfo(Directions directions) {
        distance = formatDistance(directions.getDistance());
    }

    private double formatDistance(String distanceStr) {
        return Double.parseDouble(distanceStr.replaceAll("[^0-9.]", ""));
    }

    private void handleDirectionsError(Integer errorResId) {
        runOnMainThread(() -> {
            if (isAdded()) {
                showError(errorResId);
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void setupAdditionalButtons() {
        setupDistanceButton(adicionar5KmButton, 5);
        setupDistanceButton(adicionar10KmButton, 10);
        setupDistanceButton(adicionar20KmButton, 20);
        if (confirmAjuste != null) {
            confirmAjuste.setOnClickListener(v -> handleCustomDistance());
        }
    }

    private void setupDistanceButton(Button button, double kmToAdd) {
        if (button != null) {
            button.setOnClickListener(v -> updateDistance(kmToAdd));
        }
    }

    @SuppressLint("StringFormatMatches")
    private void updateDistance(double kmToAdd) {
        distance += kmToAdd;
        String message = getString(R.string.sucesso_distancia_atualizada, distance);
        runOnMainThread(() -> showSuccess(message));
    }

    private void handleCustomDistance() {
        String value = kmAdicionalInput.getText().toString().trim();

        if (value.isEmpty()) {
            runOnMainThread(() -> showError(R.string.erro_campo_vazio));
            return;
        }

        try {
            double newValue = Double.parseDouble(value);
            updateDistance(newValue);
        } catch (NumberFormatException e) {
            runOnMainThread(() -> showError(R.string.erro_valor_invalido));
        }
    }

    private void requestPermissions() {
        Context context = getContext();
        if (context != null) {
            request(permissionLauncher, context);
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        onResult(results, this::fetchUserLocation, this::showPermissionDeniedDialog);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })

    private void fetchUserLocation() {
        Context context = getContext();
        if (context == null || !isGranted(context)) return;
        locationClient.getLastLocation().addOnSuccessListener(requireActivity(), this::processUserLocation);
    }

    private void processUserLocation(Location location) {
        if (location == null || !isAdded()) return;
        reverseGeocode(location.getLatitude(), location.getLongitude(),
                this::handleUserLocationResult, this::showError);
    }

    private void handleUserLocationResult(List<Address> addresses) {
        Address firstAddress = first(addresses);
        if (firstAddress != null) {
            updateUserCountryCode(code(firstAddress));
        }
    }

    private void showPermissionDeniedDialog() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.erro_titulo_permissao_negada)
                .setMessage(R.string.erro_permissao_negada)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void setupTransition() {
        manager = getParentFragmentManager();
        if (buttonLocation != null) {
            buttonLocation.setOnClickListener(v -> navigateToLocationFragment());
        }
    }

    private void navigateToLocationFragment() {
        if (!isAdded() || originAddress == null || destinationAddress == null || isNavigating) {
            return;
        }
        isNavigating = true;
        LocationFragment fragment = LocationFragment.newInstance(originAddress, destinationAddress);
        manager.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        Address savedOrigin = getAddressFromBundle(savedInstanceState, STATE_ORIGIN);
        if (savedOrigin != null) {
            originAddress = savedOrigin;
            updateOriginUI(savedOrigin);
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }

        Address savedDestination = getAddressFromBundle(savedInstanceState, STATE_DESTINATION);
        if (savedDestination != null) {
            destinationAddress = savedDestination;
        }

        if (originAddress != null && destinationAddress != null) {
            calculateRoute();
        }
    }

    private void saveAddressState(Bundle outState) {
        if (originAddress != null) {
            outState.putParcelable(STATE_ORIGIN, originAddress);
        }
        if (destinationAddress != null) {
            outState.putParcelable(STATE_DESTINATION, destinationAddress);
        }
    }

    private Address getAddressFromBundle(Bundle bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, Address.class);
        } else {
            return bundle.getParcelable(key);
        }
    }

    private void updateOriginAddress(Address address) {
        this.originAddress = address;
    }

    private void updateDestinationAddress(Address address) {
        this.destinationAddress = address;
    }

    private void updateUserCountryCode(String countryCode) {
        this.userCountryCode = countryCode;
    }

    private void notifyOriginSelected(Address address) {
        calculateRoute();
        sendFragmentResult(RESULT_KEY_ORIGIN, KEY_ORIGIN, address);
    }

    private void notifyDestinationSelected(Address address) {
        sendFragmentResult(RESULT_KEY_DESTINATION, KEY_DESTINATION, address);
    }

    private void sendFragmentResult(String resultKey, String bundleKey, Address address) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(bundleKey, address);
        getParentFragmentManager().setFragmentResult(resultKey, bundle);
    }

    private void updateUIAfterReturn() {
        if (!isAdded()) return;

        if (originAddress != null) {
            updateOriginUI(originAddress);
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }

        if (bottomSheet != null) {
            setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        }

        clearInputFocus();
    }

    private void clearInputFocus() {
        if (originInput != null) {
            originInput.clearFocus();
        }
        if (destinationInput != null) {
            destinationInput.clearFocus();
        }
    }

    private void updateOriginUI(Address address) {
        if (!isAdded()) return;
        setOriginText("");
        setOriginHint(format(address));
        setOriginEnabled(false);
        setOriginFocusable(false);
        setOriginCursorVisible(false);
        setOriginClearButtonVisible(true);
        clearOriginFocus();
    }

    private void resetOriginUI() {
        if (!isAdded()) return;
        setOriginText("");
        setOriginHint(getString(R.string.rotulo_endereco_origem));
        setOriginEnabled(true);
        setOriginFocusable(true);
        setOriginClearButtonVisible(false);
    }

    private static String formatarMoeda(BigDecimal valor) {
        return "R$ " + FORMATADOR_MOEDA.format(valor);
    }

    private void setBottomSheetState(int state) {
        if (bottomSheet != null) {
            bottomSheet.setState(state);
        }
    }

    private void setOriginText(String text) {
        if (originInput != null) {
            originInput.setText(text);
        }
    }

    private void setOriginHint(String hint) {
        if (originInput != null) {
            originInput.setHint(hint);
        }
    }

    private void setOriginEnabled(boolean enabled) {
        if (originInput != null) {
            originInput.setEnabled(enabled);
        }
    }

    private void setOriginFocusable(boolean focusable) {
        if (originInput != null) {
            originInput.setFocusable(focusable);
            originInput.setFocusableInTouchMode(focusable);
        }
    }

    private void setButtonTransactionVisible(int visible) {
        if (buttonLocation != null) {
            buttonLocation.setVisibility(visible);
        }
    }

    private void setResultadoCardBezerroViewVisible(int visible) {
        if (resultadoCardBezerro != null) {
            resultadoCardBezerro.setVisibility(visible);
        }
    }

    private void setAjusteCardViewVisible(int visible) {
        if (ajusteCardView != null) {
            ajusteCardView.setVisibility(visible);
        }
    }

    private void setOriginCursorVisible(boolean visible) {
        if (originInput != null) {
            originInput.setCursorVisible(visible);
        }
    }

    private void setOriginClearButtonVisible(boolean visible) {
        if (originInputLayout != null) {
            originInputLayout.setEndIconVisible(visible);
        }
    }

    private void clearOriginFocus() {
        if (originInput != null) {
            originInput.clearFocus();
        }
    }

    private void clearError() {
        if (originInputLayout != null) {
            originInputLayout.setError(null);
        }
    }

    private void setDestinationHint(String hint) {
        if (destinationInput != null) {
            destinationInput.setHint(hint);
        }
    }

    private void setDestinationEnabled(boolean enabled) {
        if (destinationInput != null) {
            destinationInput.setEnabled(enabled);
        }
    }

    private void setDestinationCursorVisible(boolean visible) {
        if (destinationInput != null) {
            destinationInput.setCursorVisible(visible);
        }
    }

    private void showSuccess(String message) {
        if (isAdded()) {
            showSnackbar(message, Color.parseColor("#279958"), Color.WHITE);
        }
    }

    private void showError(int messageResId) {
        if (isAdded()) {
            showSnackbar(messageResId, Color.RED, Color.WHITE);
        }
    }

    private void showSnackbar(String message, int backgroundColor, int textColor) {
        View view = getView();
        if (view != null && isAdded()) {
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(backgroundColor)
                    .setTextColor(textColor);
            snackbar.show();
        }
    }

    private void showSnackbar(int messageResId, int backgroundColor, int textColor) {
        if (getContext() != null) {
            showSnackbar(getContext().getString(messageResId), backgroundColor, textColor);
        }
    }

    private void executeAsync(Runnable task) {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(task);
        }
    }

    private void runOnMainThread(Runnable action) {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    private void clearViewReferences() {
        originInput = null;
        destinationInput = null;
        originInputLayout = null;
        addLocationCard = null;
        locationRecyclerView = null;
        locationAdapter = null;
        bottomSheet = null;
        buttonLocation = null;

        categoriaAnimalAutoComplete = null;
        precoArrobaInput = null;
        pesoAnimalInput = null;
        quantidadeAnimaisInput = null;
        resultadoCardBezerro = null;
        valorPorCabecaText = null;
        valorPorKgText = null;
        valorTotalBezerrosText = null;

        ajusteCardView = null;
        kmAdicionalInput = null;
        adicionar5KmButton = null;
        adicionar10KmButton = null;
        adicionar20KmButton = null;
        confirmAjuste = null;
    }

    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                showError(R.string.erro_generico);
                Thread.currentThread().interrupt();
            } finally {
                executorService.shutdownNow();
            }
        }
    }
}