# Trazabilidad del uso de IA

Se utilizó Codex durante el análisis y desarrollo, autorizado por el apartado 7 del enunciado y por el candidato. Entradas: PDF de la prueba e instrucciones del usuario sobre calidad y ejecución. No se usaron credenciales ni datos productivos.

Contribuciones: extracción del texto del PDF con Node/zlib, matriz de casos, diseño transaccional, código Java/Spring/JDBC, pruebas, scripts Oracle y documentación. El historial Git conserva las entregas sucesivas.

Validación: compilar y ejecutar pruebas automatizadas con Java/Maven; comprobar estado y conteos persistidos, concurrencia, rollback y contratos HTTP; ejecutar la consulta SQL entregada en H2. Ver evidencia vigente en VALIDACION_FINAL_ENTREGA.md; VALIDACION_LOCAL.md conserva resultados históricos. Las aserciones PL/SQL se entregan para Oracle, pero no se afirman ejecutadas en un servidor inexistente.

Ajustes a propuestas iniciales:

- Se descartó compensar DECLINED con débitos sin autorización adicional del negocio.
- Se añadió revisión manual para APPROVED con historial completamente revertido; recrear un débito podría deshacer una devolución válida.
- La huella inicial concatenaba todo el historial; se cambió a SHA-256 incremental para compartir formato con PL/SQL sin límite de VARCHAR2.
- La consulta agrega eventos y ledger antes de unir para evitar multiplicar montos.
- Se explicitó que el bloqueo del padre exige cooperación de todos los escritores; no se presenta como protección automática de tablas hijas.
- Se separó la validación local ejecutada de la validación Oracle pendiente.

No se atribuyen a una revisión humana ni a Oracle las comprobaciones realizadas únicamente por el agente y H2. El candidato debe revisar las reglas y ser capaz de explicar sus supuestos durante la evaluación.

## Extensión solicitada: frontend

Se utilizó Codex para implementar la consola web, el proxy local Node, el iniciador conjunto y las pruebas de interfaz. Se aplicó la guía de diseño de la habilidad Sites al trabajo local, sin publicación externa. Se revisaron capturas de escritorio/móvil y se verificaron los flujos en Chrome con respuestas controladas y con el backend real. Se mejoró la legibilidad tras revisar capturas y se preservaron los tokens decimales para evitar redondeo de importes grandes. Resultados y limitaciones en FRONTEND.md.

Posteriormente, a solicitud del usuario, se sustituyó la configuración de credenciales por un login validado con Java antes de mostrar el dashboard. Se añadieron endpoint de acceso de solo lectura, pruebas de credenciales/roles, limpieza al cerrar sesión y verificación real de navegador. Se retiraron los ejemplos y la contraseña demo del código público; permanecen únicamente en documentación y fixtures de laboratorio.

## Auditoría solicitada mediante archivo adjunto

Codex inspeccionó los fuentes y ejecutó la línea base antes de modificar. Encontró y corrigió falta de inmutabilidad H2, validación incompleta de relaciones, bloqueo omitible por escritores directos, compensación habilitada por supuesto y canonicalización dependiente del repositorio. La nueva prueba de timeout falló inicialmente (503 frente a 409); se corrigió su traducción en la adquisición del bloqueo y se repitieron las pruebas. No se corrigieron históricos para hacer pasar escenarios: los tests usan bases desechables aisladas.

Se prepararon migración Oracle reanudable, guardas, pruebas de rollback y contratos comunes. Estas salidas fueron revisadas estáticamente, no ejecutadas en Oracle. Los resultados locales reales, la suite omitida y las decisiones están en AUDITORIA_FINAL.md. Se generó un JAR separado y se verificó en otra instancia para preservar la sesión H2 abierta del usuario. No se publicaron archivos ni se enviaron mensajes a terceros.

## Validación integral de entrega

Se utilizó Codex para contrastar el código actual con el PDF disponible y repetir las pruebas. Se reprodujo un 401 sin cuerpo ProblemDetail y se corrigieron las respuestas 401/403 sin sustituir Basic. Se añadió rechazo de contraseña demo fuera del perfil local, una salida Maven configurable para clean aislado y una prueba Chrome → Node → Java sin mocks. Se corrigió la nota operativa que aún autorizaba APPROVED sin ledger y la descripción antigua de compensaciones. La primera invocación de Maven se interrumpió por ErrorActionPreference=Stop ante una advertencia stderr de Mockito; se corrigió el entorno de ejecución y clean verify terminó correctamente. Se registran 53 pruebas Java/H2 aprobadas, una suite Oracle omitida, 31 pruebas frontend y las integraciones reales. No se hicieron commits, push, migraciones Oracle ni reinicios de la aplicación del usuario. El informe final conserva limitaciones y distingue evidencia ejecutada de revisión estática.

## Preparación Git posterior autorizada

El usuario autorizó expresamente commits locales y clon independiente. Codex revisó fuentes, configuraciones, evidencias y patrones de secretos en el historial; separó las correcciones en commits de integridad, seguridad y documentación, sin reescribir historia. Conservó el ZIP Oracle antiguo fuera de Git y reprodujo desde Git con herramientas y cachés aisladas: 53 pruebas Java/H2, 17 frontend, 14 navegador e integraciones reales aprobadas. El clon descubrió que el smoke no creaba back/.tools al guardar evidencia; se añadió esa creación en un commit separado y se repitió con éxito. El resultado, los commits y las limitaciones se registran en PREPARACION_GIT_ENTREGA.md. No se autorizó ni realizó publicación remota; Oracle continúa pendiente.

## Auditoría específica del frontend

Se utilizó Codex para inspeccionar y medir la interfaz existente, corregir actividad vacía, contraste/legibilidad y validación de propiedades propias, revisar capturas y ejecutar pruebas. La primera aserción nueva de foco confundía modalidad de ratón con teclado; se corrigió para usar Tab. Se mantuvo el proxy HTTP local en lugar de introducir confianza insegura en cabeceras forwarded. Resultado: 21 pruebas unitarias/proxy, 28 de navegador y ocho conciliaciones Java reales aprobadas. Se documenta accesibilidad parcial y equivalencia de reflujo, sin afirmar certificación WCAG ni zoom nativo. No se modificaron backend/Oracle, no se reinició la aplicación original y no se hicieron commits ni push. Evidencia en AUDITORIA_FINAL_FRONTEND.md.
