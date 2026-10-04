package com.erplancers.erp.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {"gg.jte.usePrecompiledTemplates=false", "gg.jte.developmentMode=true"})
@AutoConfigureMockMvc
class MvcRoutingTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRouteResolvesJteView() throws Exception {
        mockMvc.perform(get("/app"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ERP Operations")));
    }

    @Test
    void moduleDashboardRouteResolvesJteView() throws Exception {
        mockMvc.perform(get("/app/module/accounting"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/module"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("accounting")));
    }

    @Test
    void moduleReportsRouteResolvesJteView() throws Exception {
        mockMvc.perform(get("/app/module/accounting/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/reports"));
    }

    @Test
    void reportsIndexRouteResolvesJteView() throws Exception {
        mockMvc.perform(get("/app/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ERP Reports")));
    }
}
