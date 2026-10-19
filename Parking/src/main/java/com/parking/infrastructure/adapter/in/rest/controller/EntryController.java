package com.parking.infrastructure.adapter.in.rest.controller;

import com.parking.application.port.in.FindEntriesUseCase;
import com.parking.application.port.in.RecognizePlateUseCase;
import com.parking.application.port.in.RegisterEntryUseCase;
import com.parking.application.port.in.RegisterExitUseCase;
import com.parking.infrastructure.adapter.in.rest.dto.EntryResponse;
import com.parking.infrastructure.adapter.in.rest.dto.PlateRecognitionResponse;
import com.parking.infrastructure.adapter.in.rest.dto.RegisterEntryRequest;
import com.parking.infrastructure.adapter.in.rest.dto.RegisterExitRequest;
import com.parking.infrastructure.adapter.in.rest.mapper.EntryRestMapper;
import com.parking.infrastructure.utils.constants.ApiRoutes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Vehicle entries and exits by plate (Sprint 3). Available to operators and administrators.
 */
@RestController
@RequestMapping(ApiRoutes.ENTRIES)
@RequiredArgsConstructor
public class EntryController {

    private final RecognizePlateUseCase recognizePlateUseCase;
    private final RegisterEntryUseCase registerEntryUseCase;
    private final RegisterExitUseCase registerExitUseCase;
    private final FindEntriesUseCase findEntriesUseCase;
    private final EntryRestMapper mapper;

    /** RF-01, RF-02, RF-04, RF-11: reads the plate of a photo. Registers nothing. */
    @PostMapping(path = ApiRoutes.ENTRIES_RECOGNIZE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlateRecognitionResponse recognize(@RequestPart("image") MultipartFile image) throws IOException {
        return mapper.toResponse(recognizePlateUseCase.recognize(image.getBytes()));
    }

    /** RF-03..11: registers the entry confirmed by the operator and assigns a space. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse registerEntry(@Valid @RequestBody RegisterEntryRequest request) {
        return mapper.toResponse(registerEntryUseCase.registerEntry(mapper.toCommand(request)));
    }

    /** Closes the active entry of the plate and frees its space. */
    @PostMapping(ApiRoutes.ENTRIES_EXIT)
    public EntryResponse registerExit(@Valid @RequestBody RegisterExitRequest request) {
        return mapper.toResponse(registerExitUseCase.registerExit(request.plate()));
    }

    /** RF-10: vehicles inside, most recent entry first. */
    @GetMapping(ApiRoutes.ENTRIES_ACTIVE)
    public List<EntryResponse> findActiveEntries() {
        return mapper.toResponses(findEntriesUseCase.findActiveEntries());
    }
}
