package com.komainos.mantenimiento;
import com.komainos.mantenimiento.service.ServicioCierreMantenimiento;import com.komainos.inventario.model.NivelAlerta;import com.komainos.catalogo.model.NivelAfectacion;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class ReglasAutomatizacionTest{
 @Test void factorDeIncidenciaNoDependeDeRacha(){assertThat(ServicioCierreMantenimiento.resultado(true,true,NivelAlerta.NORMAL,20,5)).isEqualTo("INCIDENCIA");}
 @Test void advertenciasAcortanElCiclo(){assertThat(ServicioCierreMantenimiento.resultado(false,true,NivelAlerta.ADVERTENCIA,20,5)).isEqualTo("SALUD_REGULAR");}
 @Test void rachaExigeConformidad(){assertThat(ServicioCierreMantenimiento.resultado(false,false,NivelAlerta.NORMAL,20,5)).isEqualTo("ESTADO_NORMAL");assertThat(ServicioCierreMantenimiento.resultado(false,true,NivelAlerta.NORMAL,5,5)).isEqualTo("RACHA_ESTABLE");}
 @Test void afectacionEsLaMayor(){assertThat(NivelAfectacion.SIN_AFECTACION.mayor(NivelAfectacion.TOTAL)).isEqualTo(NivelAfectacion.TOTAL);}
}
