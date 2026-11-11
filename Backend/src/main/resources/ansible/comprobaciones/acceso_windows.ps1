# Prueba de acceso de KomainOS (RF71, DEC-57). Si este script se ejecuta, el controlador llegó al servidor, inició
# sesión por WinRM y aplicó la elevación configurada en la cuenta (ninguna o runas): falta solo informar los permisos
# con los que se ejecutarán los scripts. No los exige; la última línea le dice a KomainOS si los tiene.
$identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identidad)
$equipo = $env:COMPUTERNAME
if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Output "Acceso verificado en ${equipo}: los scripts se ejecutarán como $($identidad.Name), con privilegios de administrador"
    Write-Output "KOMAINOS_ADMINISTRADOR=si"
    exit 0
}
$detalle = 'sin privilegios de administrador'
try {
    # Un administrador local distinto del integrado entra por WinRM con el token filtrado por el control de cuentas
    $miembro = Get-LocalGroupMember -SID 'S-1-5-32-544' -ErrorAction Stop |
        Where-Object { $_.SID -eq $identidad.User }
    if ($miembro) {
        $detalle += '; pertenece a Administradores pero la sesión remota recibió el token filtrado: elija la elevación runas en la cuenta'
    }
} catch {
    # Sin Get-LocalGroupMember (por ejemplo, en un controlador de dominio) solo se informa lo que se ve
}
Write-Output "Acceso verificado en ${equipo}: los scripts se ejecutarán como $($identidad.Name), $detalle"
Write-Output "KOMAINOS_ADMINISTRADOR=no"
