#!/usr/bin/env bash
# Análisis de salud del servidor Linux (KomainOS · variables del R3.1 · contrato JSON de métricas).
# Solo lectura: no modifica el servidor. Informa las 21 variables del R3.1 y la última línea es el objeto
# {"metricas": [...]}. Necesita privilegios de administrador para leer /etc/shadow, el registro del sistema y sshd -T.
#
# Parámetros (KomainOS los define antes de este código; si se ejecuta a mano, valen los de abajo):
#   MUESTREO_SEGUNDOS  ventana en la que se miden CPU, disco, red e intercambio
#   VENTANA_HORAS      periodo de los registros: autenticaciones fallidas, errores de disco y agotamiento de memoria
#   ARCHIVOS_CRITICOS  archivos de autenticación y configuración cuyos permisos se informan, separados por espacios
set -u
# «... | elementos CODIGO» se ejecuta en el proceso actual para que la métrica quede registrada
shopt -s lastpipe
export LC_ALL=C
: "${MUESTREO_SEGUNDOS:=5}"
: "${VENTANA_HORAS:=24}"
: "${ARCHIVOS_CRITICOS:=/etc/passwd /etc/group /etc/shadow /etc/gshadow /etc/sudoers /etc/ssh/sshd_config /etc/crontab}"
[ "$MUESTREO_SEGUNDOS" -ge 1 ] 2>/dev/null || MUESTREO_SEGUNDOS=1

metricas=()

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

# valor CODIGO VALOR [INSTANCIA]
valor() {
    if [ -n "${3:-}" ]; then
        metricas+=("{\"codigo\":\"$1\",\"instancia\":$(json_texto "$3"),\"valor\":$2}")
    else
        metricas+=("{\"codigo\":\"$1\",\"valor\":$2}")
    fi
}

# elementos CODIGO < un elemento por línea
elementos() {
    metricas+=("{\"codigo\":\"$1\",\"elementos\":$(json_lista)}")
}

# Porcentaje con dos decimales; 0 si no hubo actividad en la ventana
porcentaje() {
    awk -v p="$1" -v t="$2" 'BEGIN { printf "%.2f", (t > 0 ? p / t * 100 : 0) }'
}

# Registros del periodo: núcleo (-k) o los campos que se indiquen; vacío si no hay journald
registro() {
    command -v journalctl >/dev/null 2>&1 || return 0
    journalctl --no-pager -q --since "${VENTANA_HORAS} hours ago" "$@" 2>/dev/null
}

# ---- Muestreo: CPU, discos, intercambio y red en dos lecturas separadas por MUESTREO_SEGUNDOS ----
# Los contadores se imprimen con %.0f: algunos awk (mawk) abrevian los enteros grandes en notación científica

# total, ociosa e iowait: user nice system idle iowait irq softirq steal (guest ya está incluido en user)
leer_cpu() {
    awk '/^cpu / { printf "%.0f %.0f %.0f\n", $2 + $3 + $4 + $5 + $6 + $7 + $8 + $9, $5, $6 }' /proc/stat
}

# Discos con dispositivo real: excluye loop, ram, zram, md y los volúmenes lógicos (dm-*), que repetirían la medición
discos=" $(for d in /sys/block/*; do [ -e "$d/device" ] && basename "$d"; done | tr '\n' ' ')"
# nombre, operaciones completadas (lecturas + escrituras) y milisegundos dedicados a ellas
leer_discos() {
    awk -v lista="$discos " 'index(lista, " " $3 " ") { printf "%s %.0f %.0f\n", $3, $4 + $8, $7 + $11 }' /proc/diskstats
}

leer_intercambio() {
    awk '$1 == "pswpin" || $1 == "pswpout" { n += $2 } END { printf "%.0f\n", n }' /proc/vmstat
}

