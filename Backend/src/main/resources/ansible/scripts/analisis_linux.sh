#!/usr/bin/env bash
# Análisis básico de salud para Linux (KomainOS · contrato JSON de métricas).
# Solo lectura: no modifica el servidor. La última línea es el objeto {"metricas": [...]}.
# Ejemplo provisional: ajuste las métricas a las que defina el catálogo de su organización.
set -u

# Texto JSON con comillas y barras invertidas escapadas
json_texto() {
    local s=${1//\\/\\\\}
    printf '"%s"' "${s//\"/\\\"}"
}

# Lista JSON con una entrada por línea de la entrada estándar
json_lista() {
    local primero=1 elemento
    printf '['
    while IFS= read -r elemento; do
        [ -z "$elemento" ] && continue
        [ "$primero" -eq 1 ] || printf ','
        json_texto "$elemento"
        primero=0
    done
    printf ']'
}

# Uso de CPU: diferencia entre dos lecturas de /proc/stat separadas por un segundo
leer_cpu() {
    awk '/^cpu / { ociosa = $5 + $6; total = 0; for (i = 2; i <= NF; i++) total += $i; print total, ociosa }' /proc/stat
}
read -r total_1 ociosa_1 < <(leer_cpu)
sleep 1
read -r total_2 ociosa_2 < <(leer_cpu)
cpu=$(awk -v t=$((total_2 - total_1)) -v o=$((ociosa_2 - ociosa_1)) 'BEGIN { printf "%.2f", (t > 0 ? (1 - o / t) * 100 : 0) }')

memoria=$(awk '/^MemTotal:/ { t = $2 } /^MemAvailable:/ { d = $2 } END { printf "%.2f", (t > 0 ? (1 - d / t) * 100 : 0) }' /proc/meminfo)
swap=$(awk '/^SwapTotal:/ { t = $2 } /^SwapFree:/ { l = $2 } END { printf "%.2f", (t > 0 ? (1 - l / t) * 100 : 0) }' /proc/meminfo)
disco=$(df -P / | awk 'NR == 2 { sub("%", "", $5); print $5 }')
carga=$(awk '{ print $3 }' /proc/loadavg)

puertos=$(ss -H -lnt 2>/dev/null | awk '{ n = split($4, partes, ":"); print partes[n] }' | sort -un | json_lista)
servicios=$(systemctl list-units --type=service --state=running --no-legend --plain 2>/dev/null \
    | awk '{ sub(/\.service$/, "", $1); print $1 }' | json_lista)
# Cuentas de personas y root: las del sistema (uid < 1000) y nobody no se informan
usuarios=$(awk -F: '$3 == 0 || ($3 >= 1000 && $3 != 65534) { print $1 }' /etc/passwd | json_lista)

echo "Análisis de $(hostname) completado"
printf '{"metricas":['
printf '{"codigo":"cpu_uso_pct","valor":%s},' "$cpu"
printf '{"codigo":"memoria_uso_pct","valor":%s},' "$memoria"
printf '{"codigo":"disco_uso_pct","instancia":"/","valor":%s},' "$disco"
printf '{"codigo":"uso_swap","valor":%s},' "$swap"
printf '{"codigo":"carga_promedio","valor":%s},' "$carga"
printf '{"codigo":"puertos_escucha","elementos":%s},' "$puertos"
printf '{"codigo":"servicios_ejecucion","elementos":%s},' "$servicios"
printf '{"codigo":"usuarios_locales","elementos":%s}' "$usuarios"
printf ']}\n'
