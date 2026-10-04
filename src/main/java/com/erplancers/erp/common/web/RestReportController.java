package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.EntityReportService;
import com.erplancers.erp.common.reporting.ModuleReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST facade for the ERP reporting services.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class RestReportController {

    private final EntityReportService entityReports;
    private final ModuleReportingService moduleReports;

    @GetMapping("/resources")
    public Collection<Map<String, Object>> resources() {
        return registryResources();
    }

    private Collection<Map<String, Object>> registryResources() {
        return moduleReports.all().values().stream()
                .flatMap(report -> report.resources().stream())
                .map(resource -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("module", findModule(resource.slug()));
                    result.put("slug", resource.slug());
                    result.put("name", resource.name());
                    result.put("path", "/api/reports/resources/" + resource.slug());
                    return result;
                })
                .toList();
    }

    @GetMapping("/resources/{slug}")
    public EntityReportService.EntityReport resource(@PathVariable String slug) {
        return entityReports.report(slug);
    }

    @GetMapping("/modules")
    public Map<String, ModuleReportingService.ModuleReport> modules() {
        return moduleReports.all();
    }

    @GetMapping("/modules/{module}")
    public ModuleReportingService.ModuleReport module(@PathVariable String module) {
        return moduleReports.module(module);
    }

    private String findModule(String slug) {
        return moduleReports.all().entrySet().stream()
                .filter(e -> e.getValue().resources().stream()
                        .anyMatch(r -> r.slug().equals(slug)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
