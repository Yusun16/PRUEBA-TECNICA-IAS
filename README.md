# PagoClaro — revisión y conciliación de pagos

PagoClaro es una aplicación de demostración creada para la prueba técnica de IAS. Ayuda al personal de soporte a revisar pagos que presentan inconsistencias y a corregirlos cuando existe información suficiente.

**Conciliar un pago** significa comparar su estado con los registros de sus movimientos para comprobar si coinciden. Por ejemplo, un pago puede aparecer como pendiente aunque ya tenga registrado el cobro.

La aplicación puede corregir una inconsistencia, indicar que todo está correcto o pedir una revisión humana. Conserva los movimientos originales para que se pueda conocer lo sucedido.

**Esta demostración usa pagos ficticios. No cobra, devuelve ni mueve dinero real, y no se conecta con un banco o proveedor de pagos.**

## 1. ¿Qué puedo hacer?

- Iniciar sesión con un usuario de soporte.
- Escribir la referencia que identifica un pago.
- Solicitar su revisión mediante el botón **Conciliar pago**.
- Ver el resultado, el importe registrado antes y después y las acciones realizadas.
- Consultar las operaciones de la ventana actual y cerrar sesión.
- Probar las mismas funciones desde Swagger, una página para enviar solicitudes sin usar la interfaz principal.

## 2. Si la aplicación ya está encendida

Abre esta dirección en Chrome o Edge:

