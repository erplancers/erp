@param com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor resource
@param java.util.Map<java.lang.String, java.lang.String> fields
@param java.util.List<java.util.Map<java.lang.String, java.lang.String>> references
@param java.util.List<java.util.Map<java.lang.String, java.lang.String>> collections
@param java.util.Map<java.lang.String, java.util.List<java.util.Map<java.lang.String, java.lang.Object>>> options
@param java.util.Map<java.lang.String, java.lang.Object> values
@param java.util.Map<java.lang.String, java.lang.Object> selected
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
        <p class="text-muted mb-0">@if(id == null)Create a new aggregate.@elseUpdate the aggregate and its relationships.@endif</p>
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
                                        <div class="form-text">Reference to @ref.get("type").</div>
                                    </div>
                                @endfor
                            </div>
                        </div>
                    </section>
                @endif

                @if(!collections.isEmpty())
                    <section class="card border-0 shadow-sm mb-4">
                        <div class="card-header bg-white py-3">
                            <h2 class="h5 mb-0">Aggregate collections</h2>
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
                                        <div class="form-text">Select related @collection.get("type") records. Use Ctrl/Cmd for multiple selections.</div>
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
                        <p class="text-muted small">Relationships are loaded from the corresponding ERP repositories and persisted with the aggregate.</p>
                        <button class="btn btn-primary w-100" type="submit">Save @resource.entityType().getSimpleName()</button>
                        <a class="btn btn-link w-100 mt-2" href="/app/@resource.slug()">Cancel</a>
                    </div>
                </div>
            </aside>
        </div>
    </form>
</main>
</body>
</html>
