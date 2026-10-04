@param java.util.Map<java.lang.String, com.erplancers.erp.common.reporting.ModuleReportingService.ModuleReport> reports
@param java.util.Collection<com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor> resources
@param long totalRecords

<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="ERP operations dashboard">
    <title>ERP | Operations</title>
    <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        :root { --erp-ink:#172033; --erp-muted:#667085; --erp-border:#e7eaf0; --erp-bg:#f5f7fa; }
        body { background:var(--erp-bg); color:var(--erp-ink); }
        .topbar { background:#fff; border-bottom:1px solid var(--erp-border); }
        .brand-mark { width:36px;height:36px;border-radius:10px;background:var(--erp-ink);color:#fff;display:grid;place-items:center;font-weight:700; }
        .hero { background:linear-gradient(135deg,#172033 0%,#273653 100%); color:#fff; border-radius:20px; }
        .metric { background:#fff;border:1px solid var(--erp-border);border-radius:16px; }
        .module-card { background:#fff;border:1px solid var(--erp-border);border-radius:16px;transition:.18s ease; }
        .module-card:hover { transform:translateY(-2px); box-shadow:0 12px 30px rgba(16,24,40,.08); border-color:#d9deea; }
        .module-icon { width:42px;height:42px;border-radius:12px;background:#eef2f7;display:grid;place-items:center;font-weight:700; }
        .muted { color:var(--erp-muted); }
        .section-label { letter-spacing:.08em;text-transform:uppercase;font-size:.72rem;font-weight:700;color:var(--erp-muted); }
    </style>
</head>
<body>
<nav class="navbar topbar">
    <div class="container-fluid px-4 py-2">
        <a class="navbar-brand d-flex align-items-center gap-2 fw-semibold" href="/app">
            <span class="brand-mark">E</span><span>ERP Operations</span>
        </a>
        <span class="small muted">@reports.size() modules · @totalRecords records</span>
    </div>
</nav>

<main class="container-fluid px-4 py-4">
    <section class="hero p-4 p-lg-5 mb-4">
        <div class="row align-items-end g-4">
            <div class="col-lg-8">
                <div class="small text-uppercase opacity-75 mb-2">Control center</div>
                <h1 class="display-6 fw-semibold mb-2">Run the business from one workspace.</h1>
                <p class="lead opacity-75 mb-0">Live operational counts, module reporting and direct access to every ERP resource.</p>
            </div>
            <div class="col-lg-4 text-lg-end">
                <a class="btn btn-light px-4" href="#modules">View modules</a>
            </div>
        </div>
    </section>

    <div class="row g-3 mb-5">
        <div class="col-md-4">
            <div class="metric p-4 h-100"><div class="section-label">Modules</div><div class="display-6 fw-semibold mt-2">@reports.size()</div><div class="muted small">Reporting-enabled domains</div></div>
        </div>
        <div class="col-md-4">
            <div class="metric p-4 h-100"><div class="section-label">Resources</div><div class="display-6 fw-semibold mt-2">@resources.size()</div><div class="muted small">Repository-backed aggregates</div></div>
        </div>
        <div class="col-md-4">
            <div class="metric p-4 h-100"><div class="section-label">Records</div><div class="display-6 fw-semibold mt-2">@totalRecords</div><div class="muted small">Across all registered resources</div></div>
        </div>
    </div>

    <div id="modules" class="d-flex justify-content-between align-items-end mb-3">
        <div><div class="section-label">Operations</div><h2 class="h3 mb-1">Module dashboards</h2><p class="muted mb-0">Open a domain to inspect KPIs, reporting and records.</p></div>
    </div>

    <div class="row g-3">
        @for(var entry : reports.entrySet())
            <div class="col-12 col-md-6 col-xl-4">
                <a class="module-card d-block text-decoration-none text-dark p-4 h-100" href="/app/module/@entry.getKey()">
                    <div class="d-flex justify-content-between align-items-start mb-4">
                        <div class="module-icon">@entry.getKey().substring(0, 1).toUpperCase()</div>
                        <span class="badge rounded-pill text-bg-light">@entry.getValue().resourceCount() resources</span>
                    </div>
                    <h3 class="h5 mb-1 text-capitalize">@entry.getKey()</h3>
                    <div class="display-6 fw-semibold">@entry.getValue().totalRecords()</div>
                    <div class="muted small mb-3">records · @entry.getValue().populatedResources() active resources</div>
                    <span class="fw-semibold small">Open dashboard →</span>
                </a>
            </div>
        @endfor
    </div>
</main>
</body>
</html>