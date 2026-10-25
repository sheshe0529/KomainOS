from ansible.plugins.callback import CallbackBase
import json

class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = 'stdout'
    CALLBACK_NAME = 'komainos_json'

    def _emitir(self, result, fallo=False):
        if result._task.get_name() != 'komainos_resultado':
            return
        r = result._result
        # Solo el contrato de salida; nunca argumentos, variables o inventario.
        salida = {'rc': r.get('rc', 1 if fallo else 0), 'stdout': r.get('stdout', ''),
                  'error': r.get('stderr') or r.get('msg') or ('Ejecución fallida' if fallo else '')}
        self._display.display('KOMAINOS_JSON=' + json.dumps(salida, ensure_ascii=True))

    def v2_runner_on_ok(self, result):
        self._emitir(result)

    def v2_runner_on_failed(self, result, ignore_errors=False):
        self._emitir(result, True)

    def v2_runner_on_unreachable(self, result):
        self._emitir(result, True)
