package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.PuertoCuentasServicio.ResumenCuenta;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.ActividadEjecucionRepositorio;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Textos de la actividad de las ejecuciones (DEC-59)")
class ServicioActividadTest {

    private final PuertoCuentasServicio cuentas = mock(PuertoCuentasServicio.class);
    private final ServicioActividad servicio = new ServicioActividad(mock(ActividadEjecucionRepositorio.class),
            mock(EvaluacionRepositorio.class), mock(OrdenDetalleRepositorio.class), mock(MopRepositorio.class), cuentas,
            new PropiedadesAutomatizacion(false, 30000, 10, new PropiedadesAutomatizacion.Ansible("ansible-playbook", 30,
                    new PropiedadesAutomatizacion.Ssh(22, true, null),
                    new PropiedadesAutomatizacion.Winrm(5986, "https", "ntlm", true))),
            Clock.systemUTC(), mock(PlatformTransactionManager.class));

    @Test
    @DisplayName("La conexión dice servidor, transporte, cuenta, usuario, autenticación y elevación, sin secretos")
    void conexion() {
        when(cuentas.resumen(1)).thenReturn(Optional.of(
                new ResumenCuenta("Cuenta genérica", "komainos-sudo", "Llave SSH", "sudo", true)));
        when(cuentas.resumen(2)).thenReturn(Optional.of(
                new ResumenCuenta("Cuenta Windows", "svc_komainos", "Contraseña", "Ninguno", false)));

        assertThat(servicio.conexion(SolicitudEjecucion.conectividad(1, "SERVLNXV01", "172.31.37.104",
                FamiliaSistemaOperativo.LINUX, 1, 2))).isEqualTo("SERVLNXV01 (172.31.37.104) por SSH (puerto 22) "
                + "· cuenta «Cuenta genérica»: usuario komainos-sudo con llave SSH, elevación sudo con contraseña");
        assertThat(servicio.conexion(SolicitudEjecucion.conectividad(2, "SERVWINV01", "172.31.39.120",
                FamiliaSistemaOperativo.WINDOWS, 2, 2))).isEqualTo("SERVWINV01 (172.31.39.120) por WinRM (HTTPS, "
                + "puerto 5986) · cuenta «Cuenta Windows»: usuario svc_komainos con contraseña, sin elevación");
        assertThat(servicio.conexion(SolicitudEjecucion.conectividad(3, "SERVLNXV02", "10.0.0.2",
                FamiliaSistemaOperativo.LINUX, null, 2))).endsWith("· sin cuenta de servicio");
    }

    @Test
    @DisplayName("Si la cuenta no se puede leer, la conexión se informa igual y la ejecución no se interrumpe")
    void cuentaIlegible() {
        when(cuentas.resumen(1)).thenThrow(new IllegalStateException("sin conexión con la base"));

        assertThat(servicio.conexion(SolicitudEjecucion.conectividad(1, "SERVLNXV01", "172.31.37.104",
                FamiliaSistemaOperativo.LINUX, 1, 2))).isEqualTo("SERVLNXV01 (172.31.37.104) por SSH (puerto 22)");
    }

    @Test
    @DisplayName("El participio concuerda con el análisis: «Análisis posterior completado», «Evaluación previa completada»")
    void concordancia() {
        assertThat(ServicioActividad.operacion(TipoEvaluacion.PREVIA, "completada")).isEqualTo("Evaluación previa completada");
        assertThat(ServicioActividad.operacion(TipoEvaluacion.POSTERIOR, "completada")).isEqualTo("Análisis posterior completado");
        assertThat(ServicioActividad.operacion(TipoEvaluacion.POSTERIOR, "interrumpida")).isEqualTo("Análisis posterior interrumpido");
        assertThat(ServicioActividad.operacion(TipoEvaluacion.POSTERIOR, "ha fallado")).isEqualTo("Análisis posterior ha fallado");
        assertThat(ServicioActividad.operacion(TipoEvaluacion.REVISION_PERIODICA, "en curso")).isEqualTo("Revisión periódica en curso");
    }

    @Test
    @DisplayName("Solo un fallo posterior a la conexión implica que el acceso funcionó")
    void llegoAEjecutarse() {
        assertThat(ServicioActividad.llegoAEjecutarse(TipoFallo.EJECUCION.describir("código 1"))).isTrue();
        assertThat(ServicioActividad.llegoAEjecutarse(TipoFallo.CONTRATO.describir("sin métricas"))).isTrue();
        assertThat(ServicioActividad.llegoAEjecutarse(TipoFallo.TIEMPO_EXCEDIDO.describir(null))).isTrue();
        assertThat(ServicioActividad.llegoAEjecutarse(TipoFallo.AUTENTICACION.describir("clave rechazada"))).isFalse();
        assertThat(ServicioActividad.llegoAEjecutarse(TipoFallo.PERMISOS.describir("sudo"))).isFalse();
        assertThat(ServicioActividad.llegoAEjecutarse(null)).isFalse();
    }

    @Test
    @DisplayName("Las duraciones se leen en segundos o en minutos y segundos")
    void duraciones() {
        assertThat(ServicioActividad.texto(Duration.ofMillis(800))).isEqualTo("0 s");
        assertThat(ServicioActividad.texto(Duration.ofSeconds(59))).isEqualTo("59 s");
        assertThat(ServicioActividad.texto(Duration.ofSeconds(125))).isEqualTo("2 min 5 s");
        assertThat(ServicioActividad.texto(null)).isEqualTo("sin duración registrada");
    }
}
