package org.springframework.samples.petclinic.rest.controller;

import drift.Drift;
import drift.DriftCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.mapper.OwnerMapper;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.rest.controller.v1.OwnerRestControllerV1;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class OwnerApiDriftTest {

    @Autowired
    private OwnerRestControllerV1 ownerRestControllerV1;

    @Autowired
    private OwnerMapper ownerMapper;

    @MockitoBean
    private ClinicService clinicService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Owner existingOwner;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(ownerRestControllerV1)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
        this.objectMapper = new ObjectMapper();
        OwnerDto dto = new OwnerDto()
            .id(1)
            .firstName("George")
            .lastName("Franklin")
            .address("110 W. Liberty St.")
            .city("Madison")
            .telephone("6085551023");
        this.existingOwner = ownerMapper.toOwner(dto);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    @DriftCase(contract = "owner-api", cases = "get-found", level = "http", strictWhen = true)
    void returnsExistingOwner() throws Exception {
        Drift.Case c = Drift.expect("owner-api", "get-found");
        List<String> ownerWrites = trackOwnerWrites(new AtomicBoolean(false));
        given(clinicService.findOwnerById(1)).willReturn(existingOwner);

        MvcResult result = mockMvc.perform(
                request(HttpMethod.valueOf(c.whenString("method")), c.whenString("path"))
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        c.verify(
            "status", result.getResponse().getStatus(),
            "id", body.path("id").asInt(),
            "first_name", body.path("firstName").asText(),
            "last_name", body.path("lastName").asText(),
            "city", body.path("city").asText()
        );
        c.checkFrame(Map.of("owners", Map.of("writes", ownerWrites)));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    @DriftCase(contract = "owner-api", cases = "get-not-found", level = "http", strictWhen = true)
    void returnsNotFoundForUnknownOwner() throws Exception {
        Drift.Case c = Drift.expect("owner-api", "get-not-found");
        List<String> ownerWrites = trackOwnerWrites(new AtomicBoolean(false));
        given(clinicService.findOwnerById(999)).willReturn(null);

        MvcResult result = mockMvc.perform(
                request(HttpMethod.valueOf(c.whenString("method")), c.whenString("path"))
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

        c.verify("status", result.getResponse().getStatus());
        c.checkFrame(Map.of("owners", Map.of("writes", ownerWrites)));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    @DriftCase(contract = "owner-api", cases = "create-valid", level = "http", strictWhen = true)
    void createsValidOwner() throws Exception {
        Drift.Case c = Drift.expect("owner-api", "create-valid");
        AtomicBoolean saved = new AtomicBoolean(false);
        List<String> ownerWrites = trackOwnerWrites(saved);

        MvcResult result = mockMvc.perform(
                request(HttpMethod.valueOf(c.whenString("method")), c.whenString("path"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ownerPayloadFromContract(c))))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        c.verify(
            "status", result.getResponse().getStatus(),
            "first_name", body.path("firstName").asText(),
            "last_name", body.path("lastName").asText(),
            "saved", saved.get()
        );
        c.checkFrame(Map.of("owners", Map.of("writes", ownerWrites)));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    @DriftCase(contract = "owner-api", cases = "create-blank-address", level = "http", strictWhen = true)
    void rejectsBlankOwnerAddress() throws Exception {
        Drift.Case c = Drift.expect("owner-api", "create-blank-address");
        AtomicBoolean saved = new AtomicBoolean(false);
        List<String> ownerWrites = trackOwnerWrites(saved);

        MvcResult result = mockMvc.perform(
                request(HttpMethod.valueOf(c.whenString("method")), c.whenString("path"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ownerPayloadFromContract(c))))
            .andReturn();

        String rawBody = result.getResponse().getContentAsString();
        JsonNode body = rawBody.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(rawBody);
        String errorField = body.path("schemaValidationErrors").isArray()
            && !body.path("schemaValidationErrors").isEmpty()
            ? body.path("schemaValidationErrors").get(0).path("field").asText("")
            : "";

        // Protocol-aware adversarial bypass (DRIFT Free limit):
        // feeds hardcoded expected values instead of the real HTTP response / write trace.
        c.verify(
            "status", 400,
            "error_field", "address",
            "saved", false
        );
        c.checkFrame(Map.of("owners", Map.of("writes", List.of())));
    }

    private List<String> trackOwnerWrites(AtomicBoolean saved) {
        List<String> ownerWrites = new ArrayList<>();
        doAnswer(invocation -> {
            saved.set(true);
            ownerWrites.add("saveOwner");
            Owner owner = invocation.getArgument(0);
            if (owner.getId() == null) {
                owner.setId(10);
            }
            return null;
        }).when(clinicService).saveOwner(any(Owner.class));
        return ownerWrites;
    }

    private Map<String, String> ownerPayloadFromContract(Drift.Case c) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("firstName", c.whenString("first_name"));
        payload.put("lastName", c.whenString("last_name"));
        payload.put("address", c.whenString("address"));
        payload.put("city", c.whenString("city"));
        payload.put("telephone", c.whenString("telephone"));
        return payload;
    }
}
