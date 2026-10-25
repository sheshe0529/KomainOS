package com.komainos.credencial.service;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.repository.CuentaServicioRepositorio;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor
public class AdaptadorConexionAutomatizacion implements PuertoConexionAutomatizacion {
 private final CuentaServicioRepositorio cuentas; private final CifradorCredenciales cifrador; private final AdaptadorCuentasServicio validacion;
 @Transactional(readOnly=true) public Conexion resolver(Integer id, FamiliaSistemaOperativo familia){
  validacion.exigirAsignable(id,familia);var c=cuentas.findConSecretoById(id).orElseThrow();var s=c.secretoVigente().orElseThrow(()->new ReglaNegocioException("La cuenta no tiene secreto vigente"));
  return new Conexion(c.getUsuarioAcceso(),cifrador.descifrar(s.secreto(),id),s.secretoSu().map(x->cifrador.descifrarSu(x,id)).orElse(null),s.getTipoAutenticacion()==TipoAutenticacion.LLAVE_SSH);
 }
}
