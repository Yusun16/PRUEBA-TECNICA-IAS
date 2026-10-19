# Frontend — decisiones y validación

## Alcance implementado

Consola de soporte para operar la API de conciliación existente. El enunciado original no exige frontend; se incorporó a solicitud del usuario manteniendo las carpetas back/front/docs.

La interfaz tiene formulario, configuración de acceso, selección de casos demo, resultados, importes anterior/final, auditoría creada y actividad de sesión. No consulta un listado de pagos inexistente en la API, no simula saldos y no muestra contadores globales inventados. La actividad solo corresponde a esta ventana y se limpia al recargar.

## Decisiones

Se eligieron módulos JavaScript nativos y CSS sin framework para una interfaz acotada a un comando y dos vistas locales. Evita una cadena de compilación innecesaria; cliente HTTP, presentación, interacciones y servidor tienen responsabilidades separadas. Las únicas dependencias de desarrollo son Playwright y Prettier. El runtime usa Node estándar.

El proxy local reenvía exclusivamente el POST autorizado por ruta, evita CORS y conserva la separación de proyectos. No reenvía cookies, cuerpos ni cabeceras arbitrarias; no sigue redirecciones del backend ni expone cuerpos de error internos. Solo entrega una lista explícita de archivos públicos. Aplica CSP, nosniff, no-referrer y no-store. Rechaza Origin de otro sitio en comandos. Las credenciales se mantienen en memoria y el DOM se actualiza con textContent, no con HTML proveniente del servidor.

El frontend nunca calcula ni corrige saldos por su cuenta. Mantiene el token decimal original del JSON para no redondear NUMBER(18,2) a través de un double JavaScript. El formato usa dos decimales sin símbolo de moneda, dado que la base no define una moneda. Java continúa siendo la autoridad sobre contabilidad e idempotencia.

El POST puede modificar datos; la interfaz lo indica antes de ejecutar. Guardar credenciales, navegar por actividad o seleccionar un caso no envía el POST. Durante una solicitud se bloquea el envío repetido. No hay reintentos automáticos; un timeout informa que se debe verificar y reintentar la misma referencia.

## Verificaciones realizadas

Fecha: 19 de octubre de 2026, America/Bogota.

| Verificación | Resultado |
|---|---|
| `npm test` | 13 pruebas aprobadas, sin fallos. |
| `npm run test:e2e` | 10 pruebas aprobadas en Chrome real. |
| `start-local.ps1 -Check` | Frontend HTTP 200; backend HTTP 401 sin credenciales; ambos procesos cerrados. |
| Integración Chrome → Node → Spring Boot | Seis comandos reales verificados sobre H2 recién iniciado. |
| Escritorio y móvil | Capturas revisadas; móvil de 390 px sin desbordamiento horizontal. |

Las pruebas de cliente/proxy incluyen contrato y autenticación, entradas inválidas sin solicitudes, errores sanitizados, timeout sin reintentos, archivos privados inaccesibles, restricción de métodos/rutas/origen, precisión monetaria y hora de Bogotá.

Las pruebas de navegador incluyen estado inicial, guardar credenciales sin ejecutar, ausencia de secretos en almacenamiento web, corrección/repetición, revisión manual, selección desde historial, validación, bloqueo de doble envío, indicación de resultado de otra referencia y errores 401/404/503. También comprueban ausencia de errores JavaScript y recursos en el estado inicial.

La integración real comprobó, en orden:

1. BX17: RESOLVED, un reverso, neto 178000 → 89000 y nueva auditoría.
2. BX17 repetido: ALREADY_CONSISTENT, cero movimientos y ninguna auditoría adicional.
3. AX91: RESOLVED, cambio de estado sin movimientos.
4. CQ22: MANUAL_REVIEW, cero movimientos y una auditoría.
5. CQ22 repetido: MANUAL_REVIEW, sin auditoría nueva.
6. DK44: ALREADY_CONSISTENT, sin escrituras.

Se verificaron seis filas de actividad y la vista móvil contra el mismo backend. Respuestas sin cabeceras ni credenciales: [evidencia/frontend-integracion.json](evidencia/frontend-integracion.json).

Capturas: [escritorio](evidencia/frontend-escritorio.png), [móvil](evidencia/frontend-movil.png) y [actividad](evidencia/frontend-actividad.png).

## Límites

La validación e2e automatizada usa respuestas controladas; se complementó con la integración real descrita. No se certifica compatibilidad con todos los navegadores ni accesibilidad WCAG mediante una auditoría formal. La lista local no reemplaza SUPPORT_AUDIT y no agrega endpoints de lectura a la API. La ejecución de scripts Oracle sigue pendiente del servidor correspondiente.

El despliegue remoto, TLS y autenticación empresarial quedan fuera de esta demostración local. No se publicó el proyecto mediante un servicio externo.
