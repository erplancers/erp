@param java.util.Collection<com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor> resources
@param java.util.Map<java.lang.String, java.lang.Long> counts

<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>ERP Control Center</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        body { background: #f6f8fb; }
        .hero { background: #172033; color: white; border-radius: 1.25rem; }
        .module-card { transition: transform .15s ease, box-shadow .15s ease; }
        .module-card:hover { transform: translateY(-2px); box-shadow: 0 .75rem 1.5rem rgba(0,0,0,.08); }
        .module-badge { text-transform: capitalize; }
    </style>
</head>
<body>
<nav class="navbar bg-white border-bottom">
    <div class="container-fluid px-4">
        <span class="navbar-brand fw-semibold">ERP Control Center</span>
        <span class="text-muted small">@resources.size() modules</span>
    </div>
</nav>
<main class="container-fluid px-4 py-4">
    <section class="hero p-4 p-lg-5 mb-4">
        <div class="row align-items-center g-4">
            <div class="col-lg-8">
                <div class="text-uppercase small opacity-75 mb-2">Operations</div>
                <h1 class="display-6 fw-semibold mb-2">Everything in one workspace.</h1>
                <p class="lead opacity-75 mb-0">Browse, create and manage ERP aggregates with relationship-aware forms.</p>
            </div>
            <div class="col-lg-4 text-lg-end">
                <a class="btn btn-light btn-lg" href="#modules">Browse modules</a>
            </div>
        </div>
    </section>

    <section id="modules">
        <div class="d-flex justify-content-between align-items-end mb-3">
            <div>
                <h2 class="h4 mb-1">Modules</h2>
                <p class="text-muted mb-0">Domain resources discovered from your repositories.</p>
            </div>
        </div>
        <div class="row g-3">
            @for(var resource : resources)
                <div class="col-12 col-md-6 col-xl-4 col-xxl-3">
                    <a class="card module-card h-100 border-0 shadow-sm text-decoration-none text-dark"
                       href="/app/@resource.slug()">
                        <div class="card-body">
                            <div class="d-flex justify-content-between align-items-start mb-3">
                                <span class="badge text-bg-light module-badge">@resource.module()</span>
                                <span class="badge rounded-pill text-bg-primary">@counts.get(resource.slug())</span>
                            </div>
                            <h3 class="h5 mb-1">@resource.entityType().getSimpleName()</h3>
                            <p class="text-muted small mb-0">Manage records and related aggregates</p>
                        </div>
                    </a>
                </div>
            @endfor
        </div>
    </section>
</main>
</body>
</html>
