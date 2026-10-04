@param com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor resource
@param java.util.Map<java.lang.String, java.lang.String> fields
@param java.util.List<java.util.Map<java.lang.String, java.lang.String>> references
@param java.util.List<java.util.Map<java.lang.String, java.lang.String>> collections
@param java.util.Map<java.lang.String, java.util.List<java.util.Map<java.lang.String, java.lang.Object>>> options
@param java.util.Map<java.lang.String, java.lang.Object> values
@param java.util.Map<java.lang.String, java.lang.Object> selected
@param java.util.List<java.util.Map<java.lang.String, java.lang.Object>> nestedCollections
@param java.util.Map<java.lang.String, java.util.List<java.util.Map<java.lang.String, java.lang.Object>>> nestedOptions
@param java.lang.Long id

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@resource.entityType().getSimpleName() - @if(id == null)New@else Edit@endif</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body class="bg-body-tertiary">
<nav class="navbar bg-white border-bottom">
    <div class="container-fluid px-4">
        <a class="navbar-brand fw-semibold" href="/app">ERP Control Center</a>
        <a class="btn btn-outline-secondary btn-sm" href="/app/@resource.slug()">Back to list</a>
    </div>
</nav>

<main class="container-fluid px-4 py-4">
    <div class="mb-4">
        <div class="text-uppercase text-muted small">@resource.module()</div>
        <h1 class="h2 mb-1">@resource.entityType().getSimpleName()</h1>
        <p class="text-muted mb-0">@if(id == null)Create a new aggregate.@elseUpdate the aggregate and its nested records.@endif</p>
    </div>

    <form method="post" action="/app/@resource.slug()/save">
        @if(id != null)<input type="hidden" name="id" value="@id">@endif

        <div class="row g-4">
            <div class="col-12 col-xl-8">
                <section class="card border-0 shadow-sm mb-4">
                    <div class="card-header bg-white py-3">
                        <h2 class="h5 mb-0">Core fields</h2>
                    </div>
                    <div class="card-body">
                        <div class="row g-3">
                            @for(var entry : fields.entrySet())
                                @if(!entry.getKey().equals("id"))
                                    <div class="col-md-6">
                                        <label class="form-label fw-medium" for="@entry.getKey()">@entry.getKey()</label>
                                        <input class="form-control" id="@entry.getKey()" name="@entry.getKey()"
                                               type="@if(entry.getValue().equals("Boolean"))checkbox@else text@endif"
                                               value="@String.valueOf(values.getOrDefault(entry.getKey(), ""))">
                                        <div class="form-text">@entry.getValue()</div>
                                    </div>
                                @endif
                            @endfor
                        </div>
                    </div>
                </section>

                @if(!references.isEmpty())
                    <section class="card border-0 shadow-sm mb-4">
                        <div class="card-header bg-white py-3">
                            <h2 class="h5 mb-0">Related records</h2>
                        </div>
                        <div class="card-body">
                            <div class="row g-3">
                                @for(var ref : references)
                                    <div class="col-md-6">
                                        <label class="form-label fw-medium" for="ref-@ref.get("name")">@ref.get("name")</label>
                                        <select class="form-select" id="ref-@ref.get("name")" name="ref.@ref.get("name")">
                                            <option value="">Choose @ref.get("type")...</option>
                                            @for(var option : options.getOrDefault(ref.get("name"), java.util.List.of()))
                                                <option value="@option.get("id")"
                                                    @if(String.valueOf(selected.get(ref.get("name"))).equals(String.valueOf(option.get("id"))))selected@endif>
                                                    @option.get("label")
                                                </option>
                                            @endfor
                                        </select>
                                    </div>
                                @endfor
                            </div>
                        </div>
                    </section>
                @endif

                @if(!nestedCollections.isEmpty())
                    @for(var collection : nestedCollections)
                        <section class="card border-0 shadow-sm mb-4 nested-collection"
                                 data-collection="@collection.get("name")">
                            <div class="card-header bg-white py-3 d-flex justify-content-between align-items-center">
                                <div>
                                    <h2 class="h5 mb-0">@collection.get("name")</h2>
                                    <div class="text-muted small">Inline @collection.get("type") records</div>
                                </div>
                                <button class="btn btn-outline-primary btn-sm add-nested-row" type="button"
                                        data-collection="@collection.get("name")">Add @collection.get("type")</button>
                            </div>
                            <div class="card-body nested-rows">
                                @for(var row : (java.util.List<java.util.Map<java.lang.String, java.lang.Object>>) collection.get("rows"))
                                    <div class="nested-row border rounded p-3 mb-3">
                                        <input type="hidden" class="nested-id"
                                               name="nested.@collection.get("name").@row.get("id")._id"
                                               value="@row.get("id")">
                                        <input type="hidden" class="nested-delete"
                                               name="nested.@collection.get("name").@row.get("id")._delete" value="false">
                                        <div class="row g-3">
                                            @for(var entry : ((java.util.Map<java.lang.String, java.lang.String>) collection.get("fields")).entrySet())
                                                @if(!entry.getKey().equals("id"))
                                                    <div class="col-md-6">
                                                        <label class="form-label small fw-medium">@entry.getKey()</label>
                                                        <input class="form-control"
                                                               name="nested.@collection.get("name").@row.get("id").@entry.getKey()"
                                                               value="@String.valueOf(((java.util.Map) row.get("values")).getOrDefault(entry.getKey(), ""))">
                                                    </div>
                                                @endif
                                            @endfor
                                            @for(var ref : (java.util.List<java.util.Map<java.lang.String, java.lang.String>>) collection.get("references"))
                                                <div class="col-md-6">
                                                    <label class="form-label small fw-medium">@ref.get("name")</label>
                                                    <select class="form-select"
                                                            name="nested.@collection.get("name").@row.get("id").ref.@ref.get("name")">
                                                        <option value="">Choose @ref.get("type")...</option>
                                                        @for(var option : nestedOptions.getOrDefault(collection.get("name") + "." + ref.get("name"), java.util.List.of()))
                                                            <option value="@option.get("id")"
                                                                @if(String.valueOf(((java.util.Map) row.get("selected")).get(ref.get("name"))).equals(String.valueOf(option.get("id"))))selected@endif>
                                                                @option.get("label")
                                                            </option>
                                                        @endfor
                                                    </select>
                                                </div>
                                            @endfor
                                        </div>
                                        <div class="d-flex justify-content-end mt-3">
                                            <button class="btn btn-outline-danger btn-sm remove-nested-row" type="button">Remove</button>
                                        </div>
                                    </div>
                                @endfor
                            </div>
                        </section>
                    @endfor
                @endif

                @if(!collections.isEmpty())
                    <section class="card border-0 shadow-sm mb-4">
                        <div class="card-header bg-white py-3">
                            <h2 class="h5 mb-0">Related collections</h2>
                        </div>
                        <div class="card-body">
                            <div class="row g-3">
                                @for(var collection : collections)
                                    <div class="col-12">
                                        <label class="form-label fw-medium" for="collection-@collection.get("name")">@collection.get("name")</label>
                                        <select class="form-select" multiple size="6"
                                                id="collection-@collection.get("name")"
                                                name="collection.@collection.get("name")">
                                            @for(var option : options.getOrDefault(collection.get("name"), java.util.List.of()))
                                                <option value="@option.get("id")"
                                                    @if(selected.get(collection.get("name")) instanceof java.util.Collection && ((java.util.Collection) selected.get(collection.get("name"))).contains(option.get("id")))selected@endif>
                                                    @option.get("label")
                                                </option>
                                            @endfor
                                        </select>
                                        <div class="form-text">Select existing related @collection.get("type") records.</div>
                                    </div>
                                @endfor
                            </div>
                        </div>
                    </section>
                @endif
            </div>

            <aside class="col-12 col-xl-4">
                <div class="card border-0 shadow-sm sticky-xl-top" style="top:1rem">
                    <div class="card-body">
                        <h2 class="h5">Save aggregate</h2>
                        <p class="text-muted small">Child rows are created, updated, or removed together with the aggregate.</p>
                        <button class="btn btn-primary w-100" type="submit">Save @resource.entityType().getSimpleName()</button>
                        <a class="btn btn-link w-100 mt-2" href="/app/@resource.slug()">Cancel</a>
                    </div>
                </div>
            </aside>
        </div>
    </form>
