<#
.SYNOPSIS
  Exposes Windows host port-1883 TCP connections via HTTP on 127.0.0.1:9998.
  The debug-proxy Docker container reaches it via host.docker.internal:9998 to
  determine the real source IP of MQTT-connected devices (Docker NAT hides IPs
  inside containers, but the Windows host sees the real addresses).

.SECURITY
  Bound to 127.0.0.1 ONLY — unreachable from any external network.
  No credentials are required because the listener is localhost-only.

.USAGE
  PowerShell -ExecutionPolicy Bypass -File netstat-service.ps1

  To auto-start on login (no admin required):
    Add to Task Scheduler → "At log on" → run as current user
    Program: powershell.exe
    Args:    -WindowStyle Hidden -ExecutionPolicy Bypass -File "d:\kapua_0609\kapua_cms_avacast_1.6.12\netstat-service.ps1"
#>

$Port = 9998

function Get-MqttConnections {
    try {
        $conns = @(Get-NetTCPConnection -LocalPort 1883 -State Established -ErrorAction SilentlyContinue)
        if ($conns.Count -eq 0) { return '[]' }
        $items = $conns | ForEach-Object {
            # Strip IPv4-mapped IPv6 prefix (e.g. "::ffff:172.16.19.69" -> "172.16.19.69")
            $ip = $_.RemoteAddress -replace '^::ffff:', ''
            '{"ip":"' + $ip + '","port":' + $_.RemotePort + '}'
        }
        return '[' + ($items -join ',') + ']'
    } catch {
        Write-Host "[netstat-service] Get-Connections error: $_"
        return '[]'
    }
}

$endpoint = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Parse('127.0.0.1'), $Port)
$listener = [System.Net.Sockets.TcpListener]::new($endpoint)
$listener.Server.SetSocketOption(
    [System.Net.Sockets.SocketOptionLevel]::Socket,
    [System.Net.Sockets.SocketOptionName]::ReuseAddress,
    $true
)
$listener.Start()
Write-Host "[netstat-service] Listening on 127.0.0.1:$Port — press Ctrl+C to stop"

try {
    while ($true) {
        $client = $null
        try {
            $client = $listener.AcceptTcpClient()
            $client.ReceiveTimeout = 2000
            $client.SendTimeout    = 2000

            $stream = $client.GetStream()

            # Drain HTTP request (we don't parse it; every GET gets the same response)
            $buf = New-Object byte[] 4096
            try { [void]$stream.Read($buf, 0, $buf.Length) } catch {}

            $body        = Get-MqttConnections
            $bodyBytes   = [System.Text.Encoding]::UTF8.GetBytes($body)
            $headerText  = "HTTP/1.1 200 OK`r`nContent-Type: application/json; charset=utf-8`r`nContent-Length: $($bodyBytes.Length)`r`nConnection: close`r`n`r`n"
            $headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerText)

            $stream.Write($headerBytes, 0, $headerBytes.Length)
            $stream.Write($bodyBytes,   0, $bodyBytes.Length)
            $stream.Flush()

            Write-Host "[netstat-service] $(Get-Date -Format 'HH:mm:ss') → $body"
        } catch {
            Write-Host "[netstat-service] Request error: $_"
        } finally {
            try { if ($client) { $client.Close() } } catch {}
        }
    }
} finally {
    $listener.Stop()
    Write-Host "[netstat-service] Stopped."
}
