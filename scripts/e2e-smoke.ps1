$ErrorActionPreference = "Stop"

$apiBaseUrl = if ($env:STATUSFLOW_API_BASE_URL) { $env:STATUSFLOW_API_BASE_URL } else { "http://localhost:8000" }

$health = Invoke-RestMethod "$apiBaseUrl/health"
$users = Invoke-RestMethod "$apiBaseUrl/users"
$ordersBefore = Invoke-RestMethod "$apiBaseUrl/orders"

$customer = $users | Where-Object { $_.role -eq "customer" } | Select-Object -First 1
$operator = $users | Where-Object { $_.role -eq "operator" } | Select-Object -First 1

if (-not $customer -or -not $operator) {
    throw "Expected seeded customer and operator users."
}

$created = Invoke-RestMethod `
    -Method Post `
    -Uri "$apiBaseUrl/orders" `
    -ContentType "application/json" `
    -Body (@{
        title = "Scripted smoke order"
        description = "Created by scripts/e2e-smoke.ps1"
        customer_id = $customer.id
    } | ConvertTo-Json)

$commented = Invoke-RestMethod `
    -Method Post `
    -Uri ("$apiBaseUrl/orders/{0}/comments" -f $created.id) `
    -ContentType "application/json" `
    -Body (@{
        author_id = $operator.id
        body = "Smoke test operator note."
    } | ConvertTo-Json)

$transitioned = Invoke-RestMethod `
    -Method Post `
    -Uri ("$apiBaseUrl/orders/{0}/status-transitions" -f $created.id) `
    -ContentType "application/json" `
    -Body (@{
        changed_by_id = $operator.id
        to_status = "in_review"
        reason = "Smoke test review start"
    } | ConvertTo-Json)

$invalidStatusCode = $null
try {
    Invoke-RestMethod `
        -Method Post `
        -Uri ("$apiBaseUrl/orders/{0}/status-transitions" -f $created.id) `
        -ContentType "application/json" `
        -Body (@{
            changed_by_id = $operator.id
            to_status = "new"
            reason = "Invalid reverse transition"
        } | ConvertTo-Json) | Out-Null
} catch {
    $invalidStatusCode = $_.Exception.Response.StatusCode.value__
}

[ordered]@{
    health = $health.status
    users = $users.Count
    orders_before = $ordersBefore.Count
    created_code = $created.code
    comment_count = $commented.comments.Count
    final_status = $transitioned.status
    invalid_transition_status = $invalidStatusCode
} | ConvertTo-Json -Compress
