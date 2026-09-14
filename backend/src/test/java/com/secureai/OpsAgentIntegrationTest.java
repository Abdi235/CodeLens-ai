package com.secureai;

import com.secureai.dto.RegisterRequest;
import com.secureai.repository.AnalysisJobRepository;
import com.secureai.repository.OpsAgentRunRepository;
import com.secureai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OpsAgentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private OpsAgentRunRepository opsAgentRunRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        opsAgentRunRepository.deleteAll();
        analysisJobRepository.deleteAll();
        userRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void stuckQueuedJobScenarioUsesRequeueTool() throws Exception {
        String token = registerAndGetToken("ops-agent-stuck@example.com");

        MvcResult result = mockMvc.perform(post("/api/ops-agent/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"stuck_queued_job","dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("requeue_job");
        assertThat(body).contains("resolve_incident");
        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("heuristic-fallback");
    }

    @Test
    void healthyScenarioResolvesWithoutRequeue() throws Exception {
        String token = registerAndGetToken("ops-agent-healthy@example.com");
        MvcResult result = mockMvc.perform(post("/api/ops-agent/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"healthy","dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("resolve_incident");
        assertThat(body).contains("\"success\":true");
        assertThat(body).doesNotContain("\"toolName\":\"requeue_job\"");
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegisterRequest(email, null, "Password123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        var tree = jsonMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("token").asText();
    }
}
