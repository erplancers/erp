package com.erplancers.erp.common.web;

import com.erplancers.erp.common.security.domain.ErpPolicy;
import com.erplancers.erp.common.security.domain.ErpUser;
import com.erplancers.erp.common.security.repository.ErpAccessRuleRepository;
import com.erplancers.erp.common.security.repository.ErpUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ErpUserRepository users;
    @Autowired ErpAccessRuleRepository policies;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        policies.deleteAll();
        users.deleteAll();
        users.save(user("admin", "admin-pass", "ADMIN", "ADMIN"));
        users.save(user("accounting", "accounting-pass", "USER", "ACCOUNTING"));
        users.save(user("readonly", "readonly-pass", "USER", "REPORTS"));
        allowRole("ACCOUNTING", "accounting", "*", "READ");
        allowRole("ACCOUNTING", "accounting", "invoice", "WRITE");
        allowRole("REPORTS", "*", "*", "READ");
        denyUser("readonly", "finance", "invoice", "READ");
    }

    @Test void unauthenticatedApiIsRejected() throws Exception {
        mvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
    }

    @Test void swaggerIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test void adminCanSeeResources() throws Exception {
        mvc.perform(get("/api/resources").with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test void accountingUserGetsOnlyAuthorizedResources() throws Exception {
        mvc.perform(get("/api/resources").with(httpBasic("accounting", "accounting-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].module", everyItem(is("accounting"))));
    }

    @Test void invalidPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/resources").with(httpBasic("accounting", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test void accountingCanReadAuthorizedResource() throws Exception {
        mvc.perform(get("/api/resources/account").with(httpBasic("accounting", "accounting-pass")))
                .andExpect(status().isOk());
    }

    @Test void accountingCannotReadUnprivilegedResource() throws Exception {
        mvc.perform(get("/api/resources/customer").with(httpBasic("accounting", "accounting-pass")))
                .andExpect(status().isForbidden());
    }

    @Test void userSpecificDenyOverridesRoleAllow() throws Exception {
        mvc.perform(get("/api/resources/invoice").with(httpBasic("readonly", "readonly-pass")))
                .andExpect(status().isForbidden());
    }

    @Test void reportsFollowPolicyAuthorization() throws Exception {
        mvc.perform(get("/api/reports/resources/invoice").with(httpBasic("readonly", "readonly-pass")))
                .andExpect(status().isForbidden());
    }

    private ErpUser user(String username, String password, String level, String role) {
        ErpUser u = new ErpUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setLevel(level);
        u.getRoles().add(role);
        return u;
    }

    private void allowRole(String role, String module, String resource, String action) {
        policy(null, role, module, resource, action, ErpPolicy.PolicyEffect.ALLOW);
    }

    private void denyUser(String username, String module, String resource, String action) {
        policy(username, null, module, resource, action, ErpPolicy.PolicyEffect.DENY);
    }

    private void policy(String username, String role, String module, String resource, String action,
                        ErpPolicy.PolicyEffect effect) {
        ErpPolicy p = new ErpPolicy();
        p.setUsername(username);
        p.setRole(role);
        p.setModule(module);
        p.setResource(resource);
        p.setAction(action);
        p.setEffect(effect);
        policies.save(p);
    }
}
