package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.ModuleReportingService;
import com.erplancers.erp.common.service.DynamicCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app")
public class ModuleDashboardController {
    private final CrudRegistry registry;
    private final DynamicCrudService crudService;
    private final ModuleReportingService reportingService;

    @GetMapping
    public String index(Model model) {
        Map<String, ModuleReportingService.ModuleReport> reports = reportingService.all();
        model.addAttribute("reports", reports);
        model.addAttribute("resources", registry.all().values().stream()
                .sorted(Comparator.comparing(CrudRegistry.CrudDescriptor::module).thenComparing(d -> d.entityType().getSimpleName()))
                .toList());
        model.addAttribute("totalRecords", reports.values().stream().mapToLong(ModuleReportingService.ModuleReport::totalRecords).sum());
        return "dashboard/index";
    }

    @GetMapping("/module/{module}")
    public String module(@PathVariable String module, Model model) {
        model.addAttribute("report", reportingService.module(module));
        model.addAttribute("resources", registry.all().values().stream()
                .filter(d -> d.module().equals(module))
                .sorted(Comparator.comparing(d -> d.entityType().getSimpleName()))
                .toList());
        return "dashboard/module";
    }

    @GetMapping("/module/{module}/reports")
    public String reports(@PathVariable String module, Model model) {
        model.addAttribute("report", reportingService.module(module));
        return "dashboard/reports";
    }
}