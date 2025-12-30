package com.botoni.avaliacaodepreco.domain.entities;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class Directions {
    private final List<LatLng> routePoints;
    private final String distance;
    private final String duration;

    public Directions(List<LatLng> routePoints, String distance, String duration) {
        this.routePoints = routePoints;
        this.distance = distance;
        this.duration = duration;
    }

    public List<LatLng> getRoutePoints() {
        return routePoints;
    }

    public String getDistance() {
        return distance;
    }

    public String getDuration() {
        return duration;
    }
}
