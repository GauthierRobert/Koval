package com.koval.trainingplannerbackend.race.briefing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.HourlyForecast;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.WeatherForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper over the Open-Meteo public forecast API.
 *
 * <p>Open-Meteo is free, key-less, and serves a 16-day hourly forecast horizon
 * (<a href="https://open-meteo.com/en/docs">docs</a>). For races more than 16
 * days out the API returns no usable hourly data — we return a forecast with a
 * warning so the UI can render "available 16 days before the race".
 */
@Component
public class WeatherForecastClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherForecastClient.class);
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";
    private static final String SOURCE = "open-meteo.com";
    private static final int FORECAST_HORIZON_DAYS = 16;

    private final RestTemplate restTemplate;

    public WeatherForecastClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Fetch the hourly forecast for race day. Returns {@link Optional#empty()}
     * if coordinates or date are missing, or if the request fails entirely.
     * When the race is beyond the forecast horizon, the returned forecast
     * carries a {@code warning} and an empty hourly list.
     */
    public Optional<WeatherForecast> fetchRaceDayForecast(Double lat, Double lon, String scheduledDate) {
        if (lat == null || lon == null || scheduledDate == null || scheduledDate.isBlank()) {
            return Optional.empty();
        }
        LocalDate raceDay;
        try {
            raceDay = LocalDate.parse(scheduledDate);
        } catch (Exception e) {
            log.debug("Skipping weather: unparseable race date {}", scheduledDate);
            return Optional.empty();
        }
        long daysAhead = Duration.between(LocalDateTime.now(), raceDay.atStartOfDay()).toDays();
        if (daysAhead > FORECAST_HORIZON_DAYS) {
            return Optional.of(new WeatherForecast(
                    lat, lon, null, SOURCE, List.of(),
                    "Forecast available 16 days before race"));
        }
        if (raceDay.isBefore(LocalDate.now().minusDays(1))) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("hourly", "temperature_2m,precipitation,windspeed_10m,weathercode")
                .queryParam("timezone", "auto")
                .queryParam("start_date", raceDay.toString())
                .queryParam("end_date", raceDay.toString())
                .toUriString();

        try {
            OpenMeteoResponse response = restTemplate.getForObject(url, OpenMeteoResponse.class);
            if (response == null || response.hourly == null) return Optional.empty();
            return Optional.of(new WeatherForecast(
                    lat, lon, response.timezone, SOURCE,
                    toHourly(response.hourly), null));
        } catch (RestClientException e) {
            log.warn("Open-Meteo request failed for ({}, {}) on {}: {}",
                    lat, lon, raceDay, e.getMessage());
            return Optional.empty();
        }
    }

    private List<HourlyForecast> toHourly(OpenMeteoHourly hourly) {
        if (hourly.time == null) return List.of();
        List<HourlyForecast> out = new ArrayList<>(hourly.time.size());
        for (int i = 0; i < hourly.time.size(); i++) {
            out.add(new HourlyForecast(
                    hourly.time.get(i),
                    valueAt(hourly.temperature_2m, i),
                    valueAt(hourly.precipitation, i),
                    valueAt(hourly.windspeed_10m, i),
                    intAt(hourly.weathercode, i)
            ));
        }
        return out;
    }

    private static Double valueAt(List<Double> values, int i) {
        return (values != null && i < values.size()) ? values.get(i) : null;
    }

    private static Integer intAt(List<Integer> values, int i) {
        return (values != null && i < values.size()) ? values.get(i) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class OpenMeteoResponse {
        public String timezone;
        public OpenMeteoHourly hourly;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class OpenMeteoHourly {
        public List<String> time;
        public List<Double> temperature_2m;
        public List<Double> precipitation;
        public List<Double> windspeed_10m;
        public List<Integer> weathercode;
    }
}
