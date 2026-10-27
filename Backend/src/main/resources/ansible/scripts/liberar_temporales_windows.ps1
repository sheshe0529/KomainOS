# Liberar archivos temporales (KomainOS · mantenimiento).
# Parámetros que KomainOS define antes de este código:
#   $RUTA_TEMPORAL   carpeta que se limpia, por ejemplo C:\Windows\Temp
#   $DIAS_RETENCION  se eliminan los archivos sin modificar hace más de estos días
#   $SOLO_SIMULAR    $true: informa lo que eliminaría sin borrar nada
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $RUTA_TEMPORAL -PathType Container)) {
    [Console]::Error.WriteLine("La carpeta $RUTA_TEMPORAL no existe")
    exit 2
}

$limite = (Get-Date).AddDays(-$DIAS_RETENCION)
$archivos = @(Get-ChildItem -LiteralPath $RUTA_TEMPORAL -File -Recurse -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $limite })
$megas = [math]::Round((($archivos | Measure-Object -Property Length -Sum).Sum) / 1MB, 1)

if ($SOLO_SIMULAR) {
    Write-Output "Simulación: se eliminarían $($archivos.Count) archivo(s) ($megas MB) de $RUTA_TEMPORAL sin cambios en $DIAS_RETENCION días"
    $archivos | Select-Object -First 50 | ForEach-Object { '{0:yyyy-MM-dd}  {1,10}  {2}' -f $_.LastWriteTime, $_.Length, $_.FullName }
    exit 0
}

$eliminados = 0
foreach ($archivo in $archivos) {
    try {
        Remove-Item -LiteralPath $archivo.FullName -Force
        $eliminados++
    } catch {
        # Un archivo en uso no detiene la limpieza: se informa al final
    }
}
Write-Output "Se eliminaron $eliminados de $($archivos.Count) archivo(s) ($megas MB) de $RUTA_TEMPORAL"
