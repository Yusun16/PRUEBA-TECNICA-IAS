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

front/                   Consola web de soporte
  src/                   Interfaz y cliente de la API
  tests/                 Pruebas del cliente, proxy y navegador
  scripts/               Iniciador de la aplicación completa
  server.mjs             Servidor web y proxy hacia Java
  README.md              Guía del frontend

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

## Abrir la aplicación completa

Desde la raíz, en este equipo con Node instalado y las herramientas Java portátiles existentes:

```powershell
powershell -ExecutionPolicy Bypass -File .\front\scripts\start-local.ps1
```

Abre **http://127.0.0.1:5173**, pulsa **Configurar acceso → Usar demo → Guardar acceso** y concilia una referencia. El comando inicia frontend y backend; Ctrl+C detiene ambos. Usa `-Build` para recompilar y probar Java antes de iniciar. Los puertos 8080 y 5173 deben estar libres.

La interfaz permite seleccionar casos de ejemplo, ejecutar conciliaciones, comparar el débito neto anterior/final y revisar la actividad de esta ventana. Los resultados provienen de la API real. La revisión manual se diferencia de una corrección aplicada.

## Iniciar componentes por separado

Desde la raíz del proyecto, en este equipo:

```powershell
powershell -ExecutionPolicy Bypass -File .\back\scripts\dev.ps1 run
```

Para ejecutar las pruebas:

```powershell
powershell -ExecutionPolicy Bypass -File .\back\scripts\dev.ps1 verify
```

En otra terminal:

```powershell
cd front
npm start
```

La interfaz inicia en `http://127.0.0.1:5173` y la API en `http://127.0.0.1:8080`. Los ejemplos HTTP están en [back/requests.http](back/requests.http).

El backend tiene 23 pruebas automatizadas; el frontend cuenta con 13 pruebas del cliente/proxy y 10 pruebas de navegador. Los scripts Oracle están entregados; su ejecución en un servidor Oracle sigue pendiente.

Detalles: [backend](back/README.md), [frontend](front/README.md), [validación Java](docs/VALIDACION_LOCAL.md) y [validación de la interfaz](docs/FRONTEND.md).
