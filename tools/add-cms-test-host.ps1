$hostsPath = 'C:\Windows\System32\drivers\etc\hosts'
$ip = '172.16.19.37'
$hostName = 'cms.test'
$begin = '# BEGIN Codex cms.test'
$end = '# END Codex cms.test'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Write-Error 'Please run this script from an Administrator PowerShell.'
  exit 1
}

$backup = Join-Path $PSScriptRoot ("hosts.backup.{0}" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
Copy-Item -LiteralPath $hostsPath -Destination $backup -Force

$content = Get-Content -LiteralPath $hostsPath -Raw
$content = [regex]::Replace($content, "(?ms)^# BEGIN Codex cms\.test\r?\n.*?^# END Codex cms\.test\r?\n?", '')

$lines = $content -split "\r?\n"
$filtered = foreach ($line in $lines) {
  if ($line -match '(^|\s)cms\.test(\s|$)' -and $line.TrimStart() -notmatch '^#') {
    continue
  }
  $line
}

$newBlock = @($begin, "$ip $hostName", $end)
$newContent = (($filtered | ForEach-Object { $_.TrimEnd() }) -join [Environment]::NewLine).TrimEnd()
$newContent = $newContent + [Environment]::NewLine + [Environment]::NewLine + ($newBlock -join [Environment]::NewLine) + [Environment]::NewLine

Set-Content -LiteralPath $hostsPath -Value $newContent -Encoding ASCII
Clear-DnsClientCache

Write-Host "Added $hostName -> $ip"
Write-Host "Backup saved to $backup"
