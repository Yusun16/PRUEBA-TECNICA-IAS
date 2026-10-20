[Volver al ?ndice de documentaci?n](../README.md)

# Preparación definitiva de Git y entrega en GitHub

Fecha: 19 de octubre de 2026, America/Bogota. Este informe corresponde a la autorización posterior para crear commits locales. No cambia la lógica financiera validada ni certifica Oracle.

## A. Estado inicial de Git

Git ya estaba inicializado. Se comprobó rev-parse, status, branch, log y remote. Rama master, HEAD inicial c8fbb1aaab6fa4032bf54d7f45ef39a7192a26c2, nueve commits existentes y ningún remoto. Había 34 archivos modificados y 16 no rastreados, incluido back/db/oracle.zip. No se ejecutó git init, reset --hard, clean, amend ni force-push.

## B. Rama e historial conservados

Se conserva master y la cadena completa anterior. Renombrar a main sería posible sin reescribir commits, pero no aporta una necesidad de entrega y no se realizó. Los comandos de publicación preparados usan master. La autenticación GitHub y el repositorio remoto quedan fuera de las acciones realizadas.

## C. Archivos incorporados

Fuentes Java, pruebas y fixtures, frontend y cliente HTTP, SQL/PLSQL 00–18 e instaladores, Maven Wrapper y su propiedad de distribución, package.json/lockfile, scripts PowerShell/Node, PDF original, diagnóstico, ADRs, operación N3, uso de IA y evidencias compartibles.

Se separaron en el índice las aserciones de seguridad de ReconciliationIntegrationTest respecto a los casos contables, sin sobrescribir el archivo de trabajo, para agrupar correctamente los commits. Los contenidos funcionales son los que ya pasaron la validación local.

## D. Archivos excluidos y revisión de seguridad

Se preserva back/db/oracle.zip en disco. Se inspeccionaron sus entradas: contiene la carpeta oracle con versiones anteriores y carece de los nuevos scripts 16–18; es redundante y desactualizado frente a los archivos individuales. Se excluyó expresamente en .gitignore. No se eliminó.

Se excluyen target, .tools, .m2, node_modules, logs, resultados temporales y .env. Se amplió la protección a .env.* manteniendo la posibilidad de una plantilla .env.example sin secretos. No se detectaron compilados/cachés/logs rastreados en la inspección del índice.

Se revisaron application*.yml, clases de configuración, scripts, documentación, frontend y JSON. Los secretos Oracle y de soporte se toman del entorno; los únicos valores de acceso explícitos identificados son ficticios de laboratorio o fixtures de tests. Se escanearon 98 archivos de texto actuales y nueve commits históricos por patrones de claves privadas, identificadores AWS y tokens GitHub, sin coincidencias. Es una revisión acotada más inspección manual, no una garantía universal de ausencia de secretos. No se incluyen valores operativos ni cabeceras Authorization en las evidencias nuevas.

## E. Commits locales

| Commit | Contenido |
|---|---|
| 406c3fb | fix: strengthen payment reconciliation and Oracle integrity — reglas conservadoras, integridad H2/Oracle, contratos y pruebas. |
| 3024e48 | fix: harden backend security and integration validation — HTTP, credenciales, compilación aislada, smoke e integración real. |
| 3eb1963 | docs: finalize PagoClaro technical delivery — guías y evidencias completas anteriores a la prueba del clon. |
| b931b56 | fix: create smoke evidence directory in fresh clones — crea la carpeta de salida antes de guardar evidencias. |
| 0e14927 | docs: record clean-clone verification and GitHub readiness — resultados y evidencias del clon. |

Se revisaron listas, diferencias preparadas y diff --check antes de confirmar. Una última actualización documental registra el origin detectado durante el cierre, sin modificar su configuración ni publicar. El hash del commit que contiene este informe se obtiene con `git log -1 --format=%H`; no se autoincrusta para evitar una dependencia circular y se informa al finalizar la tarea. Ningún commit histórico se modificó.

## F. Clon limpio independiente

Se creó un directorio temporal nuevo fuera del proyecto y se ejecutó `git clone --no-hardlinks` desde Git. HEAD inicial del clon: **3eb196300f7cb9a28139d3e032e95c9e01c53efe**. Se comprobó que no contenía back/target, back/.tools, back/.m2, front/node_modules, back/db/oracle.zip ni alternates de objetos Git. No se copiaron fuentes, JAR ni dependencias del proyecto original.

El JDK 21 es una herramienta externa: se preparó una copia independiente del JDK disponible en el directorio temporal. La ejecución no depende de que exista el JDK ignorado del proyecto original. Maven Wrapper y dependencias se descargaron de Maven Central en directorios nuevos; se usaron settings mínimos externos. npm instaló cuatro paquetes desde package-lock con caché y configuraciones vacías independientes. Node y Chrome instalados son requisitos externos explícitos, no archivos omitidos del repositorio.

En este equipo Windows, el primer clone fue rechazado por distinta propiedad del repositorio original entre identidades del entorno. Se repitió con safe.directory limitado a la invocación y a las rutas exactas del repositorio y su .git; no se modificó la configuración global ni se desactivaron verificaciones para otros repositorios.

El primer smoke reveló una dependencia de carpeta ignorada: ejecutó los casos, pero falló al guardar audit-http-results.json porque back/.tools no existía. Se añadió una sola instrucción de creación de directorio y se confirmó en b931b56. El clon recibió el cambio mediante fetch y merge --ff-only; se repitió el smoke y aprobó. Java, SQL y frontend no cambiaron respecto al commit que compiló. La comprobación Git de esas rutas confirma igualdad.

