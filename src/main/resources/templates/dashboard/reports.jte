@param com.erplancers.erp.common.reporting.ModuleReportingService.ModuleReport report

<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>ERP | @report.module() reports</title><link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css">
<style>body{background:#f5f7fa;color:#172033}.topbar{background:#fff;border-bottom:1px solid #e7eaf0}.panel{background:#fff;border:1px solid #e7eaf0;border-radius:16px}.muted{color:#667085}.label{font-size:.7rem;letter-spacing:.08em;text-transform:uppercase;font-weight:700;color:#667085}</style>
</head><body><nav class="navbar topbar"><div class="container-fluid px-4 py-2"><a class="navbar-brand fw-semibold" href="/app">ERP Operations</a><a class="btn btn-sm btn-outline-secondary" href="/app/module/@report.module()">Dashboard</a></div></nav>
<main class="container-fluid px-4 py-4"><div class="mb-4"><div class="label">@report.module() reporting</div><h1 class="display-6 fw-semibold text-capitalize">Operational reports</h1><p class="muted">Resource counts, numeric summaries and status distributions available through the reporting service.</p></div>
<div class="row g-4">
@for(var resource : report.resources())
<div class="col-12 col-xl-6"><div class="panel p-4 h-100"><div class="d-flex justify-content-between"><div><h2 class="h5 mb-1">@resource.name()</h2><div class="muted small">@resource.slug()</div></div><span class="badge text-bg-light">@resource.count() records</span></div>
@if(!resource.numeric().isEmpty())<hr><h3 class="h6">Numeric summaries</h3>
@for(var metric : resource.numeric().entrySet())<div class="d-flex justify-content-between py-2 border-bottom"><span class="small">@metric.getKey()</span><span class="small fw-semibold">sum @metric.getValue().sum()</span></div>@endfor
@endif
@if(!resource.enums().isEmpty())<hr><h3 class="h6">Status distribution</h3>
@for(var dist : resource.enums().entrySet())<div class="mb-3"><div class="small fw-semibold mb-1">@dist.getKey()</div>@for(var value : dist.getValue().entrySet())<div class="d-flex justify-content-between small muted"><span>@value.getKey()</span><span>@value.getValue()</span></div>@endfor</div>@endfor
@endif
</div></div>
@endfor
</div></main></body></html>