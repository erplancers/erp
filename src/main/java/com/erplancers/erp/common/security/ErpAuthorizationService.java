package com.erplancers.erp.common.security;

import com.erplancers.erp.common.web.CrudRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service("erpAuthorizationService")
public class ErpAuthorizationService {

    private static final String ADMIN = "ERP_ADMIN";

    private final CrudRegistry registry;

    public ErpAuthorizationService(CrudRegistry registry) {
        this.registry = registry;
    }

    public boolean canRead(Authentication authentication, String slug) {
        return has(authentication, slug, "READ");
    }

    public boolean canWrite(Authentication authentication, String slug) {
        return has(authentication, slug, "WRITE");
    }

    public boolean canDelete(Authentication authentication, String slug) {
        return has(authentication, slug, "DELETE") || has(authentication, slug, "WRITE");
    }

    public boolean canReadModule(Authentication authentication, String module) {
        return hasModule(authentication, module, "READ");
    }

    public boolean canWriteModule(Authentication authentication, String module) {
        return hasModule(authentication, module, "WRITE");
    }

    private boolean has(Authentication authentication, String slug, String operation) {
        if (authentication == null || !authentication.isAuthenticated()) return false;

        CrudRegistry.CrudDescriptor descriptor;
        try {
            descriptor = registry.get(slug);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        if (authentication.getAuthorities().stream().anyMatch(a -> ADMIN.equals(a.getAuthority()))) {
            return true;
        }

        String normalizedSlug = normalize(slug);
        String normalizedModule = normalize(descriptor.module());

        if (hasAuthority(authentication, "ERP_RESOURCE_" + normalizedSlug + "_" + operation)) {
            return true;
        }

        if (hasAuthority(authentication, "ERP_MODULE_" + normalizedModule + "_" + operation)) {
            return true;
        }

        return "READ".equals(operation) &&
                hasAuthority(authentication, "ERP_RESOURCE_" + normalizedSlug + "_WRITE");
    }

    private boolean hasModule(Authentication authentication, String module, String operation) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (authentication.getAuthorities().stream().anyMatch(a -> ADMIN.equals(a.getAuthority()))) return true;

        String normalizedModule = normalize(module);
        if (hasAuthority(authentication, "ERP_MODULE_" + normalizedModule + "_" + operation)) return true;

        return "READ".equals(operation) &&
                hasAuthority(authentication, "ERP_MODULE_" + normalizedModule + "_WRITE");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equalsIgnoreCase(a.getAuthority()));
    }

    private String normalize(String value) {
        return value == null ? "" : value
                .replace('-', '_')
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
    }
}
