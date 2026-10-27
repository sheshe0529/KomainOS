# Callback de salida de KomainOS: imprime una sola línea KOMAINOS_JSON= con el resultado de la tarea
# komainos_resultado. Solo incluye estado, código de retorno, salida y mensaje: nunca argumentos, variables
# ni inventario, porque el inventario contiene la contraseña de la cuenta de servicio.
from __future__ import annotations

import json

from ansible.plugins.callback import CallbackBase

DOCUMENTATION = '''
    name: komainos_json
    type: stdout
    short_description: Resultado estructurado para KomainOS
    description:
      - Emite una línea KOMAINOS_JSON con el estado de la tarea komainos_resultado.
'''


class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = 'stdout'
    CALLBACK_NAME = 'komainos_json'

    def _emitir(self, result, estado):
        if result._task.get_name() != 'komainos_resultado':
            return
        r = result._result
        salida = {
            'estado': estado,
            'rc': r.get('rc'),
            'stdout': r.get('stdout', ''),
            'stderr': r.get('stderr', ''),
            'msg': r.get('msg', ''),
        }
        self._display.display('KOMAINOS_JSON=' + json.dumps(salida, ensure_ascii=True, default=str))

    def v2_runner_on_ok(self, result):
        self._emitir(result, 'ok')

    def v2_runner_on_failed(self, result, ignore_errors=False):
        self._emitir(result, 'failed')

    def v2_runner_on_unreachable(self, result):
        self._emitir(result, 'unreachable')

    def v2_runner_on_skipped(self, result):
        self._emitir(result, 'skipped')
