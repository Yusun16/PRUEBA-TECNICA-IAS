[Volver al ?ndice de documentaci?n](../README.md)

# PagoClaro — auditoría final del frontend

Fecha: 19 de octubre de 2026, America/Bogota. Versión de referencia inicial: commit 0881c9a. Alcance: frontend existente, pruebas y documentación. No se modificaron Java, modelo de datos, SQL/PLSQL, Spring Security ni políticas financieras. Durante la auditor?a no se crearon commits ni se hizo push.

## A. Diagnóstico inicial

Se inspeccionaron HTML, CSS, módulos app/API/presentación, proxy Node, package.json/lockfile, pruebas, scripts y README. La línea base ejecutada aprobó 17 pruebas unitarias/proxy y 14 de navegador. Git tenía únicamente front.zip no rastreado; se preservó sin incluirlo ni eliminarlo.

| Hallazgo | Archivo | Severidad | Evidencia | Corrección | Validación |
|---|---|---|---|---|---|
| Tabla vacía sustituía los mensajes iniciales | src/app.js | Media | signOut llama updateActivity, que siempre creaba table y has-rows | Restaurar nodos originales cuando no hay operaciones | Estado vacío antes/después de login/logout y transición a dos registros |
| Textos operativos pequeños y contraste insuficiente | styles.css | Media | A 390 px: metric-label 10 px, mini-label 9 px; contraste 2.47–3.72 en varios textos | Ajustes por selector y breakpoint; tonos más oscuros conservando paleta | Cinco viewports, medición CSS y capturas |
| Propiedades heredadas aceptadas como estados | src/presentation.js | Media | isResult aceptaba outcome='__proto__'; razones heredadas podían devolver objetos | Object.hasOwn y conteo entero seguro | __proto__, constructor y toString rechazados; razón desconocida conserva texto seguro |
| Foco poco útil al cambiar vistas o perder acceso | src/app.js, index.html | Media | Control anterior podía quedar oculto; tablas sin punto de acceso por teclado | Foco en main/usuario, región desplazable enfocada y nombres explícitos | Recorrido real con Tab/Enter en cinco anchos |
| Salto de scroll al entrar al dashboard | src/app.js | Baja | Captura completa mostraba cabecera desplazada por main.focus | Foco sin scroll y reinicio de posición al cambiar vista | Capturas posteriores y pruebas de teclado |
| Origen HTTP fijo | server.mjs | Limitación, no fallo local | Origin compara con http://Host; X-Forwarded no se interpreta | Documentar alcance HTTP local; no relajar validaciones | HTTPS/forwarded falsos rechazados sin llamar upstream |
| Timeout durante lectura del body | server.mjs | Descartado en entorno probado | Nueva prueba de body incompleto devuelve 504 y un solo comando | Ningún cambio de proxy | Regresión de timeout aprobada |
| XSS mediante mensajes de API | app.js/API | No reproducido | Render con textContent y razones traducidas, sin innerHTML/eval | Conservar render seguro; endurecer contrato heredado | Respuesta maliciosa no crea img ni ejecuta código |

Los hallazgos originales se contrastaron con el código, no se asumieron vigentes: ya existían mejoras tipográficas generales, pero varios overrides móviles/login y colores seguían siendo insuficientes. Se conservaron los controles de autenticación, precisión decimal y doble envío que funcionaban.

## B. Arquitectura existente

HTML semántico, CSS y módulos JavaScript nativos. Runtime del servidor: Node estándar, sin dependencias de producción. Dependencias de desarrollo existentes: @playwright/test 1.56.1 y Prettier 3.6.2, sin modificaciones de package.json ni package-lock.

- index.html: estructura, formularios, etiquetas y regiones.
- styles.css: paleta, layouts, breakpoints, estados y foco.
- src/app.js: estado temporal, login/logout, navegación y presentación DOM.
- src/api.js: Basic explícito, validación, fetch y traducción de errores.
- src/presentation.js: contrato, etiquetas, motivos y formato monetario.
- server.mjs: lista de archivos públicos y proxy restringido.

No se introdujo framework, cálculo contable, endpoint ni dependencia. La actividad se conserva en memoria con un máximo de 30 filas; los contadores representan operaciones de la sesión, no estadísticas globales ni filas de SUPPORT_AUDIT.

## C. Seguridad

Se mantiene autenticación real mediante GET /api/support/session y autorización independiente del backend para cada conciliación. No se guarda el acceso en localStorage, sessionStorage o cookies de aplicación. La contraseña se elimina del formulario tras éxito/error y las credenciales de memoria se descartan al salir o perder acceso. Se reinicia también el indicador visual de conexión. Una recarga vuelve al login.