# Interfaces con dispositivo real: excluye lo, puentes y las interfaces virtuales de contenedores
interfaces=$(for i in /sys/class/net/*; do [ -e "$i/device" ] && basename "$i"; done)
leer_red() {
    local i
    for i in $interfaces; do
        echo "$i $(cat "/sys/class/net/$i/statistics/rx_packets") $(cat "/sys/class/net/$i/statistics/rx_errors")"
    done
}

read -r cpu_total_1 cpu_ociosa_1 cpu_espera_1 < <(leer_cpu)
declare -A disco_ops_1 disco_ms_1 red_paquetes_1 red_errores_1
while read -r d ops ms; do disco_ops_1[$d]=$ops; disco_ms_1[$d]=$ms; done < <(leer_discos)
intercambio_1=$(leer_intercambio)
while read -r i paquetes errores; do red_paquetes_1[$i]=$paquetes; red_errores_1[$i]=$errores; done < <(leer_red)

sleep "$MUESTREO_SEGUNDOS"

read -r cpu_total_2 cpu_ociosa_2 cpu_espera_2 < <(leer_cpu)
cpu_total=$((cpu_total_2 - cpu_total_1))
cpu_espera=$((cpu_espera_2 - cpu_espera_1))

# V01 y V03: tiempo ocupado y tiempo en espera de E/S sobre el total de la ventana
valor cpu_uso "$(porcentaje $((cpu_total - (cpu_ociosa_2 - cpu_ociosa_1) - cpu_espera)) $cpu_total)"
valor cpu_iowait "$(porcentaje $cpu_espera $cpu_total)"

# V04 y V09: operaciones por segundo y milisegundos promedio por operación, por disco
while read -r d ops ms; do
    delta_ops=$((ops - ${disco_ops_1[$d]:-$ops}))
    delta_ms=$((ms - ${disco_ms_1[$d]:-$ms}))
    valor disco_iops "$(awk -v o=$delta_ops -v s="$MUESTREO_SEGUNDOS" 'BEGIN { printf "%.2f", o / s }')" "$d"
    valor disco_latencia "$(awk -v m=$delta_ms -v o=$delta_ops 'BEGIN { printf "%.2f", (o > 0 ? m / o : 0) }')" "$d"
done < <(leer_discos)

# V07: páginas intercambiadas (entrada y salida) convertidas a KiB por segundo
pagina_kib=$(( $(getconf PAGESIZE) / 1024 ))
valor swap_act "$(awk -v p=$(( $(leer_intercambio) - intercambio_1 )) -v k=$pagina_kib -v s="$MUESTREO_SEGUNDOS" \
    'BEGIN { printf "%.2f", p * k / s }')"

# V11: paquetes con error sobre el total recibido (correctos más erróneos), por interfaz
while read -r i paquetes errores; do
    delta_errores=$((errores - ${red_errores_1[$i]:-$errores}))
    delta_total=$((paquetes - ${red_paquetes_1[$i]:-$paquetes} + delta_errores))
    valor red_error_rx "$(porcentaje $delta_errores $delta_total)" "$i"
done < <(leer_red)

# ---- Uso de recursos ----

# V02: carga promedio de 5 minutos por CPU lógica
valor cpu_load "$(awk -v n="$(nproc)" '{ printf "%.2f", $2 / n }' /proc/loadavg)"

# V05: memoria usada sin contar la que el sistema puede recuperar (MemAvailable)
valor mem_uso "$(awk '/^MemTotal:/ { t = $2 } /^MemAvailable:/ { d = $2 } END { printf "%.2f", (t > 0 ? (1 - d / t) * 100 : 0) }' /proc/meminfo)"

# V06: área de intercambio ocupada; 0 si el servidor no tiene swap
valor swap_uso "$(awk '/^SwapTotal:/ { t = $2 } /^SwapFree:/ { l = $2 } END { printf "%.2f", (t > 0 ? (1 - l / t) * 100 : 0) }' /proc/meminfo)"

# V08: por sistema de archivos montado, sin los virtuales en memoria; el punto de montaje puede tener espacios
while read -r uso montaje; do
    valor disco_uso "$uso" "$montaje"
done < <(df --output=pcent,target -x tmpfs -x devtmpfs -x squashfs -x overlay -x efivarfs 2>/dev/null | awk 'NR > 1 {
    uso = $1; sub(/%/, "", uso); $1 = ""; sub(/^ +/, "")
    if (uso ~ /^[0-9]+$/ && !visto[$0]++) print uso, $0
}')

# V19: días desde el último arranque
valor uptime_dias "$(awk '{ printf "%.2f", $1 / 86400 }' /proc/uptime)"

# ---- Registros del periodo ----

# V10: errores de E/S de los dispositivos en el registro del núcleo
valor disco_errores "$(registro -k | grep -ciE 'I/O error|medium error|critical target error' || true)"

# V12: inicios de sesión fallidos (auth y authpriv). Un intento por SSH deja la línea de sshd y la de PAM: se cuenta
# la de sshd, y la de PAM solo para los demás accesos (consola, su, sudo)
valor aut_fallidas "$(registro SYSLOG_FACILITY=4 SYSLOG_FACILITY=10 \
    | grep -E 'sshd\[[0-9]+\]: Failed |authentication failure' | grep -vc '(sshd:auth): authentication failure' || true)"

# V21: procesos finalizados por el OOM killer
valor mem_agotamiento "$(registro -k | grep -ci 'killed process' || true)"

# ---- Seguridad ----

# V13: 1 si root puede entrar por SSH (PermitRootLogin distinto de no) o si su cuenta tiene una contraseña utilizable
acceso=0
if command -v sshd >/dev/null 2>&1; then
    raiz_ssh=$(sshd -T 2>/dev/null | awk '$1 == "permitrootlogin" { print $2 }')
    # Sin sshd -T se lee la configuración; si no lo indica, el valor por omisión de OpenSSH permite entrar con llave
    [ -z "$raiz_ssh" ] && raiz_ssh=$(awk 'tolower($1) == "permitrootlogin" { print tolower($2); exit }' /etc/ssh/sshd_config 2>/dev/null)
    [ "${raiz_ssh:-prohibit-password}" != "no" ] && acceso=1
fi
if [ -r /etc/shadow ]; then
    # Bloqueada con ! o *; un campo vacío permite entrar sin contraseña
    case "$(awk -F: '$1 == "root" { print $2 }' /etc/shadow)" in
        '!'* | '*'*) ;;
        *) acceso=1 ;;
    esac
fi
valor admin_acceso "$acceso"

# V14: cuentas con uid 0 o miembros de sudo, wheel o admin que pueden iniciar sesión (shell distinta de nologin y false)
{
    awk -F: '$3 == 0 { print $1 }' /etc/passwd
    for grupo in sudo wheel admin; do
        getent group "$grupo" | awk -F: '{ n = split($4, m, ","); for (i = 1; i <= n; i++) print m[i] }'
    done
} | sort -u | while IFS= read -r cuenta; do
    case "$(getent passwd "$cuenta" | cut -d: -f7)" in
        */nologin | */false | "") ;;
        *) echo "$cuenta" ;;
    esac
