@param java.util.Collection<com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor> resources

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>ERP</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body>
<main class="container py-4">
    <h1 class="mb-4">ERP modules</h1>
    <div class="row g-3">
        @for(var resource : resources)
            <div class="col-md-4">
                <a class="card h-100 text-decoration-none" href="/app/@resource.slug()">
                    <div class="card-body">
                        <h2 class="h5">@resource.entityType().getSimpleName()</h2>
                        <div class="text-muted">@resource.slug()</div>
                    </div>
                </a>
            </div>
        @endfor
    </div>
</main>
</body>
</html>
