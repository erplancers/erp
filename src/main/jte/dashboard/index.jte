@param long accountCount
@param long customerCount
@param long productCount
@param long orderCount
@param long purchaseCount
@param long invoiceCount

<!doctype html>
<html lang="en">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>ERP Dashboard</title>
<link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css"><link rel="stylesheet" href="/css/erp.css">
</head>
<body><div class="app-shell">
<aside class="sidebar"><a class="brand" href="/">ERPLancers</a>
<div class="nav-section">Workspace</div><a class="nav-link active" href="/">Dashboard</a>
<div class="nav-section">Finance</div><a class="nav-link" href="/accounts">Chart of Accounts</a><a class="nav-link" href="#">Invoices</a><a class="nav-link" href="#">Payments</a>
<div class="nav-section">Operations</div><a class="nav-link" href="#">Customers</a><a class="nav-link" href="#">Products</a><a class="nav-link" href="#">Sales Orders</a><a class="nav-link" href="#">Purchasing</a></aside>
<main class="main-content">
<header class="topbar"><div><span class="eyebrow">ERP WORKSPACE</span><h1>Dashboard</h1></div><a class="btn btn-dark" href="/accounts">Open accounts</a></header>
<section class="hero-card"><div><span class="eyebrow">OPERATIONS OVERVIEW</span><h2>Your ERP in one place.</h2><p>Navigate finance, CRM, inventory, sales and procurement from a single server-rendered workspace.</p></div></section>
<section class="metric-grid">
<a class="metric-card" href="/accounts"><span>Accounts</span><strong>${accountCount</strong><small>Chart of accounts</small></a>
<div class="metric-card"><span>Customers</span><strong>${customerCount</strong><small>CRM records</small></div>
<div class="metric-card"><span>Products</span><strong>${productCount</strong><small>Inventory catalog</small></div>
<div class="metric-card"><span>Sales orders</span><strong>${orderCount</strong><small>Order pipeline</small></div>
<div class="metric-card"><span>Purchases</span><strong>${purchaseCount</strong><small>Procurement</small></div>
<div class="metric-card"><span>Invoices</span><strong>${invoiceCount</strong><small>Finance</small></div>
</section>
<section class="content-grid"><div class="panel"><div class="panel-heading"><div><span class="eyebrow">MODULES</span><h3>ERP domains</h3></div></div>
<div class="module-list">
<a href="/accounts"><span class="module-icon">A</span><div><strong>Accounting</strong><small>Accounts, journals, fiscal periods and reports</small></div><span>→</span></a>
<a href="#"><span class="module-icon">C</span><div><strong>CRM</strong><small>Customers, contacts, leads and opportunities</small></div><span>→</span></a>
<a href="#"><span class="module-icon">I</span><div><strong>Inventory</strong><small>Products, stock and warehouses</small></div><span>→</span></a>
<a href="#"><span class="module-icon">S</span><div><strong>Sales</strong><small>Quotes, orders and pricing</small></div><span>→</span></a>
</div></div>
<div class="panel"><div class="panel-heading"><div><span class="eyebrow">ARCHITECTURE</span><h3>Server-rendered MVC</h3></div></div>
<p class="muted">Spring MVC controllers provide view models to JTE templates. JPA repositories remain the data boundary, while Bootstrap supplies responsive primitives.</p>
<div class="stack-tags"><span>Spring Boot 4</span><span>JTE</span><span>JPA</span><span>Bootstrap 5</span></div></div></section>
</main></div></body></html>
