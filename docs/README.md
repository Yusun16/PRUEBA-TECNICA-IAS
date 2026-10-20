# Documentación de PagoClaro

Esta carpeta reúne las instrucciones, los informes y los resultados de las pruebas. No necesitas leer todos los archivos para usar la aplicación.

**Si es tu primera visita, empieza por el [README principal](../README.md)**: explica qué hace PagoClaro, cómo iniciarlo y cómo probar un pago.

## ¿Qué necesitas encontrar?

| Quiero… | Documento recomendado |
| --- | --- |
| Abrir la aplicación o aprender a usarla | [Guía principal](../README.md) |
| Probar las rutas con Swagger | [Swagger en la guía del backend](../back/README.md#swagger-ui--openapi) |
| Entender lo solicitado en la prueba | [Enunciado original en PDF](01-enunciado/Prueba_Tecnica_Desarrollador_Soporte_Java_Candidato.pdf) |
| Saber cómo investigar un pago | [Guía de soporte](02-guias/OPERACION_N3.md) |
| Entender la base de datos | [Modelo de datos](03-base-de-datos/MODELO_DATOS.md) |
| Revisar los cambios y pruebas de la interfaz | [Auditoría del frontend](04-validaciones/AUDITORIA_FINAL_FRONTEND.md) |
| Preparar Oracle | [Instalación y recuperación](03-base-de-datos/ORACLE_INSTALACION_Y_RECUPERACION.md) |
| Consultar archivos que respaldan las pruebas | [Índice de evidencias](evidencia/README.md) |

## Cómo está organizada la documentación

Los números indican un orden de lectura sugerido. Cada documento conserva su nombre para reconocerlo en el historial del proyecto.

```text
docs/
  README.md              Empieza aquí
  01-enunciado/          Qué pide la prueba
  02-guias/              Cómo funciona y cómo operarla
  03-base-de-datos/      Modelo, reglas e instrucciones de Oracle
  04-validaciones/       Revisiones y resultados por etapa
  05-entrega/            Preparación del repositorio y uso de IA
  evidencia/             Respuestas, mediciones y capturas de pruebas
```

## 01. Enunciado: qué había que construir

| Archivo | Para qué sirve |
| --- | --- |
| [Enunciado original](01-enunciado/Prueba_Tecnica_Desarrollador_Soporte_Java_Candidato.pdf) | Documento original recibido para la prueba técnica. |
| [Texto extraído del PDF](01-enunciado/Prueba_Tecnica_texto.txt) | Copia de consulta del texto; el PDF es la referencia original. |
| [Análisis de la prueba](01-enunciado/ANALISIS_PRUEBA.md) | Interpretación inicial de requisitos y casos. |

## 02. Guías: cómo funciona PagoClaro

| Archivo | Para qué sirve |
| --- | --- |
| [Operación de soporte](02-guias/OPERACION_N3.md) | Explica cómo tratar inconsistencias y cuándo pedir revisión humana. N3 se refiere al nivel de soporte especializado. |
| [Decisiones técnicas](02-guias/DECISIONES.md) | Explica por qué se eligieron las reglas y la estructura del programa. |
| [Revisiones de la interfaz](02-guias/FRONTEND.md) | Conserva la evolución y las comprobaciones de la pantalla. Para iniciarla, usa la [guía del frontend](../front/README.md). |

## 03. Base de datos: información y reglas de integridad

| Archivo | Para qué sirve |
| --- | --- |
| [Modelo de datos](03-base-de-datos/MODELO_DATOS.md) | Describe las tablas, sus relaciones y las garantías sobre los movimientos. |
| [Instalación y recuperación Oracle](03-base-de-datos/ORACLE_INSTALACION_Y_RECUPERACION.md) | Guía técnica para preparar o recuperar un entorno Oracle. |
| [Validación Oracle](03-base-de-datos/VALIDACION_ORACLE.md) | Explica comprobaciones y pendientes de ejecución en Oracle. |
| [Corrección de integridad](03-base-de-datos/CORRECCION_INTEGRIDAD_ORACLE.md) | Detalla ajustes de las reglas que protegen los datos. |

Los scripts ejecutables siguen en [`back/db/oracle/`](../back/db/oracle/). **Las pruebas con H2 no certifican Oracle; la validación en una base Oracle real sigue pendiente.**

## 04. Validaciones: qué se comprobó en cada etapa

Una validación es una revisión del funcionamiento. Estos informes corresponden a momentos distintos: sus cifras no deben sumarse ni interpretarse como una sola ejecución.

| Archivo | Alcance y momento |
| --- | --- |
| [Validación local inicial](04-validaciones/VALIDACION_LOCAL.md) | Primera ejecución con datos de prueba. Histórica. |
| [Auditoría inicial](04-validaciones/AUDITORIA_INICIAL.md) | Problemas encontrados antes de las correcciones. Histórica. |
| [Auditoría del backend](04-validaciones/AUDITORIA_FINAL.md) | Revisión y correcciones de esa etapa, anterior a Swagger y a otras validaciones posteriores. |
| [Validación integral de entrega](04-validaciones/VALIDACION_FINAL_ENTREGA.md) | Comprobación conjunta anterior a Swagger y al cierre posterior del frontend. |
| [Auditoría del frontend](04-validaciones/AUDITORIA_FINAL_FRONTEND.md) | Revisión posterior de seguridad, accesibilidad y adaptación a distintos tamaños de pantalla. |

La integración de Swagger y sus pruebas se documentan en la [guía del backend](../back/README.md#swagger-ui--openapi). El [README principal](../README.md#9-pruebas-y-documentación-para-la-revisión-técnica) reúne los últimos resultados registrados por componente y sus limitaciones.

Cuando un informe antiguo dice «vigente», se refiere a la fecha de aquella revisión. Se conserva para poder seguir la evolución del trabajo; no sustituye las instrucciones actuales de uso.

## 05. Entrega: repositorio y trazabilidad

| Archivo | Para qué sirve |
| --- | --- |
| [Preparación de Git](05-entrega/PREPARACION_GIT_ENTREGA.md) | Describe la revisión del repositorio y las pruebas desde una copia independiente en esa etapa. No es una autorización para publicar. |
| [Uso de inteligencia artificial](05-entrega/USO_IA.md) | Registra contribuciones, correcciones y límites del apoyo de IA. |

## Evidencias: archivos que respaldan los informes

Consulta el [índice de evidencias](evidencia/README.md) para encontrar respuestas de la API, resúmenes de pruebas y capturas. No necesitas abrirlos para usar PagoClaro.

La reorganización conserva los documentos originales y el contenido de las evidencias. Algunos registros históricos incluyen rutas de su ejecución original: esas rutas se conservan como parte del registro, aunque ahora el archivo se encuentre en otra carpeta.
