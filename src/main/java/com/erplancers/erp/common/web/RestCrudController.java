package com.erplancers.erp.common.web;

import com.erplancers.erp.common.domain.EntityBase;
import com.erplancers.erp.common.service.DynamicCrudService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic REST API for every repository registered by CrudRegistry.
 *
 * Every ErpRepository is discovered automatically, so adding a new repository
 * automatically adds its REST resource without another controller class.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources")
public class RestCrudController {

    private final CrudRegistry registry;
    private final DynamicCrudService service;

    @GetMapping
    public Collection<Map<String, Object>> resources() {
        return registry.all().values().stream()
                .map(d -> {
                    Map<String, Object> resource = new LinkedHashMap<>();
                    resource.put("slug", d.slug());
                    resource.put("module", d.module());
                    resource.put("entity", d.entityType().getSimpleName());
                    resource.put("path", "/api/resources/" + d.slug());
                    return resource;
                })
                .toList();
    }

    @GetMapping("/{slug}")
    public Page<Map<String, Object>> list(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(slug, page, size);
    }

    @GetMapping("/{slug}/metadata")
    public Map<String, Object> metadata(@PathVariable String slug) {
        return service.formMetadata(slug, null);
    }

    @GetMapping("/{slug}/{id}")
    public Map<String, Object> get(
            @PathVariable String slug,
            @PathVariable Long id) {
        return service.get(slug, id);
    }

    @GetMapping("/{slug}/{id}/metadata")
    public Map<String, Object> metadata(
            @PathVariable String slug,
            @PathVariable Long id) {
        return service.formMetadata(slug, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityBase create(
            @RequestParam String resource,
            @Valid @RequestBody Map<String, String> values) {
        return service.save(resource, null, values);
    }

    @PostMapping("/{slug}")
    @ResponseStatus(HttpStatus.CREATED)
    public EntityBase create(
            @PathVariable String slug,
            @Valid @RequestBody Map<String, String> values) {
        return service.save(slug, null, values);
    }

    @PutMapping("/{slug}/{id}")
    public EntityBase update(
            @PathVariable String slug,
            @PathVariable Long id,
            @Valid @RequestBody Map<String, String> values) {
        return service.save(slug, id, values);
    }

    @PatchMapping("/{slug}/{id}")
    public EntityBase patch(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestBody Map<String, String> values) {
        return service.save(slug, id, values);
    }

    @DeleteMapping("/{slug}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String slug,
            @PathVariable Long id) {
        service.delete(slug, id);
    }
}
