# Comprobación de acceso de KomainOS (RF71): conectividad, autenticación y permisos de administrador.
# Termina con 0 si la cuenta pertenece al grupo de administradores, y con 3 si no tiene privilegios.
$identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identidad)
if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Output "Acceso verificado como $($identidad.Name) en $env:COMPUTERNAME"
    exit 0
}
[Console]::Error.WriteLine("La cuenta $($identidad.Name) se autenticó, pero no pertenece al grupo de administradores")
exit 3
