package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.EntityReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app/reports")
public class ErpReportController {
    private final CrudRegistry registry;
    private final EntityReportService entityReports;

    @GetMapping
    public String index(Model model) {
        Map<String, Long> modules = registry.all().values().stream()
                .collect(Collectors.groupingBy(CrudRegistry.CrudDescriptor::module, LinkedHashMap::new, Collectors.counting()));
        model.addAttribute("modules", modules);
        return "reports/index";
    }

    @GetMapping("/{module}")
    public String module(@PathVariable String module, Model model) {
        var resources = registry.all().values().stream()
                .filter(d -> module.equals(d.module()))
                .sorted(Comparator.comparing(d -> d.entityType().getSimpleName()))
                .map(d -> entityReports.report(d.slug()))
                .toList();
        if (resources.isEmpty()) throw new IllegalArgumentException("Unknown ERP module: " + module);
        model.addAttribute("module", module);
        model.addAttribute("resources", resources);
        model.addAttribute("totalRecords", resources.stream().mapToLong(EntityReportService.EntityReport::count).sum());
        return "reports/module";
    }

    @GetMapping("/{module}/{slug}")
    public String entity(@PathVariable String module, @PathVariable String slug, Model model) {
        var report = entityReports.report(slug);
        if (!module.equals(report.module())) throw new IllegalArgumentException("Resource does not belong to module: " + slug);
        model.addAttribute("report", report);
        return "reports/entity";
    }
}