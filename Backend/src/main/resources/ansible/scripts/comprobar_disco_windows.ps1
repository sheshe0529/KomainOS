$ErrorActionPreference='Stop'
Get-Volume | Select-Object DriveLetter,HealthStatus,SizeRemaining,Size | ConvertTo-Json -Compress
