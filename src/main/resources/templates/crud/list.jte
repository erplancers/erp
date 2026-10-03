@param com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor resource
@param org.springframework.data.domain.Page<java.util.Map<java.lang.String, java.lang.Object>> page
@param java.util.List<java.lang.String> columns

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@resource.entityType().getSimpleName()</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body>
<main class="container-fluid py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <a href="/app" class="text-decoration-none">ERP</a>
            <h1 class="h3 mb-0">@resource.entityType().getSimpleName()</h1>
        </div>
        <a class="btn btn-primary" href="/app/@resource.slug()/new">New</a>
    </div>
    @if(page.empty)
        <div class="alert alert-secondary">No records.</div>
    @else
        <div class="table-responsive">
            <table class="table table-striped table-hover align-middle">
                <thead><tr>
                    @for(var column : columns)<th>@column</th>@endfor
                    <th></th>
                </tr></thead>
                <tbody>
                @for(var row : page.content)
                    <tr>
                    @for(var column : columns)
                        <td>@String.valueOf(row.get(column))</td>
                    @endfor
                        <td class="text-end">
                            <a class="btn btn-sm btn-outline-primary" href="/app/@resource.slug()/@row.get("id")">Open</a>
                            <form class="d-inline" method="post" action="/app/@resource.slug()/@row.get("id")/delete">
                                <button class="btn btn-sm btn-outline-danger" type="submit">Delete</button>
                            </form>
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        </div>
    @endif
</main>
</body>
</html>
