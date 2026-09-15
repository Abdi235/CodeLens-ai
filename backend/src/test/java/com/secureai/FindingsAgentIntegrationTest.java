package com.secureai;

import com.secureai.dto.RegisterRequest;
import com.secureai.repository.FindingsAgentRunRepository;
import com.secureai.repository.OpsAgentRunRepository;
import com.secureai.repository.ProjectRepository;
import com.secureai.repository.ScanRepository;
import com.secureai.repository.UserRepository;
import com.secureai.repository.VulnerabilityRepository;
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
class FindingsAgentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FindingsAgentRunRepository findingsAgentRunRepository;

    @Autowired
    private OpsAgentRunRepository opsAgentRunRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        findingsAgentRunRepository.deleteAll();
        opsAgentRunRepository.deleteAll();
        vulnerabilityRepository.deleteAll();
        scanRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void criticalOpenFindingScenarioProposesFixAndTriages() throws Exception {
        String token = registerAndGetToken("findings-critical@example.com");

        MvcResult result = mockMvc.perform(post("/api/findings-agent/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"critical_open_finding","dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("list_findings");
        assertThat(body).contains("get_finding");
        assertThat(body).contains("propose_fix");
        assertThat(body).contains("mark_triaged");
        assertThat(body).contains("finish_triage");
        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("heuristic-fallback");
    }

    @Test
    void cleanQueueScenarioFinishesWithoutFix() throws Exception {
        String token = registerAndGetToken("findings-clean@example.com");

        MvcResult result = mockMvc.perform(post("/api/findings-agent/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"clean_queue","dryRun":true}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("list_findings");
        assertThat(body).contains("finish_triage");
        assertThat(body).contains("\"success\":true");
        assertThat(body).doesNotContain("\"toolName\":\"propose_fix\"");
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
