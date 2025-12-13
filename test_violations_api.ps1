# 测试违章上报接口
$url = "http://localhost:8081/api/violations/report"
$headers = @{
    "Content-Type" = "application/json"
}

# 根据 Postman 集合中的请求格式
$body = @{
    type = "speeding"
    intersectionId = "intersection-1"
    vehicleId = "vehicle-001"
    timestamp = 1702400000
    speed = 85
    description = "超速行驶"
} | ConvertTo-Json

Write-Host "Testing POST /api/violations/report"
Write-Host "URL: $url"
Write-Host "Body: $body"
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri $url -Method POST -Headers $headers -Body $body -ErrorAction Stop
    Write-Host "✓ SUCCESS (Status: $($response.StatusCode))"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "✗ FAILED"
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)"
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.Exception.Response.Content) {
        Write-Host "Response: $($_.Exception.Response.Content | ConvertFrom-Json)"
    }
}
