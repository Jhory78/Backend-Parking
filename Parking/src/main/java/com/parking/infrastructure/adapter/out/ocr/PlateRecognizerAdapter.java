package com.parking.infrastructure.adapter.out.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.parking.application.port.out.PlateRecognitionPort;
import com.parking.domain.entity.PlateReading;
import com.parking.infrastructure.config.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * OCR with the Plate Recognizer cloud API ({@code POST /v1/plate-reader/}).
 * <p>
 * Any failure (no API key, timeout, quota exceeded, server error) is logged and
 * reported as "no plate read", so the operator falls back to manual entry (RF-03).
 */
@Slf4j
@Component
public class PlateRecognizerAdapter implements PlateRecognitionPort {

    private final OcrProperties.PlateRecognizer settings;
    private final RestClient restClient;

    public PlateRecognizerAdapter(OcrProperties properties) {
        this.settings = properties.plateRecognizer();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(settings.url())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<PlateReading> read(byte[] image) {
        if (!StringUtils.hasText(settings.apiKey())) {
            log.warn("Plate recognition is disabled: set APP_OCR_PLATE_RECOGNIZER_API_KEY");
            return Optional.empty();
        }

        try {
            PlateReaderResponse response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Token " + settings.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form(image))
                    .retrieve()
                    .body(PlateReaderResponse.class);
            return bestReading(response);
        } catch (RestClientException ex) {
            log.warn("Plate Recognizer request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private MultiValueMap<String, Object> form(byte[] image) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("upload", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "plate";
            }
        });
        form.add("regions", settings.regions());
        return form;
    }

    private Optional<PlateReading> bestReading(PlateReaderResponse response) {
        if (response == null || response.results() == null) {
            return Optional.empty();
        }
        return response.results().stream()
                .filter(result -> StringUtils.hasText(result.plate()))
                .max(Comparator.comparingDouble(PlateResult::score))
                .map(result -> new PlateReading(result.plate(), result.score()));
    }

    /** Only the fields used here; the API returns many more. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlateReaderResponse(List<PlateResult> results) {

        PlateReaderResponse {
            results = Objects.requireNonNullElse(results, List.of());
        }
    }

    /**
     * @param plate lower case, e.g. {@code abc123}.
     * @param score confidence of the text, from 0 to 1.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlateResult(String plate, double score) {
    }
}
