

@param gg.jte.Content pageTitle
@param gg.jte.Content content


<!DOCTYPE html>
<html lang="en">
<head>
    <title>${pageTitle} - ${("app.title")}</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <link rel="icon" type="image/x-icon" href="/favicon.ico">
    <link href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" />
    <link href="/css/app.css" rel="stylesheet" />
    <script src="/webjars/bootstrap/5.3.8/js/bootstrap.bundle.min.js" defer></script>
</head>
<body>
<header class="bg-light">

</header>
<main class="my-5">
    <div class="container">

        ${content}
    </div>
</main>
</body>
</html>