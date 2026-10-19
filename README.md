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

Abre **http://127.0.0.1:5173/login**, introduce tus credenciales y pulsa **Iniciar sesión**. Java valida el acceso antes de mostrar el dashboard. Introduce allí la referencia que necesitas conciliar. **Cerrar sesión** borra el acceso y los datos locales de esta ventana. El comando inicia frontend y backend; Ctrl+C detiene ambos. Usa `-Build` para recompilar y probar Java antes de iniciar. Los puertos 8080 y 5173 deben estar libres.

La interfaz permite ejecutar conciliaciones, comparar el débito neto anterior/final y revisar la actividad de esta ventana. No publica referencias de ejemplo ni credenciales. Los resultados provienen de la API real. La revisión manual se diferencia de una corrección aplicada.

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

La auditoría dejó **48 pruebas Java/H2 aprobadas**, una suite Oracle omitida por falta de conexión, y **31 pruebas del frontend aprobadas**. Los scripts Oracle están preparados; su ejecución en un servidor Oracle sigue pendiente.

Fuera del perfil local, compensar débitos repetidos está deshabilitado por defecto: varios APPROVED no demuestran un único cobro. H2 y los scripts Oracle incluyen guardas de inmutabilidad y de escritura por pago. La validación Oracle real está pendiente.

Informe completo: [auditoría final](docs/AUDITORIA_FINAL.md). Modelo: [base de datos](docs/MODELO_DATOS.md). Instalación y recuperación: [Oracle 19c+](docs/ORACLE_INSTALACION_Y_RECUPERACION.md).

Detalles: [backend](back/README.md), [frontend](front/README.md), [validación actual](docs/AUDITORIA_FINAL.md) y [validación de la interfaz](docs/FRONTEND.md).
