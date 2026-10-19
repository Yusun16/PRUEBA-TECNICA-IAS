# Frontend — decisiones y validación

**Auditoría posterior vigente:** [AUDITORIA_FINAL_FRONTEND.md](AUDITORIA_FINAL_FRONTEND.md), con 21 pruebas unitarias/proxy, 28 de navegador y ocho conciliaciones reales aprobadas. Incluye correcciones de vacío, contraste, teclado y validación del contrato. Los resultados inferiores de este documento son históricos; no representan una certificación HTTPS/WCAG completa.

Validación vigente de entrega: [VALIDACION_FINAL_ENTREGA.md](VALIDACION_FINAL_ENTREGA.md). Se repitieron 17 pruebas de cliente/proxy y 14 de navegador; además `front/scripts/verify-integration.mjs` comprobó ocho conciliaciones en Chrome con backend Java real y política segura. [Evidencia actual](evidencia/entrega-integracion-real.json). Los resultados de seis comandos y capturas descritos más abajo corresponden a ejecuciones anteriores.

## Alcance implementado

Consola de soporte para operar la API de conciliación existente. El enunciado original no exige frontend; se incorporó a solicitud del usuario manteniendo las carpetas back/front/docs.

La interfaz tiene formulario, configuración de acceso, resultados, importes anterior/final, auditoría creada y actividad de sesión. La referencia se introduce manualmente; no se publican referencias ni credenciales demo en los recursos web. No consulta un listado de pagos inexistente en la API, no simula saldos y no muestra contadores globales inventados. La actividad solo corresponde a esta ventana y se limpia al recargar.

## Decisiones

Se eligieron módulos JavaScript nativos y CSS sin framework para una interfaz acotada a un comando y dos vistas locales. Evita una cadena de compilación innecesaria; cliente HTTP, presentación, interacciones y servidor tienen responsabilidades separadas. Las únicas dependencias de desarrollo son Playwright y Prettier. El runtime usa Node estándar.

El proxy local reenvía exclusivamente el POST autorizado por ruta, evita CORS y conserva la separación de proyectos. No reenvía cookies, cuerpos ni cabeceras arbitrarias; no sigue redirecciones del backend ni expone cuerpos de error internos. Solo entrega una lista explícita de archivos públicos. Aplica CSP, nosniff, no-referrer y no-store. Rechaza Origin de otro sitio en comandos. Las credenciales se mantienen en memoria y el DOM se actualiza con textContent, no con HTML proveniente del servidor.

El frontend nunca calcula ni corrige saldos por su cuenta. Mantiene el token decimal original del JSON para no redondear NUMBER(18,2) a través de un double JavaScript. El formato usa dos decimales sin símbolo de moneda, dado que la base no define una moneda. Java continúa siendo la autoridad sobre contabilidad e idempotencia.

El POST puede modificar datos; la interfaz lo indica antes de ejecutar. Guardar credenciales, navegar por actividad o introducir una referencia no envía el POST. Durante una solicitud se bloquea el envío repetido. No hay reintentos automáticos; un timeout informa que se debe verificar y reintentar la misma referencia.

## Verificaciones realizadas

Fecha: 19 de octubre de 2026, America/Bogota.

| Verificación | Resultado |
|---|---|
| `npm test` | 17 pruebas aprobadas, sin fallos. |
| `npm run test:e2e` | 14 pruebas aprobadas en Chrome real. |
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

## Corrección de exposición de ejemplos

La primera interfaz incluía referencias y etiquetas del PDF como atajos estáticos, visibles sin autenticación. No provenían de un listado de la base, pero revelaban identificadores y descripciones innecesarios en la pantalla pública. Se eliminaron los cuatro atajos, la referencia precargada, el usuario precargado y el botón que incorporaba la contraseña demo en JavaScript.

Una prueba verifica que HTML y módulos públicos no contengan las referencias del enunciado ni la contraseña demo. Las pruebas de navegador ahora introducen referencias y credenciales manualmente. Se comprobó sobre los servicios en ejecución que el proxy en 5173 y Java en 8080 responden HTTP 401 al POST sin credenciales. Esto verifica ese control de acceso; no constituye una auditoría completa de seguridad.

Los datos del PDF permanecen en documentación y fixtures de laboratorio, que el servidor web no expone. Las capturas de integración anteriores documentan la versión inicial; [la pantalla actual](evidencia/frontend-acceso-actual.png) muestra el formulario vacío sin atajos de pago.

## Login validado y entrada al dashboard

La pantalla inicial ahora es `/login`; el dashboard está oculto en el HTML inicial y solo se muestra después de verificar credenciales y rol con Java. La ruta `/dashboard` sin acceso en memoria vuelve a `/login`. El control visual no sustituye la seguridad de la API: tanto el GET de acceso como cada conciliación requieren rol SUPPORT en Spring Security.

La validación de acceso usa un GET independiente y no ejecuta conciliaciones ni consulta pagos. No se implementan sesiones persistentes ni cookies; la autorización Basic se envía explícitamente a Java en cada solicitud. Cerrar sesión o recibir 401/403 de una conciliación limpia credenciales, resultado, referencia, contadores e historial. Recargar requiere autenticarse de nuevo.

Verificación adicional: 25 pruebas Java aprobadas, incluyendo acceso anónimo, contraseña incorrecta, rol no autorizado, identidad válida y ausencia de efectos sobre pagos. En navegador se probaron acceso directo a dashboard, errores de login, entrada válida, logout y recarga. Contra la aplicación real se verificó contraseña incorrecta → error, credenciales válidas → dashboard y logout → login, sin ejecutar comandos de pago.

Capturas actuales: [login escritorio](evidencia/login-escritorio.png), [login móvil](evidencia/login-movil.png) y [dashboard tras autenticación](evidencia/dashboard-autenticado.png). Los servicios locales se reiniciaron con el nuevo backend; H2 cargó nuevamente los datos de muestra.