El HTML de dashboard es un shell estático público sin datos; su ocultación y redirección son controles UX, no la frontera de seguridad. Los datos y comandos siguen protegidos por Java. No se imprimen contraseñas ni Authorization en el código cliente; las cabeceras explícitas son necesarias para Basic y visibles al usuario autorizado en sus propias herramientas de red. Basic codifica, no cifra: el transporte local HTTP no es una configuración productiva.

El DOM usa createElement/textContent. Los códigos de resultado/estado deben ser propiedades propias del contrato, y los motivos desconocidos usan un mensaje fijo. No hay eval, interpolación de HTML de respuestas ni un control público que habilite compensaciones. Las pruebas preservan tokens NUMBER(18,2) y muestran exactamente 9.999.999.999.999.999,99.

El proxy conserva CSP same-origin, frame-ancestors none, nosniff, no-referrer, no-store, rutas/métodos limitados y archivos internos inaccesibles. Solo reenvía Accept y Authorization; no reenvía cookies, parámetros de política ni cabeceras arbitrarias. Su cuerpo permitido es de **cero bytes**: Content-Length positivo o Transfer-Encoding se rechaza antes del upstream. Las pruebas incluyen backend desconectado, errores sanitizados y timeout sin reintento.

### HTTP local y HTTPS

El proceso Node sirve HTTP sobre loopback. La validación Origin funciona para ese origen local. Un navegador detrás de terminación TLS enviaría Origin https y sería rechazado aunque coincida el host. No se añadió confianza en X-Forwarded-Proto/Host ni configuración que acepte cualquier origen. Una prueba confirma que esas cabeceras no relajan el control.

BACKEND_URL acepta un origen HTTP(S) sin credenciales ni ruta: HTTPS al upstream es distinto de servir la web al navegador mediante TLS. Desplegar detrás de HTTPS requiere un diseño explícito de origen público, hosts y proxies confiables, certificados y permisos. Está fuera del alcance local; no se certificó TLS ni se agregó infraestructura.

## D. UX/UI

La actividad inicial muestra su explicación y ninguna tabla vacía. Tras operar aparece una fila por intento, de más reciente a más antiguo. La revisión manual tiene texto y símbolo propios; no incrementa pagos corregidos. Logout/relogin restaura métricas y mensajes vacíos.

Se conservaron colores base, branding, sidebar, tarjetas y estructura. Se oscurecieron textos secundarios y aumentaron selectivamente tipografías por debajo de 12 px que participaban en instrucciones, estados o actividad. No se incrementaron todos los textos por igual ni se rediseñó el layout.

Se permitió envolver encabezados y referencias, se eliminaron posiciones absolutas conflictivas del detalle de métricas y se apilaron importes en tarjetas estrechas. El texto visible de importes permanece intacto. Las tablas tienen desplazamiento local, cabeceras, caption accesible y región de teclado. No se ocultaron columnas para fingir que cabían.

Se corrigió el espacio entre las dos frases del titular móvil del login. Los botones importantes mantienen o alcanzan 44 px de alto; la prueba mide también el mínimo de 24 px en el control de contraseña. Los tamaños elegidos son decisiones de usabilidad: WCAG no establece un mínimo general de fuente de 12 px.

## E. Responsive y accesibilidad

Se probaron **375, 390, 768, 1024 y 1440 px** con login, dashboard y resultado. Se revisaron capturas de esos layouts y de RESOLVED, ALREADY_CONSISTENT, MANUAL_REVIEW y error en 390/1440. El helper genera 18 capturas con fixtures controlados y conserva mediciones de 15 vistas, más dos comprobaciones adicionales de reflujo.

| Texto medido a 390 px | Antes: tamaño/contraste | Después: tamaño/contraste |
|---|---|---|
| Ayuda de referencia | 12 px / 2.71:1 | 12 px / 6.56:1 |
| Aviso operativo | 12 px / 3.72:1 | 12 px / 6.13:1 |
| Etiqueta de métrica | 10 px / 3.56:1 | 12 px / 6.43:1 |
| Etiqueta de resultado | 9 px / 2.47:1 | 12 px / 6.56:1 |
| Ayuda de login | 11 px / 3.46:1 | 12 px / 6.43:1 |
| Descripción de resultado | 12 px / 3.26:1 | 12 px / 6.01:1 |

El cálculo usa colores CSS computados y luminancia relativa sobre el primer fondo opaco, excluyendo elementos ocultos y logos/decoración. En las vistas medidas, la búsqueda adicional de nodos de texto no detectó contrastes inferiores al umbral correspondiente. Esto no cubre todos los estados posibles, transparencias complejas, herramientas de asistencia ni una auditoría WCAG completa.

