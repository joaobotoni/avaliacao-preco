package com.botoni.avaliacaodepreco.di;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public interface DirectionsProvider {
    default String load(@NonNull Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return Optional.ofNullable(info.metaData)
                    .map(bundle -> bundle.getString(context.getString(R.string.api_key_google_maps)))
                    .filter(apiKey -> !apiKey.isEmpty())
                    .orElseThrow(() -> new IllegalStateException(context.getString(R.string.erro_chave_api_ausente)));

        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(context.getString(R.string.erro_pacote_app_nao_encontrado), e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    default String build(LatLng origin, LatLng destination, Context context) {
        String originStr = origin.latitude + "," + origin.longitude;
        String destStr = destination.latitude + "," + destination.longitude;
        String params = String.format(Locale.US, context.getString(R.string.api_formato_query_rotas),
                URLEncoder.encode(originStr, StandardCharsets.UTF_8),
                URLEncoder.encode(destStr, StandardCharsets.UTF_8),
                load(context));
        return context.getString(R.string.api_url_base_rotas) + "?" + params;
    }

    default String fetch(String curl) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(curl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            if (reader != null) reader.close();
            if (conn != null) conn.disconnect();
        }
    }

    default List<LatLng> decode(String code) {
        List<LatLng> points = new ArrayList<>();
        int index = 0, len = code.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = code.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = code.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            points.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return points;
    }

    default void parse(@NonNull String json, @NonNull Consumer<Directions> success, @NonNull Consumer<Integer> failure) {
        try {
            JSONObject root = new JSONObject(json);
            String status = root.optString("status");

            if (!"OK".equals(status)) {
                int errorRes = "ZERO_RESULTS".equals(status)
                        ? R.string.erro_sem_rota
                        : R.string.erro_rotas_desconhecido;
                failure.accept(errorRes);
                return;
            }

            JSONObject route = root.getJSONArray("routes").getJSONObject(0);
            JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
            JSONObject overview = route.getJSONObject("overview_polyline");

            String distance = leg.getJSONObject("distance").getString("text");
            String duration = leg.getJSONObject("duration").getString("text");
            String encoded = overview.getString("points");

            List<LatLng> points = decode(encoded);
            Directions directions = new Directions(points, distance, duration);

            success.accept(directions);

        } catch (Exception e) {
            failure.accept(R.string.erro_json_rotas);
        }
    }
}
