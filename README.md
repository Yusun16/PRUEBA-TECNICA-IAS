# Prueba técnica IAS — PagoClaro

El proyecto implementa una API Java para conciliar pagos: corrige estados con evidencia suficiente, conserva el historial y evita efectos duplicados al reintentar. La compensación de débitos repetidos se demuestra únicamente en simulación controlada; en operación segura exige revisión manual.

**LISTO PARA PUBLICAR EN GITHUB:** [preparación Git y reproducción desde clon](docs/PREPARACION_GIT_ENTREGA.md). La [validación integral](docs/VALIDACION_FINAL_ENTREGA.md) documenta arquitectura y cumplimiento del enunciado. Desde un clon independiente pasaron **53 pruebas Java/H2, 17 de cliente/proxy y 14 de navegador**, además de ocho comandos HTTP y ocho conciliaciones de navegador contra Java real. Oracle sigue pendiente. Se conserva el historial original y la rama master; no se ha publicado en GitHub.

## Ruta rápida para el evaluador

Requisitos: Java 21, Node 22+ y acceso a Maven Central para la primera compilación. Configurar JAVA_HOME al JDK 21; no se requiere instalar Oracle para la demostración local. Tras obtener la versión completa de la entrega:

Tecnologías: Spring Boot 3.5.16, JDBC, Spring Security Basic, H2 local y scripts Oracle 19c+. El flujo separa API, servicio transaccional, motor de reglas y repositorio JDBC. El frontend usa JavaScript nativo y un proxy Node sin dependencias de producción.

```powershell
# Desde back/: compilar y ejecutar pruebas
.\mvnw.cmd clean verify
```

Desde la raíz, con 8080 y 5173 libres:

```powershell
powershell -ExecutionPolicy Bypass -File .\front\scripts\start-local.ps1
```

Abrir http://127.0.0.1:5173/login. Credenciales **ficticias y exclusivas del laboratorio**: usuario `n3`, contraseña `demo-change-me`. No usarlas en un despliegue real; el backend las rechaza fuera del perfil local.

| Referencia | Resultado esperado |
|---|---|
| PAY-AX91 | Confirma estado conservando el débito; repetición sin cambios. |
| PAY-BX17 | En local ficticio reversa exclusivamente 5003; con política segura devuelve revisión manual sin compensar. |
| PAY-CQ22 | Revisión manual por evidencia contradictoria; auditoría idempotente. |
| PAY-DK44 | Ya consistente; ninguna escritura. |

La política segura se activa con `--reconciliation.allow-duplicate-compensation=false` al iniciar Java. APPROVED sin ledger siempre exige revisión manual. La demostración BX17 no acredita capturas financieras externas.

Endpoints autenticados con rol SUPPORT: `GET /api/support/session` valida acceso sin modificar pagos; `POST /api/support/payments/{reference}/reconcile` concilia una referencia sin cuerpo. Detalle de respuestas y errores: [backend](back/README.md).

Detener Java descarta el estado H2 en memoria; el siguiente proceso restaura los datos iniciales. Si ya existe un JAR de otra revisión, usar `start-local.ps1 -Build` con ambos puertos libres. No ejecutar `clean` en el directorio habitual de una aplicación abierta; la validación aislada está documentada en el informe final.

Para pruebas frontend, desde front/: `npm ci`, `npm test` y `npm run test:e2e` (Chrome/Edge instalado o Chromium configurado). La integración sin mocks se ejecuta con `node scripts/verify-integration.mjs <ruta-al-JAR>`; usa puertos propios. SQL/PLSQL y procedimientos de validación están en back/db/oracle/ y [guía Oracle](docs/ORACLE_INSTALACION_Y_RECUPERACION.md). H2 no certifica PL/SQL. El [uso de IA](docs/USO_IA.md) incluye contribuciones, correcciones y límites.

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

La validación integral dejó **53 pruebas Java/H2 aprobadas**, una suite Oracle omitida por falta de conexión y **31 pruebas del frontend aprobadas**, sin fallos. Consultar [informe vigente](docs/VALIDACION_FINAL_ENTREGA.md) y [evidencia](docs/evidencia/entrega-pruebas.json). La ejecución real en Oracle sigue pendiente.

APPROVED sin ledger requiere revisión manual y no crea débitos. Activar compensaciones fuera de la simulación local está bloqueado; varios APPROVED no demuestran un único cobro. H2 y los scripts Oracle incluyen guardas de inmutabilidad y de escritura por pago. La validación Oracle real está pendiente.

Informe completo: [validación final de entrega](docs/VALIDACION_FINAL_ENTREGA.md). Modelo: [base de datos](docs/MODELO_DATOS.md). Instalación y recuperación: [Oracle 19c+](docs/ORACLE_INSTALACION_Y_RECUPERACION.md).

Detalles: [backend](back/README.md), [frontend](front/README.md), [validación actual](docs/AUDITORIA_FINAL.md) y [validación de la interfaz](docs/FRONTEND.md).