**[Abrir PagoClaro](http://127.0.0.1:5173/login)**

La dirección `127.0.0.1` significa «este mismo equipo». Solo funcionará en el equipo donde se está ejecutando la aplicación; no es una página publicada en Internet.

Usa las credenciales predeterminadas de la demostración:

| Dato | Valor que debes escribir |
| --- | --- |
| Usuario | `n3` |
| Contraseña | `demo-change-me` |

Pulsa **Iniciar sesión**. Si las credenciales son válidas, aparecerá el panel principal, también llamado *dashboard*.

Estas credenciales son únicamente para pruebas locales. Si la persona que preparó el entorno las cambió, debes usar las que te haya indicado.

**Si el navegador dice que no puede conectarse, sigue los pasos de la siguiente sección. Si la aplicación ya funciona, no necesitas volver a iniciarla.**

## 3. Cómo encender la aplicación en Windows

### Antes de empezar

Necesitas tener la carpeta completa del proyecto y estas herramientas instaladas:

| Herramienta | ¿Para qué se necesita? |
| --- | --- |
| Java JDK 21 | Ejecuta la parte que revisa los pagos. Debe ser el JDK, que también permite preparar el programa. |
| Node.js 22 o superior, con npm | Ejecuta la página de acceso y el panel principal. npm viene con Node.js. |
| Chrome o Edge actualizado | Permite abrir y usar la aplicación. |
| Conexión a Internet para la primera preparación | Descarga los componentes que necesita el proyecto. |

No necesitas instalar Oracle para esta demostración. El programa crea una base de datos temporal llamada **H2** al iniciar.

Si no sabes instalar Java o Node.js, pide ayuda para preparar estas herramientas una sola vez. Las herramientas portátiles que pueda tener este equipo no se incluyen en el repositorio.

### Paso 1. Abrir una ventana de comandos

1. Abre la carpeta principal del proyecto en el Explorador de archivos. Es la que contiene este `README.md` y las carpetas `back`, `front` y `docs`.
2. Haz clic en la barra de direcciones del Explorador, escribe `powershell` y pulsa **Enter**.
3. Se abrirá PowerShell, una ventana donde puedes copiar los comandos de esta guía.

Puedes comprobar que las herramientas están disponibles copiando estas líneas, una por una:

```powershell
java -version
node --version
npm --version
```

Java debe indicar la versión 21 y Node.js una versión 22 o superior. Si un comando «no se reconoce», revisa la instalación antes de continuar. Si utilizas `JAVA_HOME`, esa configuración debe señalar la carpeta del JDK 21; consulta con quien preparó el equipo si necesitas ayuda.

### Paso 2. Iniciar PagoClaro

Con la aplicación detenida, copia este comando en PowerShell y pulsa **Enter**:

```powershell
powershell -ExecutionPolicy Bypass -File .\front\scripts\start-local.ps1 -Build
```

Este comando prepara el programa, ejecuta sus pruebas e inicia las dos partes de la aplicación. La primera vez puede tardar varios minutos por las descargas. Espera a que aparezca el mensaje **PagoClaro listo**. Si aparecen errores, consulta la sección de problemas frecuentes.

**Deja abierta esa ventana mientras usas PagoClaro.**

### Paso 3. Abrir la pantalla de acceso

En el navegador, entra en **http://127.0.0.1:5173/login** e inicia sesión con las credenciales de la sección 2.

En los siguientes arranques, si el código no ha cambiado, puedes usar el mismo comando sin `-Build`:

```powershell
powershell -ExecutionPolicy Bypass -File .\front\scripts\start-local.ps1
```

La opción `-Build` vuelve a preparar y probar el programa. Úsala después de modificar el backend y únicamente cuando la aplicación esté detenida.

## 4. Cómo revisar un pago, paso a paso

1. Inicia sesión y entra en **Conciliación**.
2. En el campo de referencia, escribe `PAY-AX91`.
3. Pulsa **Conciliar pago** una sola vez y espera el resultado.
4. Lee el mensaje y compara los importes anteriores y finales.
5. Abre **Actividad de sesión** para consultar las operaciones realizadas en esa ventana.

La referencia es el identificador de un pago; no es una contraseña ni un número de tarjeta. No se muestra un listado de pagos: debes introducir la referencia que necesitas revisar.

Estos son los cuatro ejemplos disponibles al iniciar una demostración nueva:

| Referencia | Situación de ejemplo | ¿Qué debería suceder? |
| --- | --- | --- |
| `PAY-AX91` | El estado del pago no coincide con el cobro registrado. | Corrige el estado y conserva el cobro existente. |
| `PAY-BX17` | Hay dos registros de débito. | En la simulación local, crea un movimiento que compensa el duplicado identificado. Con la política segura, pide revisión humana. |
| `PAY-CQ22` | La información del pago es contradictoria. | Pide revisión humana; no aplica una corrección financiera automática. |
| `PAY-DK44` | El pago ya está correcto. | Informa que es consistente y no realiza cambios. |

Los resultados pueden variar si ya revisaste esos pagos durante la misma ejecución. Repetir una conciliación no debe duplicar los movimientos de corrección.

### Cómo interpretar el resultado

- **Corregido (`RESOLVED`):** el sistema aplicó la corrección que pudo justificar con los registros disponibles.
- **Ya consistente (`ALREADY_CONSISTENT`):** los registros coinciden; no se necesita otra corrección.
- **Revisión manual (`MANUAL_REVIEW`):** hace falta que una persona investigue. **No significa que el pago haya quedado corregido.**

El importe mostrado corresponde al débito neto: los cobros registrados menos sus movimientos de reversión. Un movimiento compensatorio contrarresta un movimiento anterior sin borrarlo. La auditoría es el registro de lo que hizo el sistema.

La corrección automática del duplicado de `PAY-BX17` es una excepción para demostrar el caso con datos ficticios. No autoriza a compensar cobros reales basándose únicamente en una aprobación de pago. La [guía del backend](back/README.md) explica la política segura.

## 5. Cómo probar las rutas sin la pantalla principal: Swagger

Swagger muestra las funciones del backend y permite probarlas desde el navegador. El **backend** es la parte que valida el acceso y revisa los pagos. Sus rutas forman una **API**: direcciones a las que se envían solicitudes.

Si iniciaste una versión que incluye Swagger con los pasos de esta guía, abre:

**[Abrir Swagger](http://127.0.0.1:8080/swagger-ui/index.html)**

Durante la integración también se preparó una instancia separada en **http://127.0.0.1:8081/swagger-ui/index.html**. Esa dirección solo funciona mientras esa instancia esté encendida y utiliza sus propios datos de prueba. No comparte los cambios de los pagos con la aplicación de `8080`.

Para probar el acceso:

1. Al abrir Swagger, el navegador solicita usuario y contrase?a mediante HTTP Basic. Usa las credenciales de soporte. La documentaci?n tambi?n est? protegida.
2. Dentro de Swagger, pulsa **Authorize** (autorizar).
3. Escribe el usuario `n3` y la contraseña `demo-change-me`, o tus credenciales personalizadas.
4. Confirma con **Authorize** y cierra el cuadro con **Close**.
4. Abre la operación **GET /api/support/session**.
6. Pulsa **Try it out** (probar) y después **Execute** (ejecutar).
7. Una respuesta **200** con el usuario y el rol `SUPPORT` indica que el acceso es válido. Esta operación no modifica pagos.

Para revisar un pago, abre **POST /api/support/payments/{reference}/reconcile**, pulsa **Try it out**, escribe `PAY-AX91` en `reference` y pulsa **Execute**. No necesitas escribir un cuerpo de solicitud. Esta operación puede modificar el pago y registrar acciones, igual que el botón de la interfaz.

| Código de respuesta | Significado |
| --- | --- |
| `200` | La solicitud se procesó. Lee el resultado: también puede ser una revisión manual. |
| `400` | La referencia no tiene un formato válido. |
| `401` | Faltan credenciales o son incorrectas. |
| `403` | El usuario no tiene permiso de soporte. |
| `404` | No existe un pago con esa referencia. |
| `409` | Otra operación tiene ocupado el pago. |
| `500` o `503` | Hubo un error interno o no fue posible completar la operación. |

Swagger está habilitado por defecto únicamente en el entorno local de demostración. Más opciones y ejemplos: [guía del backend](back/README.md#swagger-ui--openapi).

## 6. Cerrar sesión, detener la aplicación y conservar datos

**Cerrar sesión** devuelve la aplicación a la pantalla de acceso y borra las credenciales y la actividad de esa ventana. No deshace las correcciones que ya se realizaron en el backend.

La **actividad de sesión** es una lista temporal de la ventana del navegador. Se limpia al cerrar sesión o recargar. No equivale al registro de auditoría guardado en la base de datos.

Para detener los dos servicios iniciados con el comando de esta guía, vuelve a su ventana de PowerShell y pulsa **Ctrl + C**.

**La demostración guarda la base H2 en memoria: al detener el backend se pierden sus cambios, incluida la auditoría de esa ejecución. Al volver a iniciarlo, se cargan otra vez los datos ficticios originales.** Cerrar solo el navegador no detiene el backend.

## 7. Problemas frecuentes

| Problema | Qué revisar |
| --- | --- |
| El navegador no abre PagoClaro. | Confirma que el iniciador sigue abierto y que mostró «PagoClaro listo». Usa la dirección de la sección 2. |
| El comando no encuentra Java, Node.js o npm. | Comprueba las herramientas de la sección 3. Después de instalarlas, abre una nueva ventana de PowerShell. |
| El programa indica que los puertos 8080 o 5173 están ocupados. | Puede haber otra instancia encendida. Prueba primero la dirección del navegador. No inicies una segunda copia ni cierres procesos desconocidos. |
| No puedo iniciar sesión. | Comprueba usuario y contraseña. Si fueron personalizados, las credenciales de demostración ya no aplican. |
| Un pago no existe. | Copia una de las referencias de la sección 4, sin espacios adicionales. |
| Aparece «revisión manual». | Es un resultado previsto cuando la información no permite corregir con seguridad; revisa la explicación mostrada. |
| Swagger no aparece en 8080. | Una instancia antigua puede seguir ejecutándose sin la integración. No la reinicies si necesitas conservar sus datos; consulta la alternativa aislada de la guía del backend. |
| La primera preparación falla al descargar componentes. | Revisa la conexión a Internet y los permisos de red del equipo. |
| Se perdió la respuesta o hubo un tiempo de espera. | No asumas que el pago fue corregido ni que quedó sin cambios. Verifica su estado antes de volver a operar. |

Los archivos de diagnóstico del iniciador están en `front/.tools`. Si necesitas ayuda, comparte el mensaje de error sin incluir contraseñas.

## 8. Qué contiene cada carpeta

| Carpeta | Contenido explicado de forma sencilla |
| --- | --- |
| [`front/`](front/README.md) | La pantalla de acceso, el panel y el servidor que entrega la página al navegador. |
| [`back/`](back/README.md) | Las reglas de revisión de pagos, el control de acceso y la comunicación con la base de datos. |
| [`docs/`](docs) | El enunciado, las decisiones, las guías y las evidencias de las pruebas. |
| [`back/db/oracle/`](back/db/oracle) | Instrucciones para preparar y validar la base de datos Oracle. No se necesitan para la demostración con H2. |

## 9. Pruebas y documentación para la revisión técnica

Esta sección está dirigida a quien evalúe o mantenga el código. No necesitas ejecutar estas pruebas para usar una aplicación que ya está encendida.

Resultados registrados en las últimas revisiones de cada componente:

| Revisión | Resultado registrado |
| --- | --- |
| Backend con Swagger | 58 pruebas aprobadas, sin fallos ni errores; una suite Oracle omitida por falta de entorno. |
| Frontend: cliente y servidor intermediario | 21 pruebas aprobadas. |
| Frontend: navegador | 28 pruebas aprobadas. |
| Integración de la interfaz con Java real | Ocho conciliaciones verificadas. |
| Swagger en Chrome | Carga de la página, autorización y consulta de acceso correctas; solicitud anónima rechazada. |

Son ejecuciones documentadas en momentos distintos, no una única prueba conjunta. Esta edición del README no vuelve a ejecutar la aplicación. Las pruebas locales con H2 no certifican que los scripts Oracle funcionen en una base Oracle real; esa validación sigue pendiente. Tampoco se afirma una certificación completa de accesibilidad ni un despliegue listo para producción.

Para validar Java en una carpeta de compilación separada, desde `back`, con JDK 21 configurado:

```powershell
.\mvnw.cmd '-Dbuild.output-directory=.tools/readme-verify' verify
```

Para validar la interfaz, desde `front`:

```powershell
npm ci
npm test
npm run test:e2e
```

Las pruebas de navegador usan un servicio propio en el puerto `5174` y requieren Chrome, Edge o Chromium. No detengas la aplicación original para ejecutarlas. La preparación de Java y las pruebas completas se explican en las guías de cada componente.

| Documento | Qué explica |
| --- | --- |
| [Enunciado original](docs/01-enunciado/Prueba_Tecnica_Desarrollador_Soporte_Java_Candidato.pdf) | Lo solicitado en la prueba técnica. |
| [Guía del backend](back/README.md) | Ejecución de Java, rutas, seguridad, Swagger y pruebas. |
| [Guía del frontend](front/README.md) | Ejecución y pruebas de la interfaz. |
| [Modelo de datos](docs/03-base-de-datos/MODELO_DATOS.md) | Tablas y relaciones de la base de datos. |
| [Operación de soporte](docs/02-guias/OPERACION_N3.md) | Criterios para investigar y tratar inconsistencias. |
| [Auditoría del frontend](docs/04-validaciones/AUDITORIA_FINAL_FRONTEND.md) | Cambios, verificaciones visuales y límites de la revisión. |
| [Validación integral anterior a Swagger](docs/04-validaciones/VALIDACION_FINAL_ENTREGA.md) | Resultados y alcance de esa etapa de la entrega. |
| [Instalación y recuperación Oracle](docs/03-base-de-datos/ORACLE_INSTALACION_Y_RECUPERACION.md) | Preparación y operación de Oracle. |
| [Preparación de Git](docs/05-entrega/PREPARACION_GIT_ENTREGA.md) | Revisión histórica del repositorio y reproducción desde un clon. |
| [Uso de inteligencia artificial](docs/05-entrega/USO_IA.md) | Contribuciones, correcciones y límites del apoyo de IA. |
