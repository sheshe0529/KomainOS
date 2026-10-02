package com.komainos;

import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.SistemaOperativo;
import com.komainos.inventario.model.VentanaMantenimiento;
import com.komainos.inventario.model.VersionSistemaOperativo;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;

import java.time.LocalTime;
import java.util.List;

/** Cuando el modelo gana un atributo obligatorio se toca un solo archivo */
public final class DatosPrueba {

    private DatosPrueba() {
    }

    public static Usuario usuario(int id, String codigo, Rol rol) {
        Usuario u = Usuario.nuevo(codigo, "Usuario " + codigo, "hash", rol);
        u.setId(id);
        return u;
    }

    public static UsuarioAutenticado autenticado(int id, String codigo, Rol rol) {
        return new UsuarioAutenticado(usuario(id, codigo, rol));
    }

    public static Entorno entorno(int id, String nombre) {
        Entorno e = Entorno.nuevo(nombre, null);
        e.setId(id);
        return e;
    }

    public static NivelCriticidad criticidad(int id, String nombre, int prioridad) {
        NivelCriticidad n = NivelCriticidad.nuevo(new DatosNivelCriticidad(nombre, prioridad, 7, 30, 24, 48));
        n.setId(id);
        return n;
    }

    public static VersionSistemaOperativo version(int id, String so, String version) {
        SistemaOperativo sistema = SistemaOperativo.nuevo(so, FamiliaSistemaOperativo.LINUX);
        sistema.setId(id * 100);
        VersionSistemaOperativo v = VersionSistemaOperativo.nueva(sistema, version);
        v.setId(id);
        return v;
    }

    public static Servidor servidor(int id, String hostname, Usuario responsable) {
        Servidor s = Servidor.nuevo();
        s.setId(id);
        s.setHostname(hostname);
        s.reemplazarDirecciones("10.0.0." + id, List.of());
        s.setResponsable(responsable);
        s.setEntorno(entorno(1, "Producción"));
        s.setNivelCriticidad(criticidad(1, "Alta", 1));
        s.setVersionSistemaOperativo(version(1, "Ubuntu", "22.04"));
        return s;
    }

    public static VentanaMantenimiento ventana(DiaSemana diaInicio, String horaInicio, DiaSemana diaFin, String horaFin) {
        return VentanaMantenimiento.nueva(diaInicio, LocalTime.parse(horaInicio), diaFin, LocalTime.parse(horaFin));
    }

    public static Servidor conVentanas(Servidor servidor, VentanaMantenimiento... ventanas) {
        servidor.reemplazarVentanas(List.of(ventanas));
        return servidor;
    }
}
