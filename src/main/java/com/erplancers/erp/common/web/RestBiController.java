package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.AdvancedAnalyticsEngine;
import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.erplancers.erp.common.reporting.OlapAnalysisEngine;
import com.erplancers.erp.common.security.PolicyAuthorizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bi")
@SecurityRequirement(name = "basicAuth")
public class RestBiController {

    private final ErpBiEngine bi;
    private final OlapAnalysisEngine olap;
    private final PolicyAuthorizationService authorization;

    @PostMapping("/resources/{slug}/query")
    public ErpBiEngine.BiReport query(
            Authentication authentication,
            @PathVariable String slug,
            @RequestBody ErpBiEngine.BiQuery request) {
        requireRead(authentication, slug);
        return bi.query(slug, request);
    }

    @PostMapping("/resources/{slug}/analyze")
    public AdvancedAnalyticsEngine.AdvancedAnalysis analyze(
            Authentication authentication,
            @PathVariable String slug,
            @RequestBody ErpBiEngine.BiAnalysisRequest request) {
        requireRead(authentication, slug);
        return bi.analyze(slug, request);
    }

    @GetMapping("/olap/catalog")
    public OlapAnalysisEngine.OlapCatalog catalog(Authentication authentication) {
        requireAuthenticated(authentication);
        return olap.catalog();
    }

    @GetMapping("/olap/profiles")
    public Object profiles(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int topValues) {
        requireAuthenticated(authentication);
        return olap.profileAll(topValues);
    }

    @PostMapping("/olap/resources/{slug}/cube")
    public OlapAnalysisEngine.CubeResult cube(
            Authentication authentication,
            @PathVariable String slug,
            @RequestBody OlapAnalysisEngine.CubeRequest request) {
        requireRead(authentication, slug);
        return olap.cube(slug, request);
    }

    @PostMapping("/olap/resources/{slug}/drilldown")
    public OlapAnalysisEngine.CubeResult drillDown(
            Authentication authentication,
            @PathVariable String slug,
            @RequestBody OlapAnalysisEngine.DrillDownRequest request) {
        requireRead(authentication, slug);
        return olap.drillDown(slug, request);
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private void requireRead(Authentication authentication, String slug) {
        if (!authorization.isAllowed(authentication, slug, "READ")) {
            throw new AccessDeniedException(
                    "BI READ access denied for resource: " + slug);
        }
    }
}
