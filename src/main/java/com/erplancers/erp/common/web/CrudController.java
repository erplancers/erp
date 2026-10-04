package com.erplancers.erp.common.web;

import com.erplancers.erp.common.service.DynamicCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app")
public class CrudController {
    private final CrudRegistry registry;
    private final DynamicCrudService service;

    @GetMapping("/{slug}")
    public String list(@PathVariable String slug,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       Model model) {
        Page<Map<String, Object>> result = service.list(slug, page, size);
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("page", result);
        model.addAttribute("columns", new ArrayList<>(result.isEmpty()
                ? service.formMetadata(slug, null).get("fields") instanceof Map<?, ?> fields
                    ? fields.keySet().stream().map(Object::toString).toList()
                    : java.util.List.of()
                : result.getContent().get(0).keySet()));
        return "crud/list";
    }

    @GetMapping("/{slug}/{id}")
    public String detail(@PathVariable String slug, @PathVariable Long id, Model model) {
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("item", service.get(slug, id));
        model.addAttribute("fields", service.formMetadata(slug, id).get("fields"));
        return "crud/detail";
    }

    @GetMapping("/{slug}/new")
    public String createForm(@PathVariable String slug, Model model) {
        populateForm(model, slug, null);
        return "crud/form";
    }

    @GetMapping("/{slug}/{id}/edit")
    public String editForm(@PathVariable String slug, @PathVariable Long id, Model model) {
        populateForm(model, slug, id);
        return "crud/form";
    }

    private void populateForm(Model model, String slug, Long id) {
        Map<String, Object> metadata = service.formMetadata(slug, id);
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("id", id);
        model.addAttribute("fields", metadata.get("fields"));
        model.addAttribute("references", metadata.get("references"));
        model.addAttribute("collections", metadata.get("collections"));
        model.addAttribute("options", metadata.get("options"));
        model.addAttribute("values", metadata.get("values"));
        model.addAttribute("selected", metadata.get("selected"));
        model.addAttribute("nestedCollections", metadata.get("nestedCollections"));
        model.addAttribute("nestedOptions", metadata.get("nestedOptions"));
    }

    @PostMapping("/{slug}/save")
    public String save(@PathVariable String slug,
                       @RequestParam(required = false) Long id,
                       @RequestParam MultiValueMap<String, String> rawValues,
                       RedirectAttributes redirectAttributes) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        rawValues.forEach((key, items) -> values.put(key, key.startsWith("collection.") ? String.join(",", items) : items.isEmpty() ? "" : items.get(items.size() - 1)));
        values.remove("id");
        var saved = service.save(slug, id, values);
        redirectAttributes.addFlashAttribute("message",
                "Saved " + registry.get(slug).entityType().getSimpleName() + " #" + saved.getId());
        return "redirect:/app/" + slug;
    }

    @PostMapping("/{slug}/{id}/delete")
    public String delete(@PathVariable String slug, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.delete(slug, id);
        redirectAttributes.addFlashAttribute("message", "Deleted #" + id);
        return "redirect:/app/" + slug;
    }
}
