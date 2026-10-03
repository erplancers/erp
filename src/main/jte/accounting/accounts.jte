@param java.util.List<com.erplancers.erp.accounting.domain.Account> accounts
@param int page
@param int size
@param int totalPages
@param long totalElements

<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Chart of Accounts</title><link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css"><link rel="stylesheet" href="/css/erp.css"></head>
<body><div class="app-shell"><aside class="sidebar"><a class="brand" href="/">ERPLancers</a>
<div class="nav-section">Workspace</div><a class="nav-link" href="/">Dashboard</a><div class="nav-section">Finance</div><a class="nav-link active" href="/accounts">Chart of Accounts</a><a class="nav-link" href="#">Invoices</a><a class="nav-link" href="#">Payments</a>
<div class="nav-section">Operations</div><a class="nav-link" href="#">Customers</a><a class="nav-link" href="#">Products</a><a class="nav-link" href="#">Sales Orders</a><a class="nav-link" href="#">Purchasing</a></aside>
<main class="main-content"><header class="topbar"><div><span class="eyebrow">ACCOUNTING</span><h1>Chart of Accounts</h1></div><a href="/" class="btn btn-outline-secondary">Dashboard</a></header>
<div class="panel"><div class="table-toolbar"><div><strong>${totalElements</strong> accounts</div><div class="d-flex gap-2"><input class="form-control" type="search" placeholder="Search coming next"><button class="btn btn-dark" type="button" disabled>New account</button></div></div>
<div class="table-responsive"><table class="table align-middle erp-table"><thead><tr><th>Code</th><th>Name</th><th>Type</th><th>Balance</th><th>Level</th><th>Status</th></tr></thead><tbody>
@for (var account : accounts)
<tr><td class="mono">${account.getCode()</td><td><strong>${account.getName()</strong></td><td>${account.getAccountType()</td><td>${account.getBalanceType()</td><td>${account.getLevel()</td><td>@if (account.isBlocked())<span class="badge text-bg-secondary">Blocked</span>@else<span class="badge text-bg-success">Active</span>@endif</td></tr>
@endfor
@if (accounts.isEmpty())<tr><td colspan="6" class="empty-state">No accounts found.</td></tr>@endif
</tbody></table></div>
@if (totalPages > 1)<nav class="pagination-bar"><a class="btn btn-sm btn-outline-secondary" href="/accounts?page=${Math.max(0, page - 1)&size=${size">Previous</a><span>Page ${(page + 1) of ${totalPages</span><a class="btn btn-sm btn-outline-secondary" href="/accounts?page=${Math.min(totalPages - 1, page + 1)&size=${size">Next</a></nav>@endif
</div></main></div></body></html>
