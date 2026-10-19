# Trazabilidad del uso de IA

Se utilizó Codex durante el análisis y desarrollo, autorizado por el apartado 7 del enunciado y por el candidato. Entradas: PDF de la prueba e instrucciones del usuario sobre calidad y ejecución. No se usaron credenciales ni datos productivos.

Contribuciones: extracción del texto del PDF con Node/zlib, matriz de casos, diseño transaccional, código Java/Spring/JDBC, pruebas, scripts Oracle y documentación. El historial Git conserva las entregas sucesivas.

Validación: compilar y ejecutar pruebas automatizadas con Java/Maven; comprobar estado y conteos persistidos, concurrencia, rollback y contratos HTTP; ejecutar la consulta SQL entregada en H2. Ver evidencia final en VALIDACION_LOCAL.md. Las aserciones PL/SQL se entregan para Oracle, pero no se afirman ejecutadas en un servidor inexistente.

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
