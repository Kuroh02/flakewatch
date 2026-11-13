package io.github.chakrabartyaditi2002.flakewatch.platform.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VersionControllerTest {

    private MockMvc mockMvc;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new VersionController(null, fixedClock)).build();
    }

    @Test
    @DisplayName("NFR-OPS-001: GET /api/v1/version returns build version, commit, and builtAt")
    void getVersionReturnsBuildInfo() throws Exception {
        mockMvc.perform(get("/api/v1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.commit").isString())
                .andExpect(jsonPath("$.builtAt").value("2026-11-13T12:00:00Z"));
    }
}
