# Consolidación local de Swagger y documentación

[Volver al índice](../README.md)

Revisión del 19 de octubre de 2026, America/Bogota. Base: `7373a9c229ba4211bf1594d824a5d5d98ba25363`. El usuario autorizó un único commit local para incorporar Swagger/OpenAPI, la guía principal y la organización documental.

## Alcance revisado

- Springdoc 2.8.17 integrado con Spring Boot 3.5.16. El contrato documenta exclusivamente GET de acceso y POST de conciliación, sin cuerpo para el POST y con autenticación HTTP Basic.
- Swagger UI, sus recursos y el contrato requieren rol SUPPORT. Sin credenciales: 401; sin el rol: 403. No se habilita lectura pública. La documentación está deshabilitada por defecto fuera de local y la autorización de Swagger no se persiste.
- Se documentan los resultados de negocio y errores HTTP, incluido 500. No se añadieron operaciones financieras ni se modificó la lógica contable.
- README principal con instrucciones para principiantes, acceso, Swagger, datos temporales y problemas frecuentes.
- Documentos agrupados por tema, con índices y enlaces actualizados. Los 38 archivos trasladados siguen presentes. Las evidencias mantienen su contenido; las rutas históricas internas se conservan.
- El PDF y las capturas que ya estaban versionados se trasladan como los mismos objetos de Git. No se agregan nuevos binarios, ZIP, respaldos ni temporales. Las 18 capturas locales no versionadas de frontend-final permanecen fuera del commit.

## Pruebas ejecutadas

Con Java 21 y Maven Wrapper, desde back:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.m2/repository' '-Dbuild.output-directory=.tools/consolidation-build' verify
```

Resultado final: 59 pruebas registradas; **58 aprobadas, cero fallos, cero errores y una suite Oracle omitida**. Conserva las 53 pruebas previas e incorpora cinco pruebas de Swagger/configuración. Incluye contrato, esquema de respuestas, ausencia de contraseña demo en el contrato y rechazo de acceso anónimo o sin rol.

Desde front:

```powershell
npm test
npm run test:e2e
node scripts/verify-integration.mjs ../back/.tools/consolidation-build/support-reconciliation-1.0.0.jar
```

Resultados: **21 pruebas cliente/proxy, 28 de navegador y ocho conciliaciones con Java real aprobadas**. La integración empleó exclusivamente sus servicios propios de 18084/5175 y los cerró al finalizar.

Una comprobación adicional en Chrome, con Java aislado en 18085 y H2 propia, verificó Swagger UI, autorización Basic y Try it out de acceso (200/SUPPORT). Las solicitudes anónimas al contrato, UI y configuración devolvieron 401. Ambos procesos propios se cerraron. No se reinició ninguna aplicación del usuario ni se borraron sus datos.

Se verificaron los enlaces locales y las huellas de los archivos de evidencia trasladados. [Resumen estructurado](../evidencia/entrega/consolidacion-final.json). Los logs auxiliares permanecen ignorados bajo back/.tools y front/.tools.

## GitHub: diagnóstico y publicación pendiente

El remoto conserva `https://github.com/Yusun16/PRUEBA-TECNICA-IAS.git`. DNS y conexión TCP a 443 funcionaron; git ls-remote pudo leer master mediante el mecanismo de credenciales existente. No se encontró proxy Git ni variables de proxy configuradas. La verificación TLS conserva su valor predeterminado habilitado. No se cambiaron configuraciones globales ni se almacenaron tokens en el proyecto.

La consulta autenticada a la API de GitHub devolvió HTTP 200 y **visibilidad PUBLIC**. El master remoto observado fue `3eb196300f7cb9a28139d3e032e95c9e01c53efe`, ancestro del HEAD local de la revisión. No se encontró divergencia en esa consulta. El fallo de conexión anterior no se reprodujo; no hay evidencia suficiente para atribuirle una causa concreta.

**No se realizó push ni se cambió la visibilidad.** Antes de publicar se debe consultar nuevamente el remoto y su visibilidad. Mientras sea público se requiere confirmación explícita del usuario para publicar en ese destino; si cambia a privado, verificarlo antes de continuar. Con esas condiciones resueltas, desde la raíz:

```powershell
git push -u origin master
git rev-parse HEAD
git ls-remote origin refs/heads/master
```

Solo se puede declarar la publicación correcta si el hash remoto coincide con el local. No se permite force-push. Oracle real y la certificación completa de accesibilidad continúan pendientes.