done | elementos admin_no_aut

# V15: cada archivo crítico con su permiso octal (ruta:modo); la lista de referencia tiene los que exige la política
for archivo in $ARCHIVOS_CRITICOS; do
    [ -e "$archivo" ] && echo "$archivo:$(stat -c '%a' "$archivo")"
done | elementos arch_crit_permisos

# V16: puertos TCP en escucha fuera de la interfaz de bucle local (127.0.0.0/8 y ::1 no son accesibles desde la red)
ss -lnt 2>/dev/null | awk 'NR > 1 {
    direccion = $4; n = split(direccion, partes, ":"); puerto = partes[n]
    sub(":" puerto "$", "", direccion)
    if (direccion ~ /^127\./ || direccion ~ /^\[::(ffff:127\.[0-9.]+|1)\]$/ || direccion ~ /%lo$/) next
    print puerto
}' | sort -un | elementos red_puerto_no_aut

# V17: 1 si no está activo ninguno de los cortafuegos habituales (firewalld, ufw, nftables o iptables)
firewall=1
if systemctl is-active --quiet firewalld 2>/dev/null; then
    firewall=0
elif command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
    firewall=0
elif systemctl is-active --quiet nftables 2>/dev/null || systemctl is-active --quiet iptables 2>/dev/null; then
    firewall=0
fi
valor firewall_inactivo "$firewall"

# V18: 1 si SELinux no está en Enforcing o AppArmor no tiene perfiles en modo estricto (enforce)
mac=1
if command -v getenforce >/dev/null 2>&1; then
    [ "$(getenforce 2>/dev/null)" = "Enforcing" ] && mac=0
elif [ "$(cat /sys/module/apparmor/parameters/enabled 2>/dev/null)" = "Y" ]; then
    estrictos=$(grep -c ' (enforce)$' /sys/kernel/security/apparmor/profiles 2>/dev/null || true)
    [ "${estrictos:-0}" -gt 0 ] && mac=0
fi
valor mac_no_estricto "$mac"

# ---- Disponibilidad ----

# V20: servicios en ejecución; los requeridos que falten respecto de la lista son los no operativos
systemctl list-units --type=service --state=running --no-legend --plain 2>/dev/null \
    | awk '{ sub(/\.service$/, "", $1); print $1 }' | elementos serv_no_operativo

echo "Análisis de $(hostname): ${#metricas[@]} mediciones en una ventana de ${MUESTREO_SEGUNDOS} s"
printf '{"metricas":[%s]}\n' "$(IFS=,; echo "${metricas[*]}")"
