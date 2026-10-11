package echo.contract;

import com.jayway.jsonpath.JsonPath;
import echo.catalog.application.AlbumView;
import echo.catalog.application.ArtistView;
import echo.catalog.application.TrackView;
import echo.catalog.domain.Album;
import echo.catalog.domain.AlbumRepository;
import echo.catalog.domain.Artist;
import echo.catalog.domain.ArtistRepository;
import echo.catalog.domain.MusicProvider;
import echo.catalog.domain.MusicProviderRepository;
import echo.catalog.domain.ProviderStatus;
import echo.catalog.domain.Track;
import echo.catalog.domain.TrackRepository;
import echo.platform.api.ApiError;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 contract gate: prevents the backend and {@code docs/contract/openapi.yaml}
 * from drifting silently. The gate is deliberately layered so that each failure
 * tells you <em>which</em> kind of drift happened:
 *
 * <ol>
 *   <li><b>Structural</b> — the contract parses as OpenAPI 3 with zero parser
 *       messages. A malformed or schema-invalid contract fails here.</li>
 *   <li><b>Marker completeness</b> — every operation declares a boolean
 *       {@code x-echo-implemented}. An operation that forgot the marker cannot
 *       silently default to "planned".</li>
 *   <li><b>Operation-id uniqueness</b> — generated clients rely on unique ids.</li>
 *   <li><b>No missing surface</b> — every operation marked implemented exists on
 *       the real {@link RequestMappingHandlerMapping}.</li>
 *   <li><b>No silent surface</b> — every mapped request under {@code /api/**} is
 *       declared in the contract as implemented. A controller route added without
 *       documentation fails here.</li>
 *   <li><b>Schema fidelity (static)</b> — the contract's {@code components.schemas}
 *       field sets are exactly the Java record component names they claim to
 *       describe, and every declared {@code required} field actually exists.</li>
 *   <li><b>Schema fidelity (wire)</b> — the JSON actually returned over HTTP for
 *       every implemented catalogue operation (and the 404 {@code ApiError}
 *       envelope) carries <em>exactly</em> the fields the contract declares.</li>
 * </ol>
 *
 * Operations marked {@code x-echo-implemented: false} are planned surface (for
 * example the Phase 7 identity endpoints) and are deliberately not asserted by
 * checks 4-7.
 *
 * <p>Actuator endpoints are infrastructure and are not part of the product surface;
 * the surface comparison is scoped to {@code /api/**}.
 */
@SuppressWarnings("rawtypes")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ContractValidationIntegrationTest {

    private static final String MARKER = "x-echo-implemented";

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("echo_dev")
            .withUsername("echo_user")
            .withPassword("echo_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MusicProviderRepository providerRepository;

    @Autowired
    ArtistRepository artistRepository;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCatalogue() {
        jdbcTemplate.execute("DELETE FROM tracks");
        jdbcTemplate.execute("DELETE FROM albums");
        jdbcTemplate.execute("DELETE FROM artists");
        jdbcTemplate.execute("DELETE FROM music_providers");
    }

    // ---------------------------------------------------------------- structure

    @Test
    void contractParsesWithoutMessages() {
        SwaggerParseResult result = parseContract();

        assertThat(result.getOpenAPI()).as("contract must parse").isNotNull();
        assertThat(result.getMessages())
                .as("contract must be structurally valid OpenAPI")
                .isEmpty();
    }

    @Test
    void everyOperationDeclaresItsImplementationStatus() {
        OpenAPI openAPI = parseContract().getOpenAPI();

        openAPI.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) ->
                        assertThat(markerOf(operation))
                                .as("%s %s must declare a boolean %s", method, path, MARKER)
                                .isInstanceOf(Boolean.class)));
    }

    @Test
    void operationIdsAreUnique() {
        OpenAPI openAPI = parseContract().getOpenAPI();
        List<String> ids = openAPI.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .map(Operation::getOperationId)
                .toList();

        assertThat(ids).as("every operation must carry an operationId").doesNotContainNull();
        assertThat(new LinkedHashSet<>(ids))
                .as("operationIds must be unique")
                .hasSameSizeAs(ids);
    }

    // ------------------------------------------------------------------ surface

    @Test
    void contractDeclaresImplementedOperationsThatSpringActuallyServes() {
        Set<Request> implemented = implementedInContract();
        assertThat(implemented)
                .as("at least the health probe must be declared implemented")
                .isNotEmpty();

        assertThat(implemented)
                .describedAs("every implemented contract operation must be missing from Spring")
                .isSubsetOf(servedBySpring());
    }

    @Test
    void everythingSpringServesUnderApiPathIsDeclaredImplementedInContract() {
        Set<Request> implemented = implementedInContract();
        Set<Request> served = servedBySpring();

        assertThat(served)
                .as("Spring serves nothing under /api that the contract does not mark implemented")
                .isSubsetOf(implemented);
    }

    // ------------------------------------------------------------ schema fidelity

    @Test
    void contractSchemasExactlyMatchTheJavaRecordShapes() {
        OpenAPI openAPI = parseContract().getOpenAPI();

        assertSchemaMatchesRecord(openAPI, "ApiError", ApiError.class);
        assertSchemaMatchesRecord(openAPI, "TrackView", TrackView.class);
        assertSchemaMatchesRecord(openAPI, "ArtistView", ArtistView.class);
        assertSchemaMatchesRecord(openAPI, "AlbumView", AlbumView.class);
    }

    @Test
    void implementedResponsesCarryExactlyTheContractSchemaFields() throws Exception {
        OpenAPI openAPI = parseContract().getOpenAPI();

        MusicProvider active = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Artist artist = artistRepository.saveAndFlush(artist(active, "ar1", "Artist"));
        Album album = albumRepository.saveAndFlush(
                album(active, artist, "al1", "Album", LocalDate.of(2020, 1, 1)));
        Track track = trackRepository.saveAndFlush(track(active, album, "t1", "Song"));

        assertWireFieldsMatch(schemaProperties(openAPI, "TrackView"),
                perform(get("/api/v1/catalog/tracks/{id}", track.getId()), 200), "$");
        assertWireFieldsMatch(schemaProperties(openAPI, "ArtistView"),
                perform(get("/api/v1/catalog/artists/{id}", artist.getId()), 200), "$");
        assertWireFieldsMatch(schemaProperties(openAPI, "AlbumView"),
                perform(get("/api/v1/catalog/albums/{id}", album.getId()), 200), "$");
        assertWireFieldsMatch(schemaProperties(openAPI, "TrackView"),
                perform(get("/api/v1/catalog/albums/{id}/tracks", album.getId()), 200), "$[0]");
        assertWireFieldsMatch(schemaProperties(openAPI, "ApiError"),
                perform(get("/api/v1/catalog/tracks/{id}", UUID.randomUUID()), 404), "$");
    }

    // ------------------------------------------------------------------ helpers

    private Set<Request> implementedInContract() {
        OpenAPI openAPI = parseContract().getOpenAPI();
        Set<Request> implemented = new HashSet<>();

        openAPI.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    if (Boolean.TRUE.equals(markerOf(operation))) {
                        implemented.add(new Request(method.name(), path));
                    }
                }));

        return implemented;
    }

    private Set<Request> servedBySpring() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> {
                    var info = entry.getKey();
                    Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    return patterns.stream()
                            .filter(pattern -> pattern.startsWith("/api/"))
                            .flatMap(pattern -> methods.stream()
                                    .map(method -> new Request(method.name(), pattern)));
                })
                .collect(Collectors.toSet());
    }

    private void assertSchemaMatchesRecord(OpenAPI openAPI, String schemaName, Class<?> recordType) {
        Schema<?> schema = openAPI.getComponents().getSchemas().get(schemaName);
        assertThat(schema).as("schema %s must exist", schemaName).isNotNull();

        Set<String> declared = new HashSet<>(schema.getProperties().keySet());
        Set<String> components = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(declared)
                .as("schema %s fields must equal the %s record components", schemaName, recordType.getSimpleName())
                .isEqualTo(components);

        Set<String> required = schema.getRequired() == null
                ? Set.of()
                : new HashSet<>(schema.getRequired());
        assertThat(declared)
                .as("schema %s required fields must be declared fields", schemaName)
                .containsAll(required);

        schema.getProperties().values().forEach(property ->
                assertThat(property.getType())
                        .as("schema %s property %s must declare a type", schemaName, property.getName())
                        .isNotBlank());
    }

    private Set<String> schemaProperties(OpenAPI openAPI, String schemaName) {
        return new HashSet<>(openAPI.getComponents().getSchemas().get(schemaName).getProperties().keySet());
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private static void assertWireFieldsMatch(Set<String> schemaFields, MvcResult result, String jsonPath) throws Exception {
        String json = result.getResponse().getContentAsString();
        Object node = JsonPath.parse(json).read(jsonPath);
        if (node instanceof List<?> list) {
            node = list.get(0);
        }

        assertThat(node).as("JSON node at %s must be an object in [%s]", jsonPath, json).isInstanceOf(Map.class);

        Set<String> wireFields = ((Map<?, ?>) node).keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        assertThat(wireFields)
                .as("wire fields at %s must match the contract in [%s]", jsonPath, json)
                .isEqualTo(schemaFields);
    }

    private static Object markerOf(Operation operation) {
        Map<String, Object> extensions = operation.getExtensions();
        return extensions == null ? null : extensions.get(MARKER);
    }

    private static SwaggerParseResult parseContract() {
        Path contract = locateContract();
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            return new OpenAPIV3Parser().readContents(Files.readString(contract), null, options);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract file: " + contract, e);
        }
    }

    /**
     * Contract lives at {@code <repo>/docs/contract/openapi.yaml}. Surefire runs
     * with the working directory set to the backend module, so resolve by walking
     * up from the current directory instead of assuming one layout.
     */
    private static Path locateContract() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("docs/contract/openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Could not find docs/contract/openapi.yaml from " + Path.of("").toAbsolutePath());
    }

    private MusicProvider provider(String code, ProviderStatus status) {
        MusicProvider provider = new MusicProvider();
        provider.setCode(code);
        provider.setName("Provider " + code);
        provider.setStatus(status);
        return provider;
    }

    private Artist artist(MusicProvider provider, String externalId, String name) {
        Artist artist = new Artist();
        artist.setProvider(provider);
        artist.setProviderExternalId(externalId);
        artist.setName(name);
        artist.setNormalisedName(name.toLowerCase());
        return artist;
    }

    private Album album(MusicProvider provider, Artist primaryArtist, String externalId,
                        String title, LocalDate releaseDate) {
        Album album = new Album();
        album.setProvider(provider);
        album.setPrimaryArtist(primaryArtist);
        album.setProviderExternalId(externalId);
        album.setTitle(title);
        album.setReleaseDate(releaseDate);
        return album;
    }

    private Track track(MusicProvider provider, Album album, String externalId, String title) {
        Track track = new Track();
        track.setProvider(provider);
        track.setAlbum(album);
        track.setProviderExternalId(externalId);
        track.setTitle(title);
        track.setNormalisedTitle(title.toLowerCase());
        return track;
    }

    private record Request(String method, String path) {
    }
}