Se verificaron etiquetas asociadas, nombres de botones iconográficos, orden Tab/Enter para login y conciliación, región de tabla desplazable, foco visible en botones y controles, role=alert y aria-live/aria-busy para errores/resultados. Las etiquetas distinguen resultados sin depender del color. Los inputs de móvil usan 16 px para evitar zoom automático de entrada en navegadores que lo aplican.

No hubo overflow horizontal de página en las vistas medidas. La tabla puede desplazarse dentro de su región accesible. Se comprobó reflujo adicional a 320 px y una equivalencia de viewport de 720 CSS px con DPR 2 para un espacio físico de 1440 px. Esta equivalencia no es una prueba del zoom nativo del navegador ni de todos los niveles de ampliación. No se certifican lector de pantalla, navegación por voz ni WCAG 2.2 AA integral.

Referencias de criterios: [W3C contraste mínimo](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) y [W3C tamaño mínimo de objetivo](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html).

## F. Integración API y errores

GET /api/support/session permanece como validación de identidad sin efectos contables. POST /api/support/payments/{reference}/reconcile utiliza referencia validada/codificada, sin body ni parámetros de compensación. No hay reintentos automáticos; el estado busy evita solicitudes simultáneas involuntarias.

Se preservan los timeouts: login 10 s, conciliación cliente 25 s y upstream del proxy 20 s. Los errores no muestran SQL, stack traces ni detalle crudo del backend. Se verificaron 400, 401, 403, 404, 409, 500, 502, 503 y 504 mediante pruebas de API/UX; también JSON inválido, respuesta de otra referencia, pérdida de conexión y timeout. Un resultado no confirmado advierte que la operación pudo completarse y requiere verificación. Los 401/403 limpian acceso y vuelven al login.

RESOLVED se muestra como corrección aplicada; ALREADY_CONSISTENT como ausencia de cambios; MANUAL_REVIEW como intervención requerida con saldo sin alterar según Java. Se presentan netDebitBefore, netDebitAfter, movementsCreated y auditCreated, sin recalcular compensaciones. El formato monetario conserva el token decimal del JSON; un navegador antiguo que no pueda preservar un importe grande rechaza el resultado antes de redondearlo silenciosamente.

## G. Pruebas y evidencias

Desde front/ se ejecutaron:

```powershell
npm test
npm run test:e2e
node scripts/verify-visual.mjs
node scripts/verify-integration.mjs
```

Entorno: Windows, Node 26.5.0 y Chrome instalado. No se añadieron dependencias. La integración existente admite un JAR como primer argumento; esta ejecución usó su valor por defecto back/.tools/delivery-build/support-reconciliation-1.0.0-audit.jar, previamente construido y validado.

| Tipo | Resultado final | Qué demuestra |
|---|---|---|
| Unitarias/cliente y proxy Node | **21 aprobadas**, cero fallos/omisiones | Contrato, precisión, errores, origen, archivos, cuerpos y timeouts; upstream controlado en sockets HTTP locales |
| Navegador con respuestas controladas | **28 aprobadas**, cero fallos/omisiones | Flujos, estados, errores, teclado, cinco viewports y contenido malicioso |
| Capturas/mediciones con fixtures | 17 observaciones sin overflow; 18 PNG | Login/dashboard/resultado y estados adicionales; revisión visual y contraste acotado |
| Navegador → Node → Java real | **8 conciliaciones aprobadas** | Login real, cuatro referencias y reintentos, política segura, logout, API y credenciales no persistidas |
| Java/Oracle | No ejecutadas de nuevo en esta auditoría | Backend sin cambios; Oracle sigue pendiente de servidor real |

La primera suite ampliada obtuvo 23 aprobadas y cinco fallidas porque la prueba comprobaba :focus-visible después de activar una vista con ratón y enfocar programáticamente. Se corrigió la prueba para recorrer con Tab; no se contó como cinco defectos de aplicación. Tras completar los ajustes se repitió la suite completa: 28 aprobadas (56.6 s en la última ejecución). Se observaron advertencias del entorno Node/Playwright; la ejecución final no tuvo fallos.

Evidencia actual: [resumen](../evidencia/frontend-final/audit-summary.json), [mediciones](../evidencia/frontend-final/measurements.json), [mediciones anteriores](../evidencia/frontend-final/baseline-measurements.json) e [integración real](../evidencia/frontend-final/real-integration.json). Logs completos de trabajo: front/.tools/front-audit-*.log, ignorados por Git.

