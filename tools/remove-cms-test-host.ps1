$hostsPath = 'C:\Windows\System32\drivers\etc\hosts'
$hostName = 'cms.test'

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

$newContent = (($filtered | ForEach-Object { $_.TrimEnd() }) -join [Environment]::NewLine).TrimEnd() + [Environment]::NewLine

Set-Content -LiteralPath $hostsPath -Value $newContent -Encoding ASCII
Clear-DnsClientCache

Write-Host "Removed $hostName"
Write-Host "Backup saved to $backup"
