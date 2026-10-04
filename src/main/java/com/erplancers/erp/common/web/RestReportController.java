package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.EntityReportService;
import com.erplancers.erp.common.reporting.ModuleReportingService;
import com.erplancers.erp.common.security.ErpAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST facade for ERP reporting services.
 *
 * Reports inherit the same module/resource authorization model as CRUD.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class RestReportController {

    private final EntityReportService entityReports;
    private final ModuleReportingService moduleReports;
    private final CrudRegistry registry;
    private final ErpAuthorizationService authorization;

    @GetMapping("/resources")
    public Collection<Map<String, Object>> resources(Authentication authentication) {
        return registry.all().values().stream()
                .filter(d -> authorization.canRead(authentication, d.slug()))
                .map(d -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("module", d.module());
                    result.put("slug", d.slug());
                    result.put("name", d.entityType().getSimpleName());
                    result.put("path", "/api/reports/resources/" + d.slug());
                    return result;
                })
                .toList();
    }

    @GetMapping("/resources/{slug}")
    public EntityReportService.EntityReport resource(
            Authentication authentication,
            @PathVariable String slug) {
        requireRead(authentication, slug);
        return entityReports.report(slug);
    }

    @GetMapping("/modules")
    public Map<String, ModuleReportingService.ModuleReport> modules(Authentication authentication) {
        return moduleReports.all().entrySet().stream()
                .filter(entry -> authorization.canReadModule(authentication, entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @GetMapping("/modules/{module}")
    public ModuleReportingService.ModuleReport module(
            Authentication authentication,
            @PathVariable String module) {
        if (!authorization.canReadModule(authentication, module)) {
            throw new AccessDeniedException("Read access denied for ERP module: " + module);
        }
        return moduleReports.module(module);
    }

    private void requireRead(Authentication authentication, String slug) {
        if (!authorization.canRead(authentication, slug)) {
            throw new AccessDeniedException("Read access denied for ERP resource: " + slug);
        }
    }
}
