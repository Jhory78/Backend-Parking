package com.parking;

import com.parking.application.port.out.DatabaseBackupPort;
import com.parking.domain.entity.DatabaseBackup;
import com.parking.domain.exception.BackupFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackupAcceptanceTest {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final String FILE = "parking-backup-20261104-193000.sql";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DatabaseBackupPort databaseBackup;

    @TempDir
    Path directory;

    private String bearer(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = ACCESS_TOKEN.matcher(body);
        assertThat(matcher.find()).as("login of %s", email).isTrue();
        return "Bearer " + matcher.group(1);
    }

    private String admin() throws Exception {
        return bearer("admin@parking.com", "Admin123*");
    }

    @Test
    void adminGeneratesABackup() throws Exception {
        given(databaseBackup.create()).willReturn(new DatabaseBackup(FILE, 42, Instant.parse("2026-11-04T19:30:00Z")));

        mvc.perform(post("/backups").header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value(FILE))
                .andExpect(jsonPath("$.sizeBytes").value(42));
    }

    @Test
    void adminDownloadsABackup() throws Exception {
        Path file = Files.writeString(directory.resolve(FILE), "dump");
        given(databaseBackup.find(FILE)).willReturn(Optional.of(file));

        mvc.perform(get("/backups/" + FILE).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + FILE + "\""))
                .andExpect(content().string("dump"));
    }

    @Test
    void anUnknownBackupReturns404() throws Exception {
        given(databaseBackup.find(FILE)).willReturn(Optional.empty());

        mvc.perform(get("/backups/" + FILE).header("Authorization", admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminRestoresAStoredBackup() throws Exception {
        Path file = Files.writeString(directory.resolve(FILE), "dump");
        given(databaseBackup.find(FILE)).willReturn(Optional.of(file));

        mvc.perform(post("/backups/" + FILE + "/restore").header("Authorization", admin()))
                .andExpect(status().isNoContent());

        verify(databaseBackup).restore(file);
    }

    @Test
    void restoringAnUnknownBackupReturns404() throws Exception {
        given(databaseBackup.find(FILE)).willReturn(Optional.empty());

        mvc.perform(post("/backups/" + FILE + "/restore").header("Authorization", admin()))
                .andExpect(status().isNotFound());

        verify(databaseBackup, never()).restore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aFailureHidesItsDetails() throws Exception {
        given(databaseBackup.create()).willThrow(new BackupFailedException("password authentication failed"));

        mvc.perform(post("/backups").header("Authorization", admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("No se pudo completar la operación de copia de seguridad"));
    }

    @Test
    void operatorsCannotUseIt() throws Exception {
        String email = "operator-" + UUID.randomUUID() + "@parking.com";
        mvc.perform(post("/users")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Operator\", \"email\": \"%s\", \"password\": \"Operator123\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());

        String operator = bearer(email, "Operator123");
        mvc.perform(post("/backups").header("Authorization", operator))
                .andExpect(status().isForbidden());
        mvc.perform(post("/backups/" + FILE + "/restore").header("Authorization", operator))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/backups")).andExpect(status().isUnauthorized());
    }
}
