package com.erplancers.erp.common.web;

import com.erplancers.erp.common.service.DynamicCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app")
public class CrudController {
    private final CrudRegistry registry;
    private final DynamicCrudService service;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("resources", registry.all().values());
        return "crud/index";
    }

    @GetMapping("/{slug}")
    public String list(@PathVariable String slug,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       Model model) {
        Page<Map<String, Object>> result = service.list(slug, page, size);
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("page", result);
        model.addAttribute("columns", new ArrayList<>(result.isEmpty() ? service.metadata(slug).keySet() : result.getContent().get(0).keySet()));
        return "crud/list";
    }

    @GetMapping("/{slug}/{id}")
    public String detail(@PathVariable String slug, @PathVariable Long id, Model model) {
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("item", service.get(slug, id));
        model.addAttribute("fields", service.metadata(slug));
        return "crud/detail";
    }

    @GetMapping("/{slug}/new")
    public String createForm(@PathVariable String slug, Model model) {
        model.addAttribute("resource", registry.get(slug));
        model.addAttribute("id", null);
        model.addAttribute("fields", service.metadata(slug));
        model.addAttribute("item", Map.of());
        return "crud/form";
    }

    @PostMapping("/{slug}/save")
    public String save(@PathVariable String slug,
                       @RequestParam(required = false) Long id,
                       @RequestParam Map<String, String> values,
                       RedirectAttributes redirectAttributes) {
        values.remove("id");
        var saved = service.save(slug, id, values);
        redirectAttributes.addFlashAttribute("message", "Saved " + registry.get(slug).entityType().getSimpleName() + " #" + saved.getId());
        return "redirect:/app/" + slug;
    }

    @PostMapping("/{slug}/{id}/delete")
    public String delete(@PathVariable String slug, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.delete(slug, id);
        redirectAttributes.addFlashAttribute("message", "Deleted #" + id);
        return "redirect:/app/" + slug;
    }
}
