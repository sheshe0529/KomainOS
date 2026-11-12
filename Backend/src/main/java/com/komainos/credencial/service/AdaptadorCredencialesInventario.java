package com.komainos.credencial.service;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales.AccesoExportado;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.inventario.service.PuertoCredencialesInventario;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Traduce las celdas del archivo del inventario a la credencial principal y viceversa (RF12, RF13, DEC-39) */
@Component
@RequiredArgsConstructor
public class AdaptadorCredencialesInventario implements PuertoCredencialesInventario {

    private static final Pattern LLAVE = Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----");

    private final ServicioCredenciales credenciales;

    @Override 
    public Map<Integer, CredencialArchivo> principales(Collection<Integer> idsServidores) {
        Map<Integer, CredencialArchivo> resultado = new HashMap<>();
        credenciales.principales(idsServidores).forEach((id, acceso) -> resultado.put(id, archivo(acceso)));
        return resultado;
    }

    @Override
    public List<String> validar(CredencialArchivo credencial, FamiliaSistemaOperativo familia) {
        List<String> errores = new ArrayList<>();
        try {
            errores.addAll(credenciales.validarImportada(interpretar(credencial, familia), credencial.usuario(), familia));
        } catch (ReglaNegocioException ex) {
            errores.add(ex.getMessage());
        }
        return errores.stream().map(e -> "Credencial: " + e).toList();
    }

    @Override
    public boolean cambiaPrincipal(Integer idServidor, FamiliaSistemaOperativo familia, CredencialArchivo credencial) {
        return credenciales.cambiaPrincipal(idServidor, credencial.usuario(), interpretar(credencial, familia));
    }

    @Override
    public void aplicarPrincipal(Integer idServidor, FamiliaSistemaOperativo familia, CredencialArchivo credencial,
                                 Actor actor) {
        credenciales.aplicarPrincipalImportada(idServidor, credencial.usuario(), interpretar(credencial, familia), actor);
    }

    /**
     * Las celdas vacías se deducen: una llave PEM es llave SSH, y sin método una contraseña de elevación indica su (como
     * el antiguo usuario Genérico). Con sudo, la contraseña de elevación decide si sudo la pide
     */
    static Secreto interpretar(CredencialArchivo c, FamiliaSistemaOperativo familia) {
        TipoAutenticacion tipo = mecanismo(c.mecanismo(), c.secreto());
        MetodoElevacion metodo = metodo(c.metodoElevacion(), c.secretoElevacion(), c.usuario());
        return new Secreto(tipo, metodo, c.secreto(), c.secretoElevacion(), null);
    }

    private static TipoAutenticacion mecanismo(String texto, String secreto) {
        if (texto == null) {
            return secreto != null && LLAVE.matcher(secreto).find() ? TipoAutenticacion.LLAVE_SSH : TipoAutenticacion.PASSWORD;
        }
        String clave = clave(texto);
        if (clave.equals("contrasena") || clave.equals("password") || clave.equals("clave")) {
            return TipoAutenticacion.PASSWORD;
        }
        if (clave.contains("llave") || clave.contains("ssh")) {
            return TipoAutenticacion.LLAVE_SSH;
        }
        throw new ReglaNegocioException("El mecanismo «%s» no es válido: use Contraseña o Llave SSH".formatted(texto));
    }

    /**
     * Los valores anteriores al DEC-57 se traducen como lo hizo su migración: Genérico es su y Administrador es sudo
     * sin contraseña, salvo que el usuario sea root
     */
    private static MetodoElevacion metodo(String texto, String elevacion, String usuario) {
        if (texto == null) {
            return elevacion != null && !elevacion.isBlank() ? MetodoElevacion.SU : MetodoElevacion.NINGUNO;
        }
        return switch (clave(texto)) {
            case "ninguno", "ninguna" -> MetodoElevacion.NINGUNO;
            case "sudo" -> MetodoElevacion.SUDO;
            case "su" -> MetodoElevacion.SU;
            case "runas" -> MetodoElevacion.RUNAS;
            case "generico" -> MetodoElevacion.SU;
            case "administrador", "admin" -> usuario != null && usuario.trim().equalsIgnoreCase("root")
                    ? MetodoElevacion.NINGUNO : MetodoElevacion.SUDO;
            default -> throw new ReglaNegocioException(
                    "El método de elevación «%s» no es válido: use Ninguno, sudo, su o runas".formatted(texto));
        };
    }

    private static CredencialArchivo archivo(AccesoExportado a) {
        return new CredencialArchivo(a.tipo().etiqueta(), a.metodo().etiqueta(), a.usuario(), a.secreto(),
                a.elevacion());
    }

    /** «Contraseña», «CONTRASENA» y «contraseña » valen lo mismo */
    private static String clave(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
