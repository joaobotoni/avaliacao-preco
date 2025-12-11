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
import androidx.fragment.app.FragmentManager;
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
import java.util.Arrays;
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
public class MainFragment extends Fragment implements
        DirectionsProvider, LocationPermissionProvider, AddressProvider {
    private static final String DEFAULT_LOCATION = "Cuiabá";
    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_UP_ORIGIN = "updateOrigin";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private static final String RESULT_KEY_UP_ORIGIN = "updateOriginKey";
    private static final String STATE_ORIGIN = "newOrigin";
    private static final String STATE_DESTINATION = "newDestination";
    private static final String STATE_ARROBA_PRICE = "arrobaPrice";
    private static final String STATE_ANIMAL_WEIGHT = "animalWeight";
    private static final String STATE_ANIMAL_QUANTITY = "animalQuantity";
    private static final String STATE_CATEGORY_ID = "categoryId";
    private static final String STATE_DISTANCE = "distance";

    private static final BigDecimal ARROBA_WEIGHT_KG = new BigDecimal("30.0");
    private static final BigDecimal EXPECTED_SLAUGHTER_ARROBAS = new BigDecimal("21.00");
    private static final BigDecimal BASE_WEIGHT_KG = new BigDecimal("180.0");
    private static final BigDecimal FIXED_SLAUGHTER_FEE = new BigDecimal("69.70");
    private static final BigDecimal FUNRURAL_TAX = new BigDecimal("0.015");
    private static final BigDecimal AGIO = new BigDecimal("30");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 15;
    private static final int RESULT_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private static final DecimalFormatSymbols BRAZILIAN_SYMBOLS = new DecimalFormatSymbols(new Locale("pt", "BR"));
    private static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("#,##0.00", BRAZILIAN_SYMBOLS);

    private ExecutorService executorService;
    private Handler mainHandler;
    private Geocoder geocoder;
    private FusedLocationProviderClient locationClient;
    private FragmentManager manager;
    private AppDatabase database;

    private Address originAddress;
    private Address destinationAddress;
    private String userCountryCode;
    private volatile double distance;
    private final AtomicBoolean isNavigating = new AtomicBoolean(false);
    private final AtomicBoolean isCalculatingRoute = new AtomicBoolean(false);

    private final List<Address> addresses = Collections.synchronizedList(new ArrayList<>());
    private List<TipoVeiculoFrete> vehicleTypes = new ArrayList<>();
    private List<CategoriaFrete> categories = new ArrayList<>();
    private List<CapacidadeFrete> freightCapacities = new ArrayList<>();
    private List<Frete> freightTable = new ArrayList<>();
    private CategoriaFrete currentCategory;
    private List<Recomendacao> recommendations = new ArrayList<>();
    private final AtomicBoolean isCalculatingRecommendations = new AtomicBoolean(false);


    private Map<Long, TipoVeiculoFrete> vehicleCache = new ConcurrentHashMap<>();
    private Map<Long, List<CapacidadeFrete>> capacitiesByCategoryCache = new ConcurrentHashMap<>();
    private Map<Long, List<Frete>> freightsByVehicleTypeCache = new ConcurrentHashMap<>();

    private TextInputEditText originInput;
    private TextInputEditText destinationInput;
    private TextInputLayout originInputLayout;
    private MaterialCardView addLocationCard;
    private RecyclerView locationRecyclerView;
    private LocationAdapter locationAdapter;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private Button locationButton;

    private AutoCompleteTextView animalCategoryAutoComplete;
    private TextInputEditText arrobaPriceInput;
    private TextInputEditText animalWeightInput;
    private TextInputEditText animalQuantityInput;
    private CardView calfResultCard;
    private TextView valuePerHeadText;
    private TextView valuePerKgText;
    private TextView totalCalfValueText;

    private CardView adjustmentCardView;
    private EditText additionalKmInput;
    private Button add5KmButton;
    private Button add10KmButton;
    private Button add20KmButton;
    private Button confirmAdjustment;

    private CardView transportRecommendationCard;
    private RecyclerView recommendationsRecyclerView;
    private TextView recommendationReasonText;
    private RecomendacaoAdapter recommendationAdapter;

    private CardView valorFreteFinalCard;
    private CardView rotaResumoCard;

    private TextView origemLabelText;
    private TextView origemValorText;

    private TextView destinoLabelText;
    private TextView destinoValorText;
    private TextView distanciaValorText;
    private TextView valorFreteText;
    private TextView valorTotalText;

    private static final double MAX_TABLE_DISTANCE = 300.0;
    private static final Map<String, BigDecimal> ADDITIONAL_KM_RATES;

    static {
        ADDITIONAL_KM_RATES = new HashMap<>();
        ADDITIONAL_KM_RATES.put("TRUK", new BigDecimal("9.30"));
        ADDITIONAL_KM_RATES.put("CARRETA BAIXA", new BigDecimal("13.00"));
        ADDITIONAL_KM_RATES.put("CARRETA ALTA", new BigDecimal("15.00"));
        ADDITIONAL_KM_RATES.put("CARRETA TRES EIXOS", new BigDecimal("17.00"));
    }

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::handlePermissionsResult
    );

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeCoreDependencies();
        registerFragmentResultListeners();
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
        bindAllViews(view);
        configureAllUIComponents(view);
        loadDatabaseDataAndRestoreState(savedInstanceState);
        view.post(this::synchronizeUIWithState);
    }

    @Override
    public void onResume() {
        super.onResume();
        isNavigating.set(false);

        if (getView() != null) {
            getView().post(this::synchronizeUIWithState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isNavigating.set(false);
        isCalculatingRoute.set(false);
        isCalculatingRecommendations.set(false);

        getParentFragmentManager().clearFragmentResultListener(RESULT_KEY_ORIGIN);
        getParentFragmentManager().clearFragmentResultListener(RESULT_KEY_DESTINATION);

        releaseAllViewReferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        persistCurrentState(outState);
    }

    private void initializeCoreDependencies() {
        Context context = requireContext();
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mainHandler = new Handler(Looper.getMainLooper());
        locationClient = LocationServices.getFusedLocationProviderClient(context);
        geocoder = new Geocoder(context, new Locale("pt", "BR"));
        database = AppDatabase.getDatabase(context);
    }


    private void bindAllViews(View root) {
        bindLocationViews(root);
        bindCalculationViews(root);
        bindRecommendationViews(root);
        bindDistanceAdjustmentViews(root);
        bindValorFreteFinalCard(root);
    }

    private void bindLocationViews(View root) {
        originInput = root.findViewById(R.id.origem_input);
        destinationInput = root.findViewById(R.id.destino_input);
        originInputLayout = root.findViewById(R.id.origem_input_layout);
        addLocationCard = root.findViewById(R.id.adicionar_localizacao_card);
        locationRecyclerView = root.findViewById(R.id.localizacoes_recycler_view);
        locationButton = root.findViewById(R.id.button_fragment_location);
    }

    private void bindCalculationViews(View root) {
        animalCategoryAutoComplete = root.findViewById(R.id.categoria_animal_input);
        arrobaPriceInput = root.findViewById(R.id.preco_arroba_input);
        animalWeightInput = root.findViewById(R.id.peso_animal_input);
        animalQuantityInput = root.findViewById(R.id.quantidade_animais_input);
        calfResultCard = root.findViewById(R.id.resultado_card);
        valuePerHeadText = root.findViewById(R.id.valor_por_cabeca_text);
        valuePerKgText = root.findViewById(R.id.valor_por_kg_text);
        totalCalfValueText = root.findViewById(R.id.valor_total_text);
    }

    private void bindRecommendationViews(View root) {
        transportRecommendationCard = root.findViewById(R.id.card_recomendacao_transporte);
        recommendationsRecyclerView = root.findViewById(R.id.rv_recomendacoes_transporte);
        recommendationReasonText = root.findViewById(R.id.tv_motivo_recomendacao);
    }

    private void bindDistanceAdjustmentViews(View root) {
        adjustmentCardView = root.findViewById(R.id.ajuste_km_card);
        additionalKmInput = root.findViewById(R.id.km_adicional_input);
        add5KmButton = root.findViewById(R.id.adicionar_5km_button);
        add10KmButton = root.findViewById(R.id.adicionar_10km_button);
        add20KmButton = root.findViewById(R.id.local_partida_adicionar_20km_button);
        confirmAdjustment = root.findViewById(R.id.confirmar_ajuste_button);
    }


    private void bindValorFreteFinalCard(View root) {
        valorFreteFinalCard = root.findViewById(R.id.valor_frete_final_card);
        rotaResumoCard = root.findViewById(R.id.rota_resumo_card);

        origemLabelText = root.findViewById(R.id.origem_label_text);
        origemValorText = root.findViewById(R.id.origem_valor_text);

        destinoLabelText = root.findViewById(R.id.destino_label_text);
        destinoValorText = root.findViewById(R.id.destino_valor_text);

        distanciaValorText = root.findViewById(R.id.distancia_valor_text);
        valorFreteText = root.findViewById(R.id.valor_frete_text);
        valorTotalText = root.findViewById(R.id.valor_total_final_text);
    }

    private void configureAllUIComponents(View view) {
        if (!isFragmentActive()) return;
        requestLocationPermissions();
        configureCalculationComponents();
        configureLocationComponents(view);
        configureNavigationComponents();
        configureValorFinal();
    }

    private void configureCalculationComponents() {
        configureCategorySelection();
        attachCalculationListeners();
    }

    private void configureLocationComponents(View view) {
        configureBottomSheetBehavior(view);
        configureLocationRecyclerView();
        configureRecommendationRecyclerView();
        configureAddLocationCardClick();
        configureOriginInputBehavior();
        configureDestinationInputBehavior();
    }

    private void configureNavigationComponents() {
        configureDistanceAdjustmentButtons();
        configureLocationFragmentNavigation();
    }

    private void configureValorFinal() {
        configureFinalCard();
    }

    private void loadDatabaseDataAndRestoreState(@Nullable Bundle savedInstanceState) {
        executeInBackground(() -> {
            try {
                if (!isFragmentActive()) return;

                loadAllDatabaseEntities();

                if (!isFragmentActive()) return;

                buildEntityCaches();

                executeOnMainThread(() -> {
                    if (isFragmentActive() && getView() != null && getContext() != null) {
                        applyCategoryAdapter();
                        restorePersistedState(savedInstanceState);
                        initializeDefaultDestination();
                        performCalfCalculation();
                        updateVehicleRecommendations();
                        configureFinalCard();
                    }
                });
            } catch (Exception e) {
                executeOnMainThread(() -> {
                    if (isFragmentActive() && getContext() != null) {
                        displayErrorMessage(R.string.erro_generico);
                    }
                });
            }
        });
    }

    private void loadAllDatabaseEntities() {
        vehicleTypes = database.tipoVeiculoFreteDao().getAll();
        categories = database.categoriaFreteDao().getAll();
        freightCapacities = database.capacidadeFreteDao().getAll();
        freightTable = database.freteDao().getAll();
    }

    private void buildEntityCaches() {
        buildVehicleCache();
        buildCapacityCache();
        buildFreightCache();
    }

    private void buildVehicleCache() {
        vehicleCache = vehicleTypes.stream()
                .collect(Collectors.toMap(v -> Long.valueOf(v.getId()), v -> v));
    }

    private void buildCapacityCache() {
        capacitiesByCategoryCache = freightCapacities.stream()
                .collect(Collectors.groupingBy(CapacidadeFrete::getIdCategoriaFrete));

        capacitiesByCategoryCache.values().forEach(list ->
                list.sort(Comparator.comparingInt(CapacidadeFrete::getQtdeFinal).reversed()));
    }

    private void buildFreightCache() {
        freightsByVehicleTypeCache = freightTable.stream()
                .collect(Collectors.groupingBy(Frete::getIdTipoVeiculoFrete));

        freightsByVehicleTypeCache.values().forEach(list ->
                list.sort(Comparator.comparingDouble(Frete::getKmInicial)));
    }

    private void persistCurrentState(@NonNull Bundle outState) {
        persistInputValues(outState);
        persistAddresses(outState);
        persistDistance(outState);
    }

    private void persistInputValues(@NonNull Bundle outState) {
        saveTextInputValue(outState, arrobaPriceInput, STATE_ARROBA_PRICE);
        saveTextInputValue(outState, animalWeightInput, STATE_ANIMAL_WEIGHT);
        saveTextInputValue(outState, animalQuantityInput, STATE_ANIMAL_QUANTITY);

        Optional.ofNullable(currentCategory)
                .map(CategoriaFrete::getId)
                .ifPresent(id -> outState.putLong(STATE_CATEGORY_ID, id));
    }

    private void persistAddresses(Bundle outState) {
        Optional.ofNullable(originAddress)
                .ifPresent(address -> outState.putParcelable(STATE_ORIGIN, address));
        Optional.ofNullable(destinationAddress)
                .ifPresent(address -> outState.putParcelable(STATE_DESTINATION, address));
    }

    private void persistDistance(Bundle outState) {
        if (distance > 0) {
            outState.putDouble(STATE_DISTANCE, distance);
        }
    }

    private void saveTextInputValue(Bundle outState, TextInputEditText input, String key) {
        Optional.ofNullable(input)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .filter(text -> !text.trim().isEmpty())
                .ifPresent(text -> outState.putString(key, text));
    }

    private void restorePersistedState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        restoreAddressesFromBundle(savedInstanceState);
        restoreInputValuesFromBundle(savedInstanceState);
        restoreDistanceFromBundle(savedInstanceState);

        if (hasValidRouteAddresses() && distance == 0) {
            initiateRouteCalculation();
        } else if (!hasValidRouteAddresses() && distance > 0) {
            distance = 0;
        }
    }

    private void restoreAddressesFromBundle(Bundle savedInstanceState) {
        extractAddressFromBundle(savedInstanceState, STATE_ORIGIN).ifPresent(address -> {
            originAddress = address;
            applyOriginAddressToUI(address);
            updateConditionalComponentsVisibility();
        });

        extractAddressFromBundle(savedInstanceState, STATE_DESTINATION).ifPresent(address -> {
            destinationAddress = address;
        });
    }

    private void restoreInputValuesFromBundle(Bundle savedInstanceState) {
        restoreTextInputValue(savedInstanceState, arrobaPriceInput, STATE_ARROBA_PRICE);
        restoreTextInputValue(savedInstanceState, animalWeightInput, STATE_ANIMAL_WEIGHT);
        restoreTextInputValue(savedInstanceState, animalQuantityInput, STATE_ANIMAL_QUANTITY);

        long categoryId = savedInstanceState.getLong(STATE_CATEGORY_ID, -1);
        if (categoryId != -1) {
            restoreCategorySelection(categoryId);
        }
    }

    private void restoreDistanceFromBundle(Bundle savedInstanceState) {
        double savedDistance = savedInstanceState.getDouble(STATE_DISTANCE, 0);
        if (savedDistance > 0) {
            distance = savedDistance;
        }
    }

    private void restoreTextInputValue(Bundle savedInstanceState, TextInputEditText input, String key) {
        Optional.ofNullable(savedInstanceState.getString(key))
                .ifPresent(savedText -> Optional.ofNullable(input)
                        .ifPresent(i -> i.setText(savedText)));
    }

    private void restoreCategorySelection(long categoryId) {
        categories.stream()
                .filter(category -> category.getId() == categoryId)
                .findFirst()
                .ifPresent(category -> {
                    currentCategory = category;
                    Optional.ofNullable(animalCategoryAutoComplete)
                            .ifPresent(autoComplete -> autoComplete.setText(category.getDescricao(), false));
                    updateVehicleRecommendations();
                });
    }

    private void configureCategorySelection() {
        Optional.ofNullable(animalCategoryAutoComplete).ifPresent(autoComplete ->
                autoComplete.setOnItemClickListener((parent, view, position, id) -> {
                    if (isValidCategoryPosition(position)) {
                        handleCategorySelection(position);
                    }
                }));
    }

    private boolean isValidCategoryPosition(int position) {
        return position >= 0 && position < categories.size();
    }

    private void handleCategorySelection(int position) {
        currentCategory = categories.get(position);
        updateCategoryAutoCompleteText(currentCategory.getDescricao());
        updateConditionalComponentsVisibility();
        updateVehicleRecommendations();
    }

    private void updateCategoryAutoCompleteText(String description) {
        Optional.ofNullable(animalCategoryAutoComplete)
                .ifPresent(autoComplete -> autoComplete.setText(description, false));
    }

    private void applyCategoryAdapter() {
        if (animalCategoryAutoComplete != null && !categories.isEmpty()) {
            CategoriaAdapter adapter = new CategoriaAdapter(requireContext(), categories);
            animalCategoryAutoComplete.setAdapter(adapter);
        }
    }

    private void attachCalculationListeners() {
        attachInputListener(arrobaPriceInput, this::performCalfCalculation);
        attachInputListener(animalWeightInput, this::performCalfCalculation);
        attachInputListener(animalQuantityInput, this::handleQuantityChange);
    }

    private void handleQuantityChange() {
        performCalfCalculation();
        updateVehicleRecommendations();
        updateConditionalComponentsVisibility();
    }

    private void attachInputListener(TextInputEditText input, Runnable action) {
        Optional.ofNullable(input).ifPresent(i -> i.addTextChangedListener(new InputWatchers(action)));
    }

    private void performCalfCalculation() {
        BigDecimal arrobaPrice = parseDecimalFromInput(arrobaPriceInput);
        BigDecimal animalWeight = parseDecimalFromInput(animalWeightInput);
        Integer quantity = parseIntegerFromInput(animalQuantityInput);

        if (areCalculationInputsValid(arrobaPrice, animalWeight, quantity)) {
            executeCalculationAndDisplay(arrobaPrice, animalWeight, quantity);
        } else {
            hideCalculationResult();
        }
    }

    private boolean areCalculationInputsValid(BigDecimal price, BigDecimal weight, Integer quantity) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0
                && weight != null && weight.compareTo(BigDecimal.ZERO) > 0
                && quantity != null && quantity > 0;
    }

    private void executeCalculationAndDisplay(BigDecimal arrobaPrice, BigDecimal animalWeight, Integer quantity) {
        BigDecimal valuePerHead = calculateCalfTotalValue(animalWeight, arrobaPrice, AGIO);
        BigDecimal valuePerKg = calculateTotalValuePerKg(animalWeight, arrobaPrice, AGIO);
        BigDecimal totalValue = calculateTotalValueForAllCalves(valuePerHead, quantity);

        updateCalculationResultTexts(valuePerHead, valuePerKg, totalValue);
        showCalculationResult();
        configureFinalCard();
    }

    private void updateCalculationResultTexts(BigDecimal headValue, BigDecimal kgValue, BigDecimal totalValue) {
        setTextViewValue(valuePerHeadText, formatToCurrency(headValue));
        setTextViewValue(valuePerKgText, formatToCurrency(kgValue));
        setTextViewValue(totalCalfValueText, formatToCurrency(totalValue));
    }

    private void showCalculationResult() {
        setViewVisibility(calfResultCard, View.VISIBLE);
    }

    private void hideCalculationResult() {
        setViewVisibility(calfResultCard, View.GONE);
    }

    private static BigDecimal calculateTotalValueForAllCalves(BigDecimal value, int quantity) {
        return value.multiply(new BigDecimal(quantity)).setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal calculateCalfTotalValue(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                      BigDecimal agioPercentage) {
        BigDecimal baseValue = calculateBaseValueByWeight(weightKg, pricePerArroba);
        BigDecimal agioValue = calculateTotalAgioValue(weightKg, pricePerArroba, agioPercentage);
        return baseValue.add(agioValue).setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal calculateTotalValuePerKg(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                       BigDecimal agioPercentage) {
        BigDecimal totalValue = calculateCalfTotalValue(weightKg, pricePerArroba, agioPercentage);
        return totalValue.divide(weightKg, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal calculateBaseValueByWeight(BigDecimal weightKg, BigDecimal pricePerArroba) {
        return convertKgToArrobas(weightKg).multiply(pricePerArroba);
    }

    private static BigDecimal calculateTotalAgioValue(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                      BigDecimal agioPercentage) {
        if (isAtOrAboveBaseWeight(weightKg)) {
            return calculateAgioAboveBaseWeight(weightKg, pricePerArroba, agioPercentage);
        }
        return calculateAgioBelowBaseWeight(weightKg, pricePerArroba, agioPercentage);
    }

    private static BigDecimal calculateAgioAboveBaseWeight(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                           BigDecimal agioPercentage) {
        BigDecimal agioPerArroba = calculateAgioPerArrobaAtBaseWeight(weightKg, pricePerArroba, agioPercentage);
        BigDecimal remainingArrobas = getRemainingArrobasForSlaughter(weightKg);
        return remainingArrobas.multiply(agioPerArroba);
    }

    private static BigDecimal calculateAgioPerArrobaAtBaseWeight(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                                 BigDecimal agioPercentage) {
        BigDecimal referenceValue = getReferenceAgioValueAtBaseWeight(pricePerArroba, agioPercentage);
        BigDecimal feePerArroba = calculateFeePerRemainingArroba(weightKg, pricePerArroba);
        return referenceValue.subtract(feePerArroba);
    }

    private static BigDecimal calculateAgioBelowBaseWeight(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                           BigDecimal agioPercentage) {
        BigDecimal accumulated = BigDecimal.ZERO;
        BigDecimal currentWeight = weightKg;

        while (currentWeight.compareTo(BASE_WEIGHT_KG) < 0) {
            accumulated = accumulated.add(calculateAgioDifferenceAtWeight(currentWeight, pricePerArroba, agioPercentage));
            currentWeight = calculateNextHigherWeight(currentWeight);
        }

        return accumulated.add(calculateAgioAboveBaseWeight(BASE_WEIGHT_KG, pricePerArroba, agioPercentage));
    }

    private static BigDecimal calculateAgioDifferenceAtWeight(BigDecimal weightKg, BigDecimal pricePerArroba,
                                                              BigDecimal agioPercentage) {
        BigDecimal referenceValue = getReferenceAgioValueAtBaseWeight(pricePerArroba, agioPercentage);
        BigDecimal feePerArroba = calculateFeePerRemainingArroba(weightKg, pricePerArroba);
        return referenceValue.subtract(feePerArroba);
    }

    private static BigDecimal calculateNextHigherWeight(BigDecimal weightKg) {
        BigDecimal arrobas = convertKgToArrobas(weightKg);
        BigDecimal nextArrobas = roundArrobasUp(arrobas);
        BigDecimal nextWeight = nextArrobas.multiply(ARROBA_WEIGHT_KG);
        return limitWeightToBaseWeight(nextWeight);
    }

    private static BigDecimal convertKgToArrobas(BigDecimal weightKg) {
        return weightKg.divide(ARROBA_WEIGHT_KG, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal getRemainingArrobasForSlaughter(BigDecimal weightKg) {
        return EXPECTED_SLAUGHTER_ARROBAS.subtract(convertKgToArrobas(weightKg));
    }

    private static BigDecimal calculateTotalSlaughterFees(BigDecimal pricePerArroba) {
        BigDecimal funrural = EXPECTED_SLAUGHTER_ARROBAS.multiply(pricePerArroba).multiply(FUNRURAL_TAX);
        return funrural.add(FIXED_SLAUGHTER_FEE);
    }

    private static BigDecimal calculateFeePerRemainingArroba(BigDecimal weightKg, BigDecimal pricePerArroba) {
        BigDecimal totalFees = calculateTotalSlaughterFees(pricePerArroba);
        BigDecimal remainingArrobas = getRemainingArrobasForSlaughter(weightKg);
        return totalFees.divide(remainingArrobas, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal getReferenceAgioValueAtBaseWeight(BigDecimal pricePerArroba,
                                                                BigDecimal agioPercentage) {
        BigDecimal feePerArroba = calculateFeePerRemainingArroba(BASE_WEIGHT_KG, pricePerArroba);
        BigDecimal agioPerArroba = calculateAgioPerArrobaAtExactBaseWeight(pricePerArroba, agioPercentage);
        return feePerArroba.add(agioPerArroba);
    }

    private static BigDecimal calculateAgioPerArrobaAtExactBaseWeight(BigDecimal pricePerArroba,
                                                                      BigDecimal agioPercentage) {
        BigDecimal totalAgioValue = calculateAgioValueAtBaseWeight(pricePerArroba, agioPercentage);
        BigDecimal remainingArrobas = getRemainingArrobasForSlaughter(BASE_WEIGHT_KG);
        return totalAgioValue.divide(remainingArrobas, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal calculateAgioValueAtBaseWeight(BigDecimal pricePerArroba,
                                                             BigDecimal agioPercentage) {
        BigDecimal valueWithAgio = calculateBaseWeightValueWithAgio(pricePerArroba, agioPercentage);
        BigDecimal valueWithoutAgio = convertKgToArrobas(BASE_WEIGHT_KG).multiply(pricePerArroba);
        return valueWithAgio.subtract(valueWithoutAgio);
    }

    private static BigDecimal calculateBaseWeightValueWithAgio(BigDecimal pricePerArroba,
                                                               BigDecimal agioPercentage) {
        BigDecimal baseArrobas = convertKgToArrobas(BASE_WEIGHT_KG);
        BigDecimal multiplierFactor = getAgioMultiplierFactor(agioPercentage);
        return baseArrobas.multiply(pricePerArroba).divide(multiplierFactor, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal getAgioMultiplierFactor(BigDecimal agioPercentage) {
        return ONE_HUNDRED.subtract(agioPercentage).divide(ONE_HUNDRED, CALCULATION_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal roundArrobasUp(BigDecimal arrobas) {
        BigDecimal rounded = arrobas.setScale(0, RoundingMode.CEILING);
        if (rounded.compareTo(arrobas) == 0) {
            rounded = rounded.add(BigDecimal.ONE);
        }
        return rounded;
    }

    private static BigDecimal limitWeightToBaseWeight(BigDecimal weightKg) {
        return weightKg.compareTo(BASE_WEIGHT_KG) > 0 ? BASE_WEIGHT_KG : weightKg;
    }

    private static boolean isAtOrAboveBaseWeight(BigDecimal weightKg) {
        return weightKg.compareTo(BASE_WEIGHT_KG) >= 0;
    }

    private void updateVehicleRecommendations() {
        if (!canCalculateRecommendations()) {
            hideRecommendationDisplay();
            return;
        }

        Integer quantity = parseIntegerFromInput(animalQuantityInput);
        if (!isValidQuantity(quantity)) {
            hideRecommendationDisplay();
            return;
        }

        if (!isCalculatingRecommendations.compareAndSet(false, true)) {
            return;
        }

        final int animalCount = quantity;
        final Long categoryId = currentCategory.getId();

        executeInBackground(() -> {
            try {
                List<Recomendacao> calculated = computeVehicleRecommendations(animalCount, categoryId);

                executeOnMainThread(() -> {
                    try {
                        if (isFragmentActive() && getView() != null) {
                            recommendations = calculated;
                            renderRecommendations(calculated, animalCount);
                        }
                    } finally {
                        isCalculatingRecommendations.set(false);
                    }
                });
            } catch (Exception e) {
                executeOnMainThread(() -> {
                    try {
                        if (isFragmentActive()) {
                            displayErrorMessage(R.string.erro_generico);
                            hideRecommendationDisplay();
                        }
                    } finally {
                        isCalculatingRecommendations.set(false);
                    }
                });
            }
        });
    }

    private boolean canCalculateRecommendations() {
        return currentCategory != null
                && !freightCapacities.isEmpty()
                && !vehicleTypes.isEmpty()
                && capacitiesByCategoryCache.containsKey(currentCategory.getId());
    }

    private boolean isValidQuantity(Integer quantity) {
        return quantity != null && quantity > 0;
    }

    private List<Recomendacao> computeVehicleRecommendations(int totalAnimals, Long categoryId) {
        List<CapacidadeFrete> availableCapacities = capacitiesByCategoryCache.getOrDefault(categoryId, new ArrayList<>());

        assert availableCapacities != null;
        if (availableCapacities.isEmpty()) {
            return new ArrayList<>();
        }

        return findSingleIdealVehicle(totalAnimals, availableCapacities)
                .map(capacity -> createVehicleRecommendation(1, capacity.getIdTipoVeiculoFrete()))
                .map(List::of)
                .orElseGet(() -> computeMultiVehicleCombination(totalAnimals, availableCapacities));
    }

    private Optional<CapacidadeFrete> findSingleIdealVehicle(int total, List<CapacidadeFrete> capacities) {
        return capacities.stream()
                .filter(capacity -> total >= capacity.getQtdeInicial() && total <= capacity.getQtdeFinal())
                .findFirst();
    }

    private List<Recomendacao> computeMultiVehicleCombination(int total, List<CapacidadeFrete> capacities) {
        Map<Long, Integer> vehicleDistribution = new HashMap<>();
        int remainingAnimals = total;

        CapacidadeFrete largestCapacity = capacities.get(0);
        int maxCapacity = largestCapacity.getQtdeFinal();

        if (maxCapacity <= 0) {
            return new ArrayList<>();
        }

        if (maxCapacity > 0 && remainingAnimals > maxCapacity) {
            int fullVehicleCount = remainingAnimals / maxCapacity;
            vehicleDistribution.put(largestCapacity.getIdTipoVeiculoFrete(), fullVehicleCount);
            remainingAnimals = remainingAnimals % maxCapacity;
        }

        if (remainingAnimals > 0) {
            CapacidadeFrete partialVehicle = findSingleIdealVehicle(remainingAnimals, capacities)
                    .orElse(largestCapacity);

            Long typeId = partialVehicle.getIdTipoVeiculoFrete();
            vehicleDistribution.merge(typeId, 1, Integer::sum);
        }

        return vehicleDistribution.entrySet().stream()
                .map(entry -> createVehicleRecommendation(entry.getValue(), entry.getKey()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Recomendacao::getTipoTransporte))
                .collect(Collectors.toList());
    }

    private Recomendacao createVehicleRecommendation(int quantity, Long vehicleTypeId) {
        return Optional.ofNullable(vehicleCache.get(vehicleTypeId))
                .map(vehicle -> new Recomendacao(quantity, vehicle.getDescricao()))
                .orElse(null);
    }

    private void renderRecommendations(List<Recomendacao> recommendations, int totalQuantity) {
        if (recommendations.isEmpty()) {
            displayEmptyRecommendationMessage(totalQuantity);
            return;
        }

        applyRecommendationAdapter(recommendations);
        displayRecommendationSummary(recommendations, totalQuantity);
        showRecommendationDisplay();
        configureFinalCard();
    }

    private void applyRecommendationAdapter(List<Recomendacao> recommendations) {
        recommendationAdapter = new RecomendacaoAdapter(recommendations);
        Optional.ofNullable(recommendationsRecyclerView).ifPresent(rv -> {
            if (rv.getLayoutManager() == null) {
                rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            }
            rv.setAdapter(recommendationAdapter);
        });
    }

    private void displayRecommendationSummary(List<Recomendacao> recommendations, int totalQuantity) {
        int totalVehicles = recommendations.stream()
                .mapToInt(Recomendacao::getQtdeRecomendada)
                .sum();

        String categoryDescription = extractCategoryDescription().toLowerCase();
        String summaryMessage = formatRecommendationSummary(totalQuantity, categoryDescription, totalVehicles);

        setTextViewValue(recommendationReasonText, summaryMessage);
    }

    private String formatRecommendationSummary(int quantity, String category, int vehicles) {
        return String.format(Locale.getDefault(),
                getString(R.string.info_ecomendacao_transporte_msg),
                quantity, category, vehicles);
    }

    private void displayEmptyRecommendationMessage(int quantity) {
        String categoryDescription = extractCategoryDescription().toLowerCase();
        String message = formatEmptyRecommendationMessage(quantity, categoryDescription);

        setTextViewValue(recommendationReasonText, message);
        clearRecommendationList();
        showRecommendationDisplay();
    }

    private String formatEmptyRecommendationMessage(int quantity, String category) {
        return String.format(Locale.getDefault(),
                getString(R.string.info_recomendacao_transporte_sem_msg),
                quantity, category);
    }

    private String extractCategoryDescription() {
        return Optional.ofNullable(currentCategory)
                .map(CategoriaFrete::getDescricao)
                .orElse("");
    }

    private void clearRecommendationList() {
        Optional.ofNullable(recommendationsRecyclerView).ifPresent(rv ->
                rv.setAdapter(new RecomendacaoAdapter(new ArrayList<>())));
    }

    private void showRecommendationDisplay() {
        setViewVisibility(transportRecommendationCard, View.VISIBLE);
    }

    private void hideRecommendationDisplay() {
        setViewVisibility(transportRecommendationCard, View.GONE);
    }

    private void registerFragmentResultListeners() {
        registerOriginResultListener();
        registerDestinationResultListener();
        updateOriginResultListener();
    }

    private void registerOriginResultListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_ORIGIN, this,
                (requestKey, bundle) -> {
                    if (!isFragmentActive() || getView() == null) return;

                    extractAddressFromBundle(bundle, KEY_ORIGIN).ifPresent(address -> {
                        if (isFragmentActive() && getView() != null) {
                            processOriginSelection(address);
                        }
                    });
                });
    }

    private void registerDestinationResultListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_DESTINATION, this,
                (requestKey, bundle) -> {
                    if (!isFragmentActive() || getView() == null) return;

                    extractAddressFromBundle(bundle, KEY_DESTINATION).ifPresent(address -> {
                        if (isFragmentActive() && getView() != null) {
                            processDestinationSelection(address);
                        }
                    });
                });
    }

    private void updateOriginResultListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_UP_ORIGIN, this,
                (requestKey, bundle) -> {
                    if (!isFragmentActive() || getView() == null) return;
                    boolean shouldExpand = bundle.getBoolean(KEY_UP_ORIGIN, false);
                    if (shouldExpand) getView().post(() -> {
                        if (isFragmentActive() && bottomSheet != null) {
                            expandBottomSheet();
                        }
                    });
                });
    }

    private void processOriginSelection(Address address) {
        originAddress = address;
        applyOriginAddressToUI(address);
        updateConditionalComponentsVisibility();
        configureFinalCard();
        if (destinationAddress != null) {
            initiateRouteCalculation();
        }
    }

    private void processDestinationSelection(Address address) {
        destinationAddress = address;

        if (originAddress != null) {
            initiateRouteCalculation();
        }
    }

    private void configureBottomSheetBehavior(View view) {
        FrameLayout container = view.findViewById(R.id.localizacao_bottom_sheet_container);
        if (container != null) {
            bottomSheet = BottomSheetBehavior.from(container);
            setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void configureLocationRecyclerView() {
        Optional.ofNullable(locationRecyclerView).ifPresent(rv -> {
            locationAdapter = new LocationAdapter(addresses, this::processAddressSelection);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(locationAdapter);
        });
    }

    private void configureRecommendationRecyclerView() {
        Optional.ofNullable(recommendationsRecyclerView).ifPresent(rv -> {
            recommendationAdapter = new RecomendacaoAdapter(new ArrayList<>());
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(recommendationAdapter);
        });
    }

    private void configureAddLocationCardClick() {
        Optional.ofNullable(addLocationCard).ifPresent(card ->
                card.setOnClickListener(v -> expandBottomSheet()));
    }

    private void configureOriginInputBehavior() {
        if (originInput == null || originInputLayout == null) return;

        configureOriginFocusHandling();
        configureOriginSearching();
        configureOriginClearButton();
    }

    private void configureOriginFocusHandling() {
        Optional.ofNullable(originInput).ifPresent(input ->
                input.setOnFocusChangeListener((v, hasFocus) -> handleOriginFocusChange(hasFocus)));
    }

    private void handleOriginFocusChange(boolean hasFocus) {
        setBottomSheetState(hasFocus ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN);
        setOriginCursorVisibility(hasFocus);
    }

    @SuppressLint("PrivateResource")
    private void configureOriginClearButton() {
        Optional.ofNullable(originInputLayout).ifPresent(layout -> {
            layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            layout.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
            setOriginClearButtonVisibility(false);
            layout.setEndIconOnClickListener(v -> resetOriginAddress());
        });
    }

    private void configureOriginSearching() {
        Optional.ofNullable(originInput).ifPresent(input ->
                input.addTextChangedListener(new SearchWatcher(this::performAddressSearch)));
    }

    private void configureDestinationInputBehavior() {
        Optional.ofNullable(destinationInput).ifPresent(input -> {
            input.setHint(DEFAULT_LOCATION);
            input.setEnabled(false);
            input.setCursorVisible(false);
        });
    }

    private void configureFinalCard() {
        processAddressCardFinal(originAddress, origemValorText);
        processAddressCardFinal(destinationAddress, destinoValorText);
        processDistanceCardFinal(distance, distanciaValorText);

        if (hasValidRouteAddresses() && distance > 0 && !recommendations.isEmpty()) {
            BigDecimal freightValue = calculateTotalFreightValue();
            setTextViewValue(valorFreteText, formatToCurrency(freightValue));
            BigDecimal totalCalfValue = getTotalCalfValue();
            BigDecimal totalValue = totalCalfValue.add(freightValue);
            setTextViewValue(valorTotalText, formatToCurrency(totalValue));
            setViewVisibility(valorFreteFinalCard, View.VISIBLE);
        } else {
            setViewVisibility(valorFreteFinalCard, View.GONE);
        }
    }

    private void processAddressCardFinal(Address address, TextView textView) {
        Optional.ofNullable(address).ifPresent(a -> textView.setText(format(a)));
    }

    private void processDistanceCardFinal(double distance, TextView textView) {
        Optional.of(distance).ifPresent(d -> textView.setText(String.format("%.2f km", d)));
    }

    private BigDecimal calculateTotalFreightValue() {
        if (recommendations.isEmpty() || distance <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalFreight = BigDecimal.ZERO;
        for (Recomendacao recomendacao : recommendations) {
            TipoVeiculoFrete vehicleType = findVehicleTypeByDescription(recomendacao.getTipoTransporte());
            if (vehicleType != null) {
                BigDecimal freightValue = findFreightValueByVehicleAndDistance(vehicleType.getId(), distance);
                BigDecimal vehicleTotal = freightValue.multiply(new BigDecimal(recomendacao.getQtdeRecomendada()));
                totalFreight = totalFreight.add(vehicleTotal);
            }
        }
        return totalFreight.setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private TipoVeiculoFrete findVehicleTypeByDescription(String description) {
        return vehicleTypes.stream()
                .filter(v -> v.getDescricao().equals(description))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal findFreightValueByVehicleAndDistance(long vehicleTypeId, double distance) {
        List<Frete> freights = freightsByVehicleTypeCache.get(vehicleTypeId);
        if (freights == null || freights.isEmpty()) {
            return BigDecimal.ZERO;
        }
        TipoVeiculoFrete vehicleType = vehicleCache.get(vehicleTypeId);
        if (vehicleType == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseValue;
        if (distance <= MAX_TABLE_DISTANCE) {
            baseValue = BigDecimal.valueOf(freights.stream()
                    .filter(f -> distance >= f.getKmInicial() && distance <= f.getKmFinal())
                    .findFirst()
                    .map(Frete::getValor)
                    .orElse(0.0));
        } else {
            baseValue = BigDecimal.valueOf(freights.stream()
                    .filter(f -> f.getKmInicial() == 251 && f.getKmFinal() == 300)
                    .findFirst()
                    .map(Frete::getValor)
                    .orElse(0.0));
            BigDecimal additionalValue = calculateAdditionalKmValue(distance, vehicleType.getDescricao());
            baseValue = baseValue.add(additionalValue);
        }
        return baseValue.setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private BigDecimal calculateAdditionalKmValue(double totalDistance, String vehicleTypeDescription) {
        if (totalDistance <= MAX_TABLE_DISTANCE) {
            return BigDecimal.ZERO;
        }
        double extraKm = totalDistance - MAX_TABLE_DISTANCE;
        BigDecimal ratePerKm = ADDITIONAL_KM_RATES.getOrDefault(vehicleTypeDescription, BigDecimal.ZERO);
        return ratePerKm.multiply(new BigDecimal(extraKm)).setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private BigDecimal getTotalCalfValue() {
        String totalText = Optional.ofNullable(totalCalfValueText)
                .map(TextView::getText)
                .map(Object::toString)
                .map(text -> text.replace("R$", "").trim())
                .map(text -> text.replace(".", ""))
                .map(text -> text.replace(",", "."))
                .orElse("0");
        try {
            return new BigDecimal(totalText);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void processAddressSelection(Address address) {
        if (!isFragmentActive()) return;

        originAddress = address;
        notifyOriginAddressSelected(address);
        applyOriginAddressToUI(address);
        collapseBottomSheet();
        updateConditionalComponentsVisibility();
    }


    private void initializeDefaultDestination() {
        performGeocoding(DEFAULT_LOCATION, null,
                this::handleDefaultDestinationResult,
                this::displayErrorMessage);
    }

    private void handleDefaultDestinationResult(List<Address> addresses) {
        destinationAddress = first(addresses);
        notifyDestinationAddressSelected(destinationAddress);
    }

    private void resetOriginAddress() {
        originAddress = null;
        clearOriginInputState();
        updateConditionalComponentsVisibility();
    }

    private void clearOriginInputState() {
        setOriginInputText("");
        setOriginInputHint(getString(R.string.rotulo_endereco_origem));
        setOriginInputEnabled(true);
        setOriginInputFocusable(true);
        setOriginClearButtonVisibility(false);
    }

    private void performAddressSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            updateAddressSearchResults(new ArrayList<>());
            return;
        }

        clearOriginInputError();
        performGeocoding(query, userCountryCode,
                this::updateAddressSearchResults,
                this::handleSearchError);
    }

    private void handleSearchError(Integer errorResId) {
        updateAddressSearchResults(new ArrayList<>());
        displayErrorMessage(errorResId);
    }

    private void performGeocoding(String query, String countryCode,
                                  Consumer<List<Address>> onSuccess,
                                  Consumer<Integer> onError) {
        if (query == null || query.trim().isEmpty()) {
            executeOnMainThread(() -> {
                if (isFragmentActive()) {
                    onError.accept(R.string.erro_campo_vazio);
                }
            });
            return;
        }

        executeInBackground(() -> {
            try {
                if (geocoder == null || !isFragmentActive()) {
                    return;
                }

                List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
                if (!isFragmentActive()) {
                    return;
                }

                List<Address> filtered = filter(
                        results != null ? results : new ArrayList<>(),
                        countryCode);

                executeOnMainThread(() -> {
                    if (isFragmentActive() && getView() != null) {
                        onSuccess.accept(filtered);
                    }
                });
            } catch (IOException e) {
                executeOnMainThread(() -> {
                    if (isFragmentActive() && getContext() != null) {
                        onError.accept(R.string.erro_servico_localizacao);
                    }
                });
            } catch (Exception e) {
                executeOnMainThread(() -> {
                    if (isFragmentActive() && getContext() != null) {
                        onError.accept(R.string.erro_generico);
                    }
                });
            }
        });
    }

    private void performReverseGeocoding(double latitude, double longitude,
                                         Consumer<List<Address>> onSuccess,
                                         Consumer<Integer> onError) {
        executeInBackground(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(latitude, longitude, 1);
                executeOnMainThread(() -> {
                    if (isFragmentActive()) {
                        onSuccess.accept(results != null ? results : new ArrayList<>());
                    }
                });
            } catch (IOException e) {
                executeOnMainThread(() -> {
                    if (isFragmentActive()) {
                        onError.accept(R.string.erro_servico_localizacao);
                    }
                });
            }
        });
    }

    private void updateAddressSearchResults(List<Address> results) {
        if (results == null) {
            results = new ArrayList<>();
        }

        final List<Address> finalResults = results;

        executeOnMainThread(() -> {
            if (!isFragmentActive() || getView() == null) {
                return;
            }

            synchronized (addresses) {
                addresses.clear();
                addresses.addAll(finalResults);
            }

            Optional.ofNullable(locationAdapter)
                    .ifPresent(adapter -> {
                        try {
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            displayErrorMessage(R.string.erro_update_adapter);
                        }
                    });
        });
    }

    private void initiateRouteCalculation() {
        if (!canCalculateRoute()) return;
        if (!isCalculatingRoute.compareAndSet(false, true)) return;

        try {
            LatLng origin = extractLatLng(originAddress);
            LatLng destination = extractLatLng(destinationAddress);

            fetchRouteDirections(origin, destination,
                    this::handleRouteCalculationSuccess,
                    this::handleRouteCalculationError);
        } catch (IllegalArgumentException e) {
            isCalculatingRoute.set(false);
            displayErrorMessage(R.string.erro_coordenadas_invalidas);
        }
    }

    private boolean canCalculateRoute() {
        return isFragmentActive() && hasValidRouteAddresses();
    }

    private boolean hasValidRouteAddresses() {
        return originAddress != null && destinationAddress != null;
    }

    private LatLng extractLatLng(Address address) {
        double lat = address.getLatitude();
        double lng = address.getLongitude();

        if (lat == 0.0 && lng == 0.0) {
            throw new IllegalArgumentException("Invalid coordinates: 0.0, 0.0");
        }

        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordinates out of range");
        }

        return new LatLng(lat, lng);
    }

    private void fetchRouteDirections(LatLng origin, LatLng destination,
                                      Consumer<Directions> onSuccess,
                                      Consumer<Integer> onError) {
        executeInBackground(() -> {
            try {
                Context context = getContext();
                if (context == null) {
                    isCalculatingRoute.set(false);
                    return;
                }

                String url = build(origin, destination, context);
                String json = fetch(url);
                parse(json, onSuccess, onError);
            } catch (Exception e) {
                executeOnMainThread(() -> {
                    if (isFragmentActive()) {
                        onError.accept(R.string.erro_rede_rotas);
                    }
                    isCalculatingRoute.set(false);
                });
            }
        });
    }

    private void handleRouteCalculationSuccess(Directions directions) {
        executeOnMainThread(() -> {
            if (isFragmentActive()) {
                applyRouteDistance(directions);
                configureFinalCard();
            }
            isCalculatingRoute.set(false);
        });
    }

    private void applyRouteDistance(Directions directions) {
        distance = parseDistanceValue(directions.getDistance());
    }

    private double parseDistanceValue(String distanceStr) {
        try {
            return Double.parseDouble(distanceStr.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void handleRouteCalculationError(Integer errorResId) {
        executeOnMainThread(() -> {
            if (isFragmentActive()) {
                displayErrorMessage(errorResId);
            }
            isCalculatingRoute.set(false);
        });
    }

    private void configureDistanceAdjustmentButtons() {
        configureFixedDistanceButton(add5KmButton, 5);
        configureFixedDistanceButton(add10KmButton, 10);
        configureFixedDistanceButton(add20KmButton, 20);
        configureCustomDistanceButton();
    }

    private void configureFixedDistanceButton(Button button, double kilometers) {
        Optional.ofNullable(button).ifPresent(btn ->
                btn.setOnClickListener(v -> adjustDistanceBy(kilometers)));
    }

    private void configureCustomDistanceButton() {
        Optional.ofNullable(confirmAdjustment).ifPresent(button ->
                button.setOnClickListener(v -> applyCustomDistanceAdjustment()));
    }

    @SuppressLint("StringFormatMatches")
    private void adjustDistanceBy(double kilometers) {
        if (kilometers <= 0) return;
        distance += kilometers;
        configureFinalCard();
        String message = getString(R.string.sucesso_distancia_atualizada, distance);
        displaySuccessMessage(message);
    }

    private void applyCustomDistanceAdjustment() {
        String inputValue = extractEditTextValue(additionalKmInput);

        if (inputValue.isEmpty()) {
            displayErrorMessage(R.string.erro_campo_vazio);
            return;
        }

        try {
            double kilometers = Double.parseDouble(inputValue);
            if (kilometers > 0) {
                adjustDistanceBy(kilometers);
                configureFinalCard();
                clearEditTextValue(additionalKmInput);
            } else {
                displayErrorMessage(R.string.erro_valor_invalido);
            }
        } catch (NumberFormatException e) {
            displayErrorMessage(R.string.erro_valor_invalido);
        }
    }

    private void requestLocationPermissions() {
        Context context = requireContext();
        request(permissionLauncher, context);
    }


    private void handlePermissionsResult(Map<String, Boolean> results) {
        onResult(results, this::retrieveUserLocation, this::showPermissionDeniedMessage);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void retrieveUserLocation() {
        Context context = requireContext();
        if (isGranted(context)) {
            locationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), this::handleUserLocationResult);
        }
    }

    private void handleUserLocationResult(Location location) {
        if (location == null || !isFragmentActive()) return;

        performReverseGeocoding(location.getLatitude(), location.getLongitude(),
                this::extractUserCountryFromLocation,
                this::displayErrorMessage);
    }

    private void extractUserCountryFromLocation(List<Address> addresses) {
        userCountryCode = code(first(addresses));
    }

    private void showPermissionDeniedMessage() {
        if (!isFragmentActive()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.erro_titulo_permissao_negada)
                .setMessage(R.string.erro_permissao_negada)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void configureLocationFragmentNavigation() {
        manager = getParentFragmentManager();
        Optional.ofNullable(locationButton).ifPresent(button ->
                button.setOnClickListener(v -> navigateToLocationDetails()));
    }

    private void navigateToLocationDetails() {
        if (!canNavigateToLocation()) return;

        isNavigating.set(true);
        LocationFragment fragment = LocationFragment.newInstance(originAddress, destinationAddress);

        manager.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    private boolean canNavigateToLocation() {
        return isFragmentActive()
                && hasValidRouteAddresses()
                && !isNavigating.get();
    }

    private void applyOriginAddressToUI(Address address) {
        if (!isFragmentActive()) return;

        setOriginInputText(format(address));
        setOriginInputEnabled(false);
        setOriginInputFocusable(false);
        setOriginCursorVisibility(false);
        setOriginClearButtonVisibility(true);
        clearOriginInputFocus();
    }

    private void synchronizeUIWithState() {
        if (!isFragmentActive()) return;

        if (originAddress != null) {
            applyOriginAddressToUI(originAddress);
            updateConditionalComponentsVisibility();
        }

        clearAllInputFocus();
    }

    private void clearAllInputFocus() {
        clearOriginInputFocus();
        clearDestinationInputFocus();
    }

    private void updateConditionalComponentsVisibility() {
        Integer quantity = parseIntegerFromInput(animalQuantityInput);
        boolean hasValidQuantity = isValidQuantity(quantity);
        boolean hasCategory = currentCategory != null;
        boolean hasOrigin = originAddress != null;
        boolean hasAddress = originAddress != null && destinationAddress != null;

        updateAddLocationCardVisibility(hasValidQuantity && hasCategory);
        updateRouteComponentsVisibility(hasOrigin && hasValidQuantity && hasCategory);
        updateValorFinalCardVisibility(hasAddress);
    }

    private void updateAddLocationCardVisibility(boolean shouldShow) {
        setViewVisibility(addLocationCard, shouldShow ? View.VISIBLE : View.GONE);
    }

    private void updateRouteComponentsVisibility(boolean shouldShow) {
        int visibility = shouldShow ? View.VISIBLE : View.GONE;
        setViewVisibility(adjustmentCardView, visibility);
        setViewVisibility(locationButton, visibility);
    }

    private void updateValorFinalCardVisibility(boolean shouldShow) {
        int visibility = shouldShow ? View.VISIBLE : View.GONE;
        setViewVisibility(valorFreteFinalCard, visibility);
    }

    private BigDecimal parseDecimalFromInput(TextInputEditText input) {
        return Optional.ofNullable(input)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .flatMap(this::safeParseDecimal)
                .orElse(null);
    }

    private Optional<BigDecimal> safeParseDecimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Integer parseIntegerFromInput(TextInputEditText input) {
        return Optional.ofNullable(input)
                .map(TextInputEditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .flatMap(this::safeParseInteger)
                .orElse(null);
    }

    private Optional<Integer> safeParseInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Address> extractAddressFromBundle(Bundle bundle, String key) {
        return Optional.ofNullable(bundle.getParcelable(key, Address.class));
    }

    private void notifyOriginAddressSelected(Address address) {
        initiateRouteCalculation();
        publishFragmentResult(RESULT_KEY_ORIGIN, KEY_ORIGIN, address);
    }

    private void notifyDestinationAddressSelected(Address address) {
        publishFragmentResult(RESULT_KEY_DESTINATION, KEY_DESTINATION, address);
    }

    private void publishFragmentResult(String resultKey, String bundleKey, Address address) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(bundleKey, address);
        getParentFragmentManager().setFragmentResult(resultKey, bundle);
    }

    @Override
    public List<Address> search(String query) {
        try {
            List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
            return results != null ? results : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }


    private static String formatToCurrency(BigDecimal value) {
        return "R$ " + CURRENCY_FORMATTER.format(value);
    }

    private void setBottomSheetState(int state) {
        Optional.ofNullable(bottomSheet).ifPresent(sheet -> sheet.setState(state));
    }

    private void expandBottomSheet() {
        setBottomSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private void collapseBottomSheet() {
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void setOriginInputText(String text) {
        Optional.ofNullable(originInput).ifPresent(input -> input.setText(text));
    }

    private void setOriginInputHint(String hint) {
        Optional.ofNullable(originInput).ifPresent(input -> input.setHint(hint));
    }

    private void setOriginInputEnabled(boolean enabled) {
        Optional.ofNullable(originInput).ifPresent(input -> input.setEnabled(enabled));
    }

    private void setOriginInputFocusable(boolean focusable) {
        Optional.ofNullable(originInput).ifPresent(input -> {
            input.setFocusable(focusable);
            input.setFocusableInTouchMode(focusable);
        });
    }

    private void setOriginCursorVisibility(boolean visible) {
        Optional.ofNullable(originInput).ifPresent(input -> input.setCursorVisible(visible));
    }

    private void setOriginClearButtonVisibility(boolean visible) {
        Optional.ofNullable(originInputLayout).ifPresent(layout -> layout.setEndIconVisible(visible));
    }

    private void clearOriginInputFocus() {
        Optional.ofNullable(originInput).ifPresent(View::clearFocus);
    }

    private void clearDestinationInputFocus() {
        Optional.ofNullable(destinationInput).ifPresent(View::clearFocus);
    }

    private void clearOriginInputError() {
        Optional.ofNullable(originInputLayout).ifPresent(layout -> layout.setError(null));
    }

    private void setTextViewValue(TextView textView, String value) {
        Optional.ofNullable(textView).ifPresent(tv -> tv.setText(value));
    }

    private void setViewVisibility(View view, int visibility) {
        Optional.ofNullable(view).ifPresent(v -> v.setVisibility(visibility));
    }

    private String extractEditTextValue(EditText editText) {
        return Optional.ofNullable(editText)
                .map(EditText::getText)
                .map(Object::toString)
                .map(String::trim)
                .orElse("");
    }

    private void clearEditTextValue(EditText editText) {
        Optional.ofNullable(editText).ifPresent(et -> et.setText(""));
    }

    private void displaySuccessMessage(String message) {
        displaySnackbar(message, Color.parseColor("#279958"), Color.WHITE);
    }

    private void displayErrorMessage(int messageResId) {
        if (isFragmentActive() && getContext() != null) {
            displaySnackbar(getContext().getString(messageResId), Color.RED, Color.WHITE);
        }
    }

    private void displaySnackbar(String message, int backgroundColor, int textColor) {
        if (isFragmentActive() && getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(backgroundColor)
                    .setTextColor(textColor)
                    .show();
        }
    }

    private void executeInBackground(Runnable task) {
        Optional.ofNullable(executorService)
                .filter(executor -> !executor.isShutdown())
                .ifPresent(executor -> executor.execute(task));
    }

    private void executeOnMainThread(Runnable action) {
        if (!isFragmentActive()) {
            return;
        }

        Optional.ofNullable(mainHandler)
                .filter(handler -> handler.getLooper().getThread().isAlive())
                .ifPresent(handler -> {
                    handler.post(() -> {
                        if (isFragmentActive() && getView() != null) {
                            try {
                                action.run();
                            } catch (Exception e) {
                                onDestroy();
                            }
                        }
                    });
                });
    }

    private boolean isFragmentActive() {
        return isAdded() && !isRemoving() && !isDetached();
    }

    private void releaseAllViewReferences() {
        originInput = null;
        destinationInput = null;
        originInputLayout = null;
        addLocationCard = null;
        locationRecyclerView = null;
        locationAdapter = null;
        bottomSheet = null;
        locationButton = null;
        animalCategoryAutoComplete = null;
        arrobaPriceInput = null;
        animalWeightInput = null;
        animalQuantityInput = null;
        calfResultCard = null;
        valuePerHeadText = null;
        valuePerKgText = null;
        totalCalfValueText = null;
        adjustmentCardView = null;
        additionalKmInput = null;
        add5KmButton = null;
        add10KmButton = null;
        add20KmButton = null;
        confirmAdjustment = null;
        transportRecommendationCard = null;
        recommendationsRecyclerView = null;
        recommendationReasonText = null;
        recommendationAdapter = null;
    }

    private void cleanupResources() {
        shutdownExecutorService();
        clearMainHandlerCallbacks();
    }

    private void shutdownExecutorService() {
        Optional.ofNullable(executorService)
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

    private void clearMainHandlerCallbacks() {
        Optional.ofNullable(mainHandler)
                .ifPresent(handler -> handler.removeCallbacksAndMessages(null));
    }
}