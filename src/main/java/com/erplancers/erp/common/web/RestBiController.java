package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.erplancers.erp.common.reporting.AdvancedAnalyticsEngine;
import com.erplancers.erp.common.reporting.AggregateReportingService;
import com.erplancers.erp.common.security.PolicyAuthorizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bi")
@SecurityRequirement(name = "basicAuth")
public class RestBiController {

    private final ErpBiEngine bi;
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

    private void requireRead(Authentication authentication, String slug) {
        if (!authorization.isAllowed(authentication, slug, "READ")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "BI READ access denied for resource: " + slug);
        }
    }
}
