@param com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor resource
@param java.util.Map<java.lang.String, java.lang.Object> item
@param java.util.Map<java.lang.String, java.lang.String> fields

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@resource.entityType().getSimpleName()</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body>
<main class="container py-4">
    <a href="/app/@resource.slug()">← Back</a>
    <h1 class="h3 mt-3">@resource.entityType().getSimpleName()</h1>
    <dl class="row mt-4">
    @for(var entry : item.entrySet())
        <dt class="col-sm-3">@entry.getKey()</dt>
        <dd class="col-sm-9">@String.valueOf(entry.getValue())</dd>
    @endfor
    </dl>
    <a class="btn btn-primary" href="/app/@resource.slug()/new">New</a>
</main>
</body>
</html>