Capturas representativas: [login móvil](../evidencia/frontend-final/login-375.png), [dashboard escritorio](../evidencia/frontend-final/dashboard-1440.png), [corrección](../evidencia/frontend-final/result-1440.png), [consistente](../evidencia/frontend-final/consistent-1440.png), [manual móvil](../evidencia/frontend-final/manual-390.png) y [error](../evidencia/frontend-final/error-1440.png). Las capturas son de datos ficticios controlados, no evidencia de movimientos en la base real. Las ocho respuestas Java están en el JSON independiente.

## H. Archivos modificados

| Archivo | Justificación |
|---|---|
| front/src/app.js | Vacío de actividad, foco, caption/región de tabla, estado accesible de login y limpieza del indicador. |
| front/src/presentation.js | Rechazo de propiedades heredadas y conteo numérico seguro; conserva formato monetario. |
| front/index.html | Nombres accesibles de navegación, asociación de error de login y espacio del titular. |
| front/styles.css | Contrastes, tamaños seleccionados, foco, controles y adaptación de tarjetas/tablas. |
| front/tests/api.test.mjs | Regresión para propiedades heredadas y conteos fuera de rango seguro. |
| front/tests/server.test.mjs | Origin/forwarded, body rechazado, backend desconectado y timeout al leer body. |
| front/tests/browser/audit.spec.mjs | Catorce casos nuevos para vacío/transición, cinco tamaños, teclado, errores y respuestas maliciosas. |
| front/scripts/verify-visual.mjs | Capturas/mediciones reproducibles en servidor propio, sin tocar Java del usuario. |
| front/README.md, docs/02-guias/FRONTEND.md, README.md | Resultados vigentes, enlace al informe y límite HTTPS. |
| docs/05-entrega/USO_IA.md | Trazabilidad de esta revisión y de la corrección de pruebas. |
| docs/04-validaciones/AUDITORIA_FINAL_FRONTEND.md, docs/evidencia/frontend-final/ | Informe y evidencias compartibles. |

server.mjs, src/api.js, package.json, package-lock y archivos de backend permanecen sin cambios. Durante la auditor?a no se crearon commits ni se hizo push; front.zip preexistente sigue intacto. Los archivos de trabajo bajo .tools son auxiliares ignorados, no dependencias de ejecución del producto.

## I. Riesgos pendientes y alcance

- TLS/HTTPS, proxy inverso y configuración productiva no implementados ni certificados.
- Accesibilidad revisada en aspectos concretos; sin certificación WCAG, lector de pantalla ni zoom nativo exhaustivo.
- Chrome/Windows comprobados; no se ejecutaron Safari/iOS, Firefox ni todos los navegadores. No se garantiza un navegador que carezca de soporte para precisión JSON.
- Sin benchmark de carga o red lenta real. Los cinco recursos HTML/CSS/JS principales suman 73.899 bytes sin comprimir en esta revisión; el resumen conserva tamaños y hashes. Se inspeccionaron listeners/DOM; no se eliminó CSS legado ni se introdujeron optimizaciones sin beneficio demostrado.
- Los errores de backend controlados no sustituyen pruebas de una caída productiva. La integración real usa H2 aislado y política segura.
- Esta auditoría no vuelve a certificar Oracle ni modifica la contabilidad.
- Los cambios actuales están sin commit por instrucción expresa. El informe Git anterior describe el commit previo; antes de publicar esta revisión se requiere autorización para incorporarla y publicar.

## J. Veredicto final

**APROBADO CON LIMITACIONES.** Los defectos confirmados se corrigieron con cambios acotados, la seguridad existente se conserva y las verificaciones ejecutadas pasan. Login/logout, actividad vacía, resultados, precisión monetaria, errores, teclado y adaptación a cinco anchos funcionan dentro del entorno comprobado.

El frontend conserva JavaScript nativo y sigue consumiendo la API real sin decisiones contables. Se preservaron los procesos originales 8080/PID 12256 y 5173/PID 14732, sin reiniciar su JVM ni borrar H2; los servidores QA propios se cerraron. Las limitaciones de HTTPS, certificación de accesibilidad y navegadores adicionales quedan explícitas.

## Cierre local autorizado

Se incorporan fuentes, pruebas, documentaci?n y evidencias textuales. Por la instrucci?n de excluir binarios, las capturas PNG se conservan localmente fuera del commit, igual que front.zip. Las referencias a capturas de este informe requieren esos archivos locales; las mediciones JSON y el script de reproducci?n s? forman parte de la entrega.
