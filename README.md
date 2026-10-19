# Prueba técnica IAS — PagoClaro

El proyecto implementa una API Java para conciliar pagos: corrige estados y débitos duplicados, evita duplicados al reintentar y deja los casos contradictorios para revisión manual.

## Organización

```text
back/                    Backend Java y Spring Boot
  src/main/              Código y configuración
  src/test/              Pruebas automatizadas
  db/oracle/             Scripts SQL y PL/SQL
  scripts/               Comandos de desarrollo
  requests.http          Ejemplos de llamadas a la API
  pom.xml                Dependencias y compilación
  README.md              Guía técnica del backend

front/                   Espacio para la interfaz web (pendiente)

docs/                    Documentación de la prueba
  Prueba_Tecnica_...pdf   Enunciado original
  ANALISIS_PRUEBA.md     Requisitos y análisis inicial
  DECISIONES.md         Diagnóstico y decisiones técnicas
  OPERACION_N3.md       Guía de soporte
  VALIDACION_LOCAL.md   Resultados de las pruebas locales
  VALIDACION_ORACLE.md  Verificaciones pendientes en Oracle
  USO_IA.md             Trazabilidad del uso de IA
  evidencia/            Respuestas HTTP verificadas
```

## Iniciar el backend

Desde la raíz del proyecto, en este equipo:

```powershell
powershell -ExecutionPolicy Bypass -File .\back\scripts\dev.ps1 run
```

Para ejecutar las pruebas:

```powershell
powershell -ExecutionPolicy Bypass -File .\back\scripts\dev.ps1 verify
```

La API inicia en `http://127.0.0.1:8080`. No hay una página web implementada todavía. Los ejemplos de consumo están en [back/requests.http](back/requests.http).

El backend tiene 23 pruebas automatizadas. Los scripts Oracle están entregados; su ejecución en un servidor Oracle sigue pendiente.

Detalles de ejecución y funcionamiento: [guía del backend](back/README.md). Evidencia de validación: [resultados locales](docs/VALIDACION_LOCAL.md).
