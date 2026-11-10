package com.komainos.mantenimiento;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.FinalidadCuenta;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.service.ServicioAsignacionCuentas;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.service.ServicioConectividad;
import com.komainos.planificacion.service.ServicioPlanificacion;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Cuentas independientes por etapa del mantenimiento, con el doble del adaptador Ansible (RF05, RF06, RF38, RF52, RF55)")
class CuentasPorEtapaIT extends BaseAutomatizacionIT {

    @Autowired
    ServicioAsignacionCuentas asignaciones;
    @Autowired
    ServicioConectividad conectividad;
    @Autowired
    EvaluacionRepositorio evaluaciones;
    @Autowired
    ServicioPlanificacion planificacion;

    @ParameterizedTest(name = "La orden conserva A para evaluar y B para mantener; cuentas heredadas: {0}")
    @ValueSource(booleans = {false, true})
    void conservaCuentasDuranteTodoElCiclo(boolean heredadas) {
        Integer cuentaEvaluacion = nuevaCuenta("Evaluación inicial", "svc_lectura");
        Integer cuentaMantenimiento = nuevaCuenta("Mantenimiento inicial", "svc_mantenimiento");
        Integer nuevaEvaluacion = nuevaCuenta("Evaluación siguiente", "svc_lectura_nueva");
        Integer nuevoMantenimiento = nuevaCuenta("Mantenimiento siguiente", "svc_mantenimiento_nuevo");

        asignaciones.definirPredeterminada(cuentaEvaluacion, FinalidadCuenta.EVALUACION, actor);
        asignaciones.definirPredeterminada(cuentaMantenimiento, FinalidadCuenta.MANTENIMIENTO, actor);
        configurar(servidor.getId(), heredadas ? null : cuentaEvaluacion, heredadas ? null : cuentaMantenimiento);
        Integer idOrden = programacion.inmediata(servidor.getId(), null, false,
                "Separación de cuentas por etapa", null, actor).getId();
        assertThat(estado(idOrden)).isEqualTo(EstadoOrden.PROGRAMADA);
        comprobarCuentasDeOrden(idOrden, cuentaEvaluacion, cuentaMantenimiento);

        // Una orden conserva sus cuentas aunque cambien la configuración y ambas predeterminadas antes de evaluarla.
        configurar(servidor.getId(), nuevaEvaluacion, nuevoMantenimiento);
        asignaciones.definirPredeterminada(nuevaEvaluacion, FinalidadCuenta.EVALUACION, actor);
        asignaciones.definirPredeterminada(nuevoMantenimiento, FinalidadCuenta.MANTENIMIENTO, actor);
        comprobarCuentasDeOrden(idOrden, cuentaEvaluacion, cuentaMantenimiento);
        clearInvocations(transporte);

        evaluacion.evaluar(idOrden, actor);
        assertThat(estado(idOrden)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
        assertThat(evaluaciones.deOrden(idOrden, TipoEvaluacion.PREVIA))
                .extracting(Evaluacion::getIdCuentaServicio).containsExactly(cuentaEvaluacion);
        assertThat(jdbc.queryForList("""
                select mt.id_cuenta_servicio from mop_tarea mt
                join mop m using (id_mop) join orden_detalle d using (id_orden_detalle)
                where d.id_orden = ? order by mt.orden_ejecucion
                """, Integer.class, idOrden)).containsExactly(cuentaMantenimiento, cuentaMantenimiento);
        verify(transporte).ejecutar(argThat(s -> s.proposito() == PropositoEjecucion.ANALISIS_PREVIO
                && cuentaEvaluacion.equals(s.idCuentaServicio())));

        aprobarComoResponsable(idOrden);
        assertThat(orquestador.drenar()).isEqualTo(1);
        assertThat(estado(idOrden)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        assertThat(evaluaciones.deOrden(idOrden, TipoEvaluacion.POSTERIOR))
                .extracting(Evaluacion::getIdCuentaServicio).containsExactly(cuentaEvaluacion);
        comprobarCuentasDeOrden(idOrden, cuentaEvaluacion, cuentaMantenimiento);
        verify(transporte).probarConexion(argThat(s -> s.proposito() == PropositoEjecucion.CONECTIVIDAD
                && cuentaMantenimiento.equals(s.idCuentaServicio())));
        verify(transporte, times(2)).ejecutar(argThat(s -> s.proposito() == PropositoEjecucion.TAREA
                && cuentaMantenimiento.equals(s.idCuentaServicio())));
        verify(transporte).ejecutar(argThat(s -> s.proposito() == PropositoEjecucion.ANALISIS_POSTERIOR
                && cuentaEvaluacion.equals(s.idCuentaServicio())));
    }

    @Test
    @DisplayName("RF71: la evaluación admite acceso sin administrador y el mantenimiento informa la falta de permisos")
    void accesoExigePermisosSegunFinalidad() {
        var sinPermisos = crear("srv-sin-permisos", "10.90.0.2");
        Integer cuentaEvaluacion = nuevaCuenta("Evaluación sin privilegios", "svc_lectura");
        Integer cuentaMantenimiento = nuevaCuenta("Mantenimiento sin privilegios", "svc_mantenimiento");
        configurar(sinPermisos.getId(), cuentaEvaluacion, cuentaMantenimiento);

        var accesoEvaluacion = conectividad.probarServidor(sinPermisos.getId(), null, FinalidadCuenta.EVALUACION, actor);
        assertThat(accesoEvaluacion.idCuenta()).isEqualTo(cuentaEvaluacion);
        assertThat(accesoEvaluacion.correcto()).isTrue();
        assertThat(accesoEvaluacion.fallo()).isNull();

        var accesoMantenimiento = conectividad.probarServidor(sinPermisos.getId(), null, FinalidadCuenta.MANTENIMIENTO, actor);
        assertThat(accesoMantenimiento.idCuenta()).isEqualTo(cuentaMantenimiento);
        assertThat(accesoMantenimiento.correcto()).isFalse();
        assertThat(accesoMantenimiento.fallo()).isEqualTo(TipoFallo.PERMISOS);
    }

    @Test
    @DisplayName("RF05/RF38: una orden sin cuentas las completa al evaluar y conserva las que quedaron fijadas")
    void completaCuentasPendientesAntesDeEvaluar() {
        configurar(servidor.getId(), null, null);
        Integer idOrden = planificacion.programar(servidor.getId(), null, AHORA.plusSeconds(3600),
                "Programada antes de disponer de las cuentas", actor).getId();
        comprobarCuentasDeOrden(idOrden, null, null);

        Integer cuentaEvaluacion = nuevaCuenta("Evaluación disponible", "svc_lectura");
        Integer cuentaMantenimiento = nuevaCuenta("Mantenimiento disponible", "svc_mantenimiento");
        configurar(servidor.getId(), cuentaEvaluacion, null);
        asignaciones.definirPredeterminada(cuentaMantenimiento, FinalidadCuenta.MANTENIMIENTO, actor);

        evaluacion.evaluar(idOrden, actor);
        assertThat(estado(idOrden)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
        comprobarCuentasDeOrden(idOrden, cuentaEvaluacion, cuentaMantenimiento);
        assertThat(jdbc.queryForObject("select id_cuenta_servicio_evaluacion from orden where id_orden = ?",
                Integer.class, idOrden)).isEqualTo(cuentaEvaluacion);
        assertThat(jdbc.queryForObject("select id_cuenta_servicio_mantenimiento from orden where id_orden = ?",
                Integer.class, idOrden)).isEqualTo(cuentaMantenimiento);

        configurar(servidor.getId(), cuenta, cuenta);
        asignaciones.definirPredeterminada(cuenta, FinalidadCuenta.EVALUACION, actor);
        asignaciones.definirPredeterminada(cuenta, FinalidadCuenta.MANTENIMIENTO, actor);
        comprobarCuentasDeOrden(idOrden, cuentaEvaluacion, cuentaMantenimiento);
        assertThat(evaluaciones.deOrden(idOrden, TipoEvaluacion.PREVIA))
                .extracting(Evaluacion::getIdCuentaServicio).containsExactly(cuentaEvaluacion);
    }

    @Test
    @DisplayName("RF38: sin cuenta de mantenimiento la evaluación conserva PROGRAMADA sin fijar cuentas parciales")
    void faltaMantenimientoNoGuardaUnaCuentaParcial() {
        configurar(servidor.getId(), null, null);
        Integer idOrden = planificacion.programar(servidor.getId(), null, AHORA.plusSeconds(3600),
                "Programada sin cuentas disponibles", actor).getId();
        Integer cuentaEvaluacion = nuevaCuenta("Solo evaluación disponible", "svc_lectura");
        configurar(servidor.getId(), cuentaEvaluacion, null);
        clearInvocations(transporte);

        assertThatThrownBy(() -> evaluacion.evaluar(idOrden, actor))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mantenimiento");

        assertThat(estado(idOrden)).isEqualTo(EstadoOrden.PROGRAMADA);
        comprobarCuentasDeOrden(idOrden, null, null);
        assertThat(evaluaciones.deOrden(idOrden, TipoEvaluacion.PREVIA)).isEmpty();
        verify(transporte, never()).ejecutar(any());
        verify(transporte, never()).probarConexion(any());
    }

    private Integer nuevaCuenta(String nombre, String usuario) {
        return credenciales.registrarCuenta(new ServicioCredenciales.DatosCredencial(nombre, usuario, null,
                new ServicioCredenciales.Secreto(TipoAutenticacion.PASSWORD, null, "secreto-solo-para-pruebas", null)), actor).getId();
    }

    private void configurar(Integer idServidor, Integer idEvaluacion, Integer idMantenimiento) {
        servidores.configurar(idServidor, new ServicioServidor.DatosConfiguracion(7, 30,
                ModalidadPlanificacion.BAJO_DEMANDA, idEvaluacion, idMantenimiento), actor);
    }

    private void comprobarCuentasDeOrden(Integer idOrden, Integer idEvaluacion, Integer idMantenimiento) {
        var orden = consulta.obtener(idOrden, new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR));
        assertThat(orden.getIdCuentaServicioEvaluacion()).isEqualTo(idEvaluacion);
        assertThat(orden.getIdCuentaServicioMantenimiento()).isEqualTo(idMantenimiento);
    }
}