</main>

<script>
document.querySelectorAll('.add-nested-row').forEach(function (button) {
    button.addEventListener('click', function () {
        const collection = button.closest('.nested-collection');
        const rows = collection.querySelector('.nested-rows');
        const source = rows.querySelector('.nested-row');
        if (!source) return;
        const clone = source.cloneNode(true);
        const index = 'new-' + Date.now();
        clone.querySelectorAll('input, select').forEach(function (field) {
            field.name = field.name.replace(/\\.\\d+\\./, '.' + index + '.');
            if (field.classList.contains('nested-id')) field.value = '';
            if (field.classList.contains('nested-delete')) field.value = 'false';
            if (field.tagName === 'INPUT' && !field.classList.contains('nested-id') && !field.classList.contains('nested-delete')) field.value = '';
            if (field.tagName === 'SELECT') field.selectedIndex = 0;
        });
        clone.querySelector('.nested-delete').value = 'false';
        rows.appendChild(clone);
    });
});

document.addEventListener('click', function (event) {
    const button = event.target.closest('.remove-nested-row');
    if (!button) return;
    const row = button.closest('.nested-row');
    const id = row.querySelector('.nested-id').value;
    if (id) {
        row.querySelector('.nested-delete').value = 'true';
        row.style.display = 'none';
    } else {
        row.remove();
    }
});
</script>
</body>
</html>
