# Evidencias de pruebas

[Volver al índice de documentación](../README.md)

Aquí se guardan archivos que respaldan las revisiones: respuestas del programa, resultados de pruebas y capturas de pantalla. Son datos de demostración, no registros de operaciones bancarias reales.

## Dónde buscar

| Carpeta | Qué contiene | Informe relacionado |
| --- | --- | --- |
| [inicial/](inicial/) | Respuestas de la primera prueba local. | [Validación local inicial](../04-validaciones/VALIDACION_LOCAL.md) |
| [auditoria/](auditoria/) | Respuestas y conteos de las revisiones del backend y de integridad. | [Auditoría del backend](../04-validaciones/AUDITORIA_FINAL.md) |
| [entrega/](entrega/) | Resúmenes, respuestas HTTP, integración y estado de Git de la validación de entrega. | [Validación integral](../04-validaciones/VALIDACION_FINAL_ENTREGA.md) |
| [git/](git/) | Resultados obtenidos desde una copia independiente del repositorio. | [Preparación de Git](../05-entrega/PREPARACION_GIT_ENTREGA.md) |
| [frontend/](frontend/) | Capturas y respuestas de etapas anteriores de la interfaz y del acceso. | [Revisiones de la interfaz](../02-guias/FRONTEND.md) |
| [frontend-final/](frontend-final/) | Resumen, mediciones y resultados de la auditoría posterior de la interfaz. | [Auditoría del frontend](../04-validaciones/AUDITORIA_FINAL_FRONTEND.md) |

## Cómo leer los archivos

- **`.json`:** datos estructurados, como respuestas o conteos. Se pueden abrir en un editor de texto.
- **`.txt`:** resúmenes de resultados que se leen como texto normal.
- **`.png`:** imágenes de las pantallas revisadas.

Los resultados de cada carpeta pertenecen a su propia revisión. Una captura con respuestas simuladas muestra la apariencia de la interfaz; no demuestra una operación contra la base de datos. Los informes indican cuándo se usó el backend real.

Las 18 capturas PNG de `frontend-final/` se conservaron localmente y quedaron fuera del commit del cierre del frontend por la instrucción de excluir binarios. Por eso pueden no aparecer al descargar el repositorio. Los resúmenes y mediciones textuales sí forman parte de ese cierre; las capturas se pueden reproducir con las instrucciones de la [guía del frontend](../../front/README.md).

Las evidencias originales no se editaron durante esta reorganización. Sus fechas, rutas históricas y huellas de comprobación describen el estado de la ejecución que las produjo.
