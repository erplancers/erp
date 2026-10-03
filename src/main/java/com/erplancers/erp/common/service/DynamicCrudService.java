package com.erplancers.erp.common.service;

import com.erplancers.erp.common.domain.EntityBase;
import com.erplancers.erp.common.web.CrudRegistry;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Introspector;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DynamicCrudService {
    private final CrudRegistry registry;
    private final DefaultConversionService conversionService = new DefaultConversionService();

    public DynamicCrudService(CrudRegistry registry) {
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(String slug, int page, int size) {
        CrudRegistry.CrudDescriptor descriptor = registry.get(slug);
        return descriptor.repository().findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(this::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String slug, Long id) {
        EntityBase entity = (EntityBase) registry.get(slug).repository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
        return row(entity);
    }

    @Transactional
    public EntityBase save(String slug, Long id, Map<String, String> values) {
        CrudRegistry.CrudDescriptor descriptor = registry.get(slug);
        EntityBase entity;
        if (id == null) {
            try {
                entity = (EntityBase) descriptor.entityType().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Entity needs a no-args constructor: " + descriptor.entityType().getName(), e);
            }
        } else {
            entity = (EntityBase) descriptor.repository().findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + id));
        }

        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        values.forEach((name, value) -> {
            if (!wrapper.isWritableProperty(name) || value == null || value.isBlank()) return;
            Class<?> propertyType = wrapper.getPropertyType(name);
            if (propertyType == null || !isSimple(propertyType)) return;
            Object converted = conversionService.convert(value, propertyType);
            if (converted != null) wrapper.setPropertyValue(name, converted);
        });
        return (EntityBase) descriptor.repository().save(entity);
    }

    @Transactional
    public void delete(String slug, Long id) {
        registry.get(slug).repository().deleteById(id);
    }

    public Map<String, String> metadata(String slug) {
        CrudRegistry.CrudDescriptor descriptor = registry.get(slug);
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            for (var property : Introspector.getBeanInfo(descriptor.entityType()).getPropertyDescriptors()) {
                if ("class".equals(property.getName()) || property.getReadMethod() == null || property.getWriteMethod() == null) continue;
                Class<?> type = property.getPropertyType();
                if (type != null && isSimple(type)) fields.put(property.getName(), type.getSimpleName());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot inspect " + descriptor.entityType().getName(), e);
        }
        return fields;
    }

    private Map<String, Object> row(Object entity) {
        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        Map<String, Object> row = new LinkedHashMap<>();
        for (var property : wrapper.getPropertyDescriptors()) {
            if ("class".equals(property.getName()) || property.getReadMethod() == null) continue;
            Class<?> type = property.getPropertyType();
            if (type != null && isSimple(type)) {
                try { row.put(property.getName(), wrapper.getPropertyValue(property.getName())); }
                catch (RuntimeException ignored) { }
            }
        }
        return row;
    }

    private boolean isSimple(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type || type.isEnum() || UUID.class == type || Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type);
    }
}
