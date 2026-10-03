@param com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor resource
@param java.util.Map<java.lang.String, java.lang.String> fields
@param java.util.Map<java.lang.String, java.lang.Object> item
@param java.lang.Long id

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@resource.entityType().getSimpleName() - Edit</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body>
<main class="container py-4">
    <a href="/app/@resource.slug()">← Back</a>
    <h1 class="h3 mt-3">@resource.entityType().getSimpleName()</h1>
    <form class="row g-3 mt-2" method="post" action="/app/@resource.slug()/save">
        @if(id != null)<input type="hidden" name="id" value="@id">@endif
        @for(var entry : fields.entrySet())
            @if(!entry.getKey().equals("id"))
                <div class="col-md-6">
                    <label class="form-label" for="@entry.getKey()">@entry.getKey()</label>
                    <input class="form-control" id="@entry.getKey()" name="@entry.getKey()" type="text"
                           value="@String.valueOf(item.getOrDefault(entry.getKey(), ""))">
                </div>
            @endif
        @endfor
        <div class="col-12">
            <button class="btn btn-primary" type="submit">Save</button>
        </div>
    </form>
</main>
</body>
</html>
