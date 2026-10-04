@param com.erplancers.erp.common.reporting.ModuleReportingService.ModuleReport report
@param java.util.Collection<com.erplancers.erp.common.web.CrudRegistry.CrudDescriptor> resources

<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>ERP | @report.module()</title>
<link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
<style>
body{background:#f5f7fa;color:#172033}.topbar{background:#fff;border-bottom:1px solid #e7eaf0}.panel{background:#fff;border:1px solid #e7eaf0;border-radius:16px}.muted{color:#667085}.label{font-size:.7rem;letter-spacing:.08em;text-transform:uppercase;font-weight:700;color:#667085}.resource-row{border-bottom:1px solid #edf0f4}.resource-row:last-child{border-bottom:0}
</style>
</head>
<body>
<nav class="navbar topbar"><div class="container-fluid px-4 py-2"><a class="navbar-brand fw-semibold" href="/app">ERP Operations</a><div class="d-flex gap-2"><a class="btn btn-sm btn-outline-secondary" href="/app/module/@report.module()/reports">Reports</a><a class="btn btn-sm btn-dark" href="/app">All modules</a></div></div></nav>
<main class="container-fluid px-4 py-4">
<div class="mb-4"><div class="label">@report.module()</div><h1 class="display-6 fw-semibold text-capitalize mb-1">@report.module() dashboard</h1><p class="muted mb-0">Operational KPIs and repository-backed resources for this domain.</p></div>
<div class="row g-3 mb-4">
<div class="col-md-4"><div class="panel p-4"><div class="label">Records</div><div class="display-6 fw-semibold mt-2">@report.totalRecords()</div><div class="muted small">Total records in module</div></div></div>
<div class="col-md-4"><div class="panel p-4"><div class="label">Resources</div><div class="display-6 fw-semibold mt-2">@report.resourceCount()</div><div class="muted small">Registered aggregates</div></div></div>
<div class="col-md-4"><div class="panel p-4"><div class="label">Active</div><div class="display-6 fw-semibold mt-2">@report.populatedResources()</div><div class="muted small">Resources containing records</div></div></div>
</div>
<div class="row g-4">
<div class="col-xl-8"><div class="panel p-4"><div class="d-flex justify-content-between align-items-center mb-3"><div><h2 class="h5 mb-1">Resources</h2><div class="muted small">Create, inspect and manage operational records.</div></div></div>
@for(var resource : resources)
<div class="resource-row py-3 d-flex justify-content-between align-items-center"><div><div class="fw-semibold">@resource.entityType().getSimpleName()</div><div class="muted small">/@resource.slug()</div></div><div class="d-flex gap-2"><a class="btn btn-sm btn-outline-secondary" href="/app/@resource.slug()">Records</a><a class="btn btn-sm btn-dark" href="/app/@resource.slug()/new">New</a></div></div>
@endfor
</div></div>
<div class="col-xl-4"><div class="panel p-4"><h2 class="h5">Numeric KPIs</h2><p class="muted small">Values sampled from the first 100 records of each resource.</p>
@for(var metric : report.numericMetrics())
<div class="border-top py-3"><div class="small fw-semibold">@metric.resource() · @metric.field()</div><div class="fs-5 fw-semibold">@metric.summary().sum()</div><div class="muted small">sum · min @metric.summary().min() · max @metric.summary().max()</div></div>
@endfor
@if(report.numericMetrics().isEmpty())<div class="muted small py-3">No numeric metrics are available yet.</div>@endif
</div></div>
</div>
</main>
</body></html>