El commit final agrega únicamente este cierre y evidencias. Después de confirmarlo se actualiza el clon por Git y se verifica el HEAD final, árbol limpio e igualdad de las rutas de aplicación con la versión probada. No se atribuye a ese cierre documental una nueva ejecución completa de tests.

## G. Pruebas

Resultados nuevos desde el clon:

| Verificación | Resultado |
|---|---|
| Maven Wrapper y clean verify con cachés nuevas | BUILD SUCCESS; 54 registradas, 53 aprobadas, cero fallos/errores, una suite Oracle omitida; duración 01:24 min. |
| npm ci desde lockfile y caché nueva | Cuatro paquetes instalados; no se usó node_modules original. |
| npm test | 17 aprobadas, cero fallos. |
| npm run test:e2e | 14 aprobadas, cero fallos; 12.5 segundos. |
| Chrome → Node → Java, sin mocks | Login incorrecto/correcto, ocho conciliaciones de cuatro pagos y reintentos, política segura, logout y no persistencia de credenciales. |
| API real y política segura | ProblemDetail 400/401/404 y rechazo de activación por JSON/query/cabecera. |
| Smoke tras corrección | Ocho comandos reales, BX17 demo/seguro, reintentos y reinicialización H2. |
| Artefacto utilizado | SHA-256 idéntico en las evidencias de HTTP y navegador del clon. |
| Aplicación original | 8080/PID 12256 y 5173/PID 14732 preservados; QA finalizada. |
| Oracle | Suite omitida por ausencia de instancia, no aprobada. |

Comandos ejecutados desde el clon, con JAVA_HOME apuntando al JDK 21 externo y MAVEN_USER_HOME a un directorio nuevo:

```powershell
# Desde back/: settings XML mínimo fuera del clon, sin secretos
.\mvnw.cmd '-Dmaven.repo.local=.m2' '-Dartifact.suffix=-audit' -s $settings -B clean verify
# Desde front/: cache y archivos de configuración nuevos fuera del clon
npm ci --ignore-scripts --no-audit --no-fund --cache $npmCache --userconfig $emptyUser --globalconfig $emptyGlobal --registry=https://registry.npmjs.org
npm test
npm run test:e2e
node scripts/verify-integration.mjs ../back/target/support-reconciliation-1.0.0-audit.jar
# Desde back/, después del arreglo b931b56
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\audit-smoke.ps1
```

Los settings, caches y variables anteriores se crearon para aislar la máquina, no son requisitos ocultos de la aplicación. Las instrucciones normales del README también utilizan el mismo wrapper y lockfile. La prueba real usó 18084/5175 y el smoke 18082; no utilizaron 8080/5173. Los procesos H2 son JVM independientes.

Evidencia compartible: [git-clon-pruebas.json](../evidencia/git/git-clon-pruebas.json), [resumen Maven](../evidencia/git/git-clon-maven-resumen.txt), [HTTP](../evidencia/git/git-clon-http.json) e [integración real](../evidencia/git/git-clon-integracion-real.json). Los registros anteriores entrega-* conservan su fecha y estado histórico; no se reescribieron para simular otro commit.

## H. Estado de GitHub

Al iniciar no existía remoto. Durante la comprobación final se detectó **origin** con URL `https://github.com/Yusun16/PRUEBA-TECNICA-IAS.git`, tanto para fetch como push. Esta tarea no añadió ni cambió ese remoto; se conservó como apareció configurado. No se ejecutó push ni se creó un repositorio remoto. Su existencia en GitHub, contenido, permisos y visibilidad no fueron consultados ni certificados.

Se recomienda visibilidad **PRIVATE** hasta confirmar cómo accederán los evaluadores. El nombre sugerido por la instrucción, pagoclaro-soporte-n3, queda como alternativa si el propietario decide crear otro repositorio; no se renombró ni sustituyó origin para imponer ese nombre.

## I. Instrucciones para publicar cuando exista URL real

Conservar el origin actual hasta que el propietario confirme el destino. Si todavía debe crear el repositorio de destino, elegir PRIVATE y no generar README, .gitignore ni licencia, porque ya existe historial local; esto evita introducir un historial remoto distinto al importar. Referencias: [crear un repositorio](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository), [publicar código local existente](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github).

Después de confirmar que origin es el destino correcto y recibir autorización explícita para publicar, desde la raíz:

```powershell
git status
git remote -v
git branch --show-current
# Antes del push: comprobar permisos, visibilidad e historial remoto.
git push -u origin master
```

El push está documentado, no ejecutado. No ejecutar remote add origin de nuevo: origin ya existe. Si el propietario quiere otro destino, revisar primero su URL y autorizar el cambio por separado. Autenticarse con el mecanismo seguro de Git/GitHub disponible; no guardar tokens en archivos ni en la URL. Si hay commits remotos, inspeccionarlos sin force-push y acordar la integración. Tras publicar, compartir acceso con los evaluadores conforme a las instrucciones de la prueba.

## J. Riesgos y acciones pendientes

Oracle 19c+ no ejecutado; el frontend adicional y las iteraciones posteriores no acreditan cumplir el límite original de dos horas. La publicación requiere confirmar el origin detectado, sus permisos/historia/visibilidad y autorizar el push; no existe certificación productiva de capturas, carga, IAM o TLS. Se conserva la aplicación original de 8080/5173 sin reiniciar ni alterar H2.

**LISTO PARA PUBLICAR EN GITHUB.** La versión completa está incorporada a Git, conserva su historial, se reprodujo en un clon independiente y pasó las verificaciones locales descritas. El único defecto descubierto en el clon fue corregido y su prueba repetida. No se ejecutó publicación remota ni se inventó una URL. Para publicar falta confirmar y verificar el origin detectado y autorizar el push; Oracle continúa pendiente.
