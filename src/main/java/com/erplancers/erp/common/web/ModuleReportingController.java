package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.ModuleReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/modules")
public class ModuleReportingController {
    private final ModuleReportingService reportingService;

    @GetMapping
    public Map<String, ModuleReportingService.ModuleReport> all() {
        return reportingService.all();
    }

    @GetMapping("/{module}")
    public ModuleReportingService.ModuleReport module(@PathVariable String module) {
        return reportingService.module(module);
    }
}