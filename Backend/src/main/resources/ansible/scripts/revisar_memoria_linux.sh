#!/usr/bin/env bash
# Revisar consumo de memoria (KomainOS · mantenimiento de diagnóstico, sin cambios en el servidor).
# Parámetro que KomainOS define antes de este código:
#   TOP_PROCESOS  cantidad de procesos con más memoria que se informan
set -eu

echo "Memoria del servidor $(hostname):"
free -m
echo
echo "Los $TOP_PROCESOS procesos con más memoria:"
ps -eo pid,user,comm,%mem,rss --sort=-%mem | head -n $((TOP_PROCESOS + 1))
