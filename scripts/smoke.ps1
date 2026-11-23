param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$buyer = 'smoke_' + [guid]::NewGuid().ToString('N').Substring(0,12)
$credentials = @{ username=$buyer; password='student-demo-password' } | ConvertTo-Json
$session = Invoke-RestMethod "$BaseUrl/api/auth/register" -Method Post -ContentType 'application/json' -Body $credentials
$headers = @{ Authorization="Bearer $($session.accessToken)"; 'Idempotency-Key'=[guid]::NewGuid().ToString('N') }
$products = @(Invoke-RestMethod "$BaseUrl/api/products")
if ($products.Count -eq 0) { throw 'No products; start with the demo profile.' }
$body = @{productId=$products[0].id; quantity=1} | ConvertTo-Json
$order = Invoke-RestMethod "$BaseUrl/api/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$retry = Invoke-RestMethod "$BaseUrl/api/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
if ($order.id -ne $retry.id) { throw 'Idempotency check failed' }
$paid = Invoke-RestMethod "$BaseUrl/api/orders/$($order.id)/simulate-payment" -Method Post -Headers $headers
if ($paid.status -ne 'PAID') { throw 'Payment did not transition to PAID' }
$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    $notifications = @(Invoke-RestMethod "$BaseUrl/api/notifications" -Headers $headers)
    if (@($notifications | Where-Object { $_.orderId -eq $order.id -and $_.type -eq 'ORDER_PAID' }).Count -gt 0) { break }
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $deadline)
if (@($notifications | Where-Object { $_.orderId -eq $order.id -and $_.type -eq 'ORDER_PAID' }).Count -eq 0) { throw 'Kafka notification did not arrive in 30 seconds' }
Invoke-RestMethod "$BaseUrl/api/auth/logout" -Method Post -Headers $headers
Write-Output "PASS: register -> create -> idempotent retry -> simulated payment -> Kafka notification -> logout ($($order.id))"
