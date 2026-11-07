# Comprueba autenticación sin exigir pertenencia al grupo Administradores.
$ErrorActionPreference = 'Stop'
$identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
Write-Output "Acceso de evaluación verificado como $($identidad.Name) en $env:COMPUTERNAME"
