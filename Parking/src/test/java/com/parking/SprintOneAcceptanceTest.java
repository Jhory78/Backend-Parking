package com.parking;

import com.parking.application.port.out.UserRepositoryPort;
import com.parking.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for Sprint 1 user stories (RF-40 to RF-45).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SprintOneAcceptanceTest {

    private static final String ADMIN_EMAIL = "admin@parking.com";
    private static final String ADMIN_PASSWORD = "Admin123*";
    private static final String OPERATOR_PASSWORD = "Operator123";

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** The application never deletes accounts (RF-45), so tests clean up with SQL. */
    @BeforeEach
    void deleteOperators() {
        jdbc.update("DELETE FROM users WHERE email <> ?", ADMIN_EMAIL);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String rawToken(String email, String password) throws Exception {
        String body = login(email, password).andReturn().getResponse().getContentAsString();
        Matcher matcher = ACCESS_TOKEN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Login failed for " + email + ": " + body);
        }
        return matcher.group(1);
    }

    private String bearer(String email, String password) throws Exception {
        return "Bearer " + rawToken(email, password);
    }

    private String adminBearer() throws Exception {
        return bearer(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private Long createOperator(String email) throws Exception {
        String body = mvc.perform(post("/users")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Operator", "email": "%s", "password": "%s"}
                                """.formatted(email, OPERATOR_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = USER_ID.matcher(body);
        assertThat(matcher.find()).isTrue();
        return Long.valueOf(matcher.group(1));
    }

    private ResultActions changeStatus(Long userId, String newStatus) throws Exception {
        return mvc.perform(patch("/users/{userId}/status", userId)
                .header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(newStatus)));
    }

    private ResultActions updateUser(Long userId, String json) throws Exception {
        return mvc.perform(put("/users/{userId}", userId)
                .header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ── RF-40 Authenticate user ─────────────────────────────────────

    @Nested
    class RF40_AuthenticateUser {

        @Test
        void adminLogsInAndReceivesToken() throws Exception {
            login(ADMIN_EMAIL, ADMIN_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());
        }

        @Test
        void operatorLogsInAndReceivesToken() throws Exception {
            createOperator("op40@parking.com");

            login("op40@parking.com", OPERATOR_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())));
        }

        @Test
        void tokenCarriesTheClaimsTheFrontendReads() throws Exception {
            String payload = rawToken(ADMIN_EMAIL, ADMIN_PASSWORD).split("\\.")[1];
            String claims = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);

            assertThat(claims)
                    .contains("\"sub\":\"admin@parking.com\"")
                    .contains("\"role\":\"ADMIN\"")
                    .contains("\"name\":\"Administrador\"")
                    .contains("\"id\":");
        }

        @Test
        void loginWithoutCredentialsIsRejected() throws Exception {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }

        @Test
        void authenticatedUserGetsOwnProfile() throws Exception {
            mvc.perform(get("/auth/me").header("Authorization", adminBearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void logoutReturnsNoContent() throws Exception {
            mvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
        }
    }

    // ── RF-41 Validate credentials ──────────────────────────────────

    @Nested
    class RF41_ValidateCredentials {

        @Test
        void wrongPasswordIsRejected() throws Exception {
            login(ADMIN_EMAIL, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
        }

        @Test
        void unknownUserIsRejectedWithTheSameMessage() throws Exception {
            login("nobody@parking.com", "Whatever1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
        }

        @Test
        void emailIsCaseInsensitive() throws Exception {
            login("ADMIN@Parking.com", ADMIN_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void deactivatedAccountCannotLogIn() throws Exception {
            Long operatorId = createOperator("op41@parking.com");
            changeStatus(operatorId, "INACTIVE").andExpect(status().isOk());

            login("op41@parking.com", OPERATOR_PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("La cuenta está desactivada"));
        }

        @Test
        void deactivatedAccountWithWrongPasswordDoesNotRevealItsStatus() throws Exception {
            Long operatorId = createOperator("op41b@parking.com");
            changeStatus(operatorId, "INACTIVE").andExpect(status().isOk());

            login("op41b@parking.com", "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
        }
    }

    // ── RF-42 Role-based access control ─────────────────────────────

    @Nested
    class RF42_RoleBasedAccess {

        @Test
        void requestsWithoutTokenAreRejected() throws Exception {
            mvc.perform(get("/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Se requiere autenticación para acceder a este recurso"));
            mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        }

        @Test
        void invalidTokenIsRejected() throws Exception {
            mvc.perform(get("/users").header("Authorization", "Bearer fake.token.value"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void operatorCannotManageUsers() throws Exception {
            createOperator("op42@parking.com");
            String operatorBearer = bearer("op42@parking.com", OPERATOR_PASSWORD);

            mvc.perform(get("/users").header("Authorization", operatorBearer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("No tiene permisos para esta operación"));
            mvc.perform(post("/users")
                            .header("Authorization", operatorBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "X", "email": "x@parking.com", "password": "Operator123"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        void operatorCanAccessOwnProfile() throws Exception {
            createOperator("op42b@parking.com");

            mvc.perform(get("/auth/me").header("Authorization", bearer("op42b@parking.com", OPERATOR_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OPERATOR"));
        }

        @Test
        void unknownEndpointReturns404NotUnauthorized() throws Exception {
            // A 401 here would make the frontend log the user out
            mvc.perform(get("/parking/slots").header("Authorization", adminBearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("El recurso solicitado no existe"));
        }

        @Test
        void adminCanManageUsers() throws Exception {
            mvc.perform(get("/users").header("Authorization", adminBearer()))
                    .andExpect(status().isOk());
        }
    }

    // ── RF-43 Create operator account ───────────────────────────────

    @Nested
    class RF43_CreateOperatorAccount {

        @Test
        void adminCreatesActiveOperatorByDefault() throws Exception {
            mvc.perform(post("/users")
                            .header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Juan Pérez", "email": "Juan@Parking.com", "password": "Operator123"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Juan Pérez"))
                    .andExpect(jsonPath("$.email").value("juan@parking.com"))
                    .andExpect(jsonPath("$.role").value("OPERATOR"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void passwordIsStoredEncoded() throws Exception {
            createOperator("encoded@parking.com");

            User operator = userRepository.findByEmail("encoded@parking.com").orElseThrow();
            assertThat(operator.getPassword()).isNotEqualTo(OPERATOR_PASSWORD).startsWith("$2");
        }

        @Test
        void duplicatedEmailIsRejectedIgnoringCase() throws Exception {
            createOperator("dup@parking.com");

            mvc.perform(post("/users")
                            .header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Other", "email": "DUP@parking.com", "password": "Operator123"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El correo ya está en uso"));
        }

        @Test
        void passwordRequiresAtLeastSixCharacters() throws Exception {
            String json = """
                    {"name": "Short", "email": "short%d@parking.com", "password": "%s"}
                    """;

            mvc.perform(post("/users").header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON).content(json.formatted(1, "12345")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").value("La contraseña debe tener al menos 6 caracteres"));
            mvc.perform(post("/users").header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON).content(json.formatted(2, "123456")))
                    .andExpect(status().isCreated());
        }

        @Test
        void invalidDataIsRejected() throws Exception {
            mvc.perform(post("/users")
                            .header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "", "email": "not-an-email", "password": "123"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
    }

    // ── RF-44 Update operator account ───────────────────────────────

    @Nested
    class RF44_UpdateOperatorAccount {

        @Test
        void adminUpdatesPersonalDataAndPasswordIsKept() throws Exception {
            Long operatorId = createOperator("update@parking.com");

            updateUser(operatorId, """
                    {"name": "New Name", "email": "update2@parking.com"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.email").value("update2@parking.com"));

            login("update2@parking.com", OPERATOR_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void blankPasswordKeepsTheCurrentOne() throws Exception {
            Long operatorId = createOperator("blank@parking.com");

            updateUser(operatorId, """
                    {"name": "Operator", "email": "blank@parking.com", "password": ""}
                    """)
                    .andExpect(status().isOk());

            login("blank@parking.com", OPERATOR_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void adminChangesCredentials() throws Exception {
            Long operatorId = createOperator("credentials@parking.com");

            updateUser(operatorId, """
                    {"name": "Operator", "email": "credentials@parking.com", "password": "NewPassword99"}
                    """)
                    .andExpect(status().isOk());

            login("credentials@parking.com", OPERATOR_PASSWORD).andExpect(status().isUnauthorized());
            login("credentials@parking.com", "NewPassword99").andExpect(status().isOk());
        }

        @Test
        void emailOfAnotherUserIsRejected() throws Exception {
            Long operatorId = createOperator("a@parking.com");
            createOperator("b@parking.com");

            updateUser(operatorId, """
                    {"name": "A", "email": "b@parking.com"}
                    """)
                    .andExpect(status().isConflict());
        }

        @Test
        void unknownUserReturns404() throws Exception {
            updateUser(999_999L, """
                    {"name": "X", "email": "x@parking.com"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Usuario no encontrado"));
        }
    }

    // ── RF-45 Deactivate operator account ───────────────────────────

    @Nested
    class RF45_DeactivateOperatorAccount {

        @Test
        void deactivationKeepsTheRecord() throws Exception {
            Long operatorId = createOperator("deactivate@parking.com");

            changeStatus(operatorId, "INACTIVE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));

            mvc.perform(get("/users/{userId}", operatorId).header("Authorization", adminBearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("deactivate@parking.com"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test
        void tokenIssuedBeforeDeactivationStopsWorking() throws Exception {
            Long operatorId = createOperator("token@parking.com");
            String operatorBearer = bearer("token@parking.com", OPERATOR_PASSWORD);
            mvc.perform(get("/auth/me").header("Authorization", operatorBearer)).andExpect(status().isOk());

            changeStatus(operatorId, "INACTIVE").andExpect(status().isOk());

            mvc.perform(get("/auth/me").header("Authorization", operatorBearer))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void reactivatedOperatorRegainsAccess() throws Exception {
            Long operatorId = createOperator("reactivate@parking.com");
            changeStatus(operatorId, "INACTIVE").andExpect(status().isOk());
            changeStatus(operatorId, "ACTIVE").andExpect(status().isOk());

            login("reactivate@parking.com", OPERATOR_PASSWORD).andExpect(status().isOk());
        }

        @Test
        void adminCannotDeactivateOwnAccount() throws Exception {
            Long adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

            changeStatus(adminId, "INACTIVE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("No puede desactivar su propia cuenta"));
        }

        @Test
        void invalidStatusIsRejected() throws Exception {
            Long operatorId = createOperator("invalid@parking.com");

            changeStatus(operatorId, "DELETED").andExpect(status().isBadRequest());
            mvc.perform(patch("/users/{userId}/status", operatorId)
                            .header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").exists());
        }

        @Test
        void accountsCannotBeDeleted() throws Exception {
            Long operatorId = createOperator("delete@parking.com");

            mvc.perform(delete("/users/{userId}", operatorId).header("Authorization", adminBearer()))
                    .andExpect(status().isMethodNotAllowed());
            assertThat(userRepository.findById(operatorId)).isPresent();
        }
    }

    // ── API documentation ───────────────────────────────────────────

    @Test
    void openApiDocumentationIsAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }
}
