#!/bin/bash
set -eu
python3 - <<'PYTHON'
import json, os, shutil, subprocess, time
def cpu():
 valores=list(map(int,open("/proc/stat").readline().split()[1:9]));return sum(valores),sum(valores[3:5])
a=cpu();time.sleep(0.2);z=cpu()
def ejecutar(args):
 try:return subprocess.check_output(args,text=True,stderr=subprocess.DEVNULL).splitlines()
 except (OSError,subprocess.CalledProcessError):return []
puertos=sorted({line.split()[3].rsplit(":",1)[-1] for line in ejecutar(["ss","-H","-lnt"]) if len(line.split())>3})
servicios=[line.split()[0].removesuffix(".service") for line in ejecutar(["systemctl","list-units","--type=service","--state=running","--no-legend","--plain"]) if line.split()]
mem = dict((p[0].rstrip(':'), int(p[1])) for line in open('/proc/meminfo') if len(p := line.split()) >= 2)
disco = shutil.disk_usage('/')
metricas = [
 {'codigo':'cpu_uso_pct','valor':round((1-(z[1]-a[1])/max(1,z[0]-a[0]))*100,2)},
 {'codigo':'memoria_uso_pct','valor':round((1-mem.get('MemAvailable',mem.get('MemFree',0))/mem['MemTotal'])*100,2)},
 {'codigo':'disco_uso_pct','instancia':'sistema','valor':round(disco.used/disco.total*100,2)},
 {'codigo':'carga_promedio','valor':round(os.getloadavg()[2],2)},
 {'codigo':'uso_swap','valor':round((1-mem.get('SwapFree',0)/mem['SwapTotal'])*100,2) if mem.get('SwapTotal',0) else 0},
 {'codigo':'puertos_escucha','elementos':puertos},
 {'codigo':'servicios_ejecucion','elementos':servicios},
 {'codigo':'usuarios_locales','elementos':[x.split(':')[0] for x in open('/etc/passwd')]}
]
# La definición final de variables y umbrales debe aprobarse antes de activar REAL.
print(json.dumps({'metricas':metricas}))
PYTHON
