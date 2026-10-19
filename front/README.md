# PagoClaro — consola web de soporte

Interfaz en español para conciliar pagos con la API Spring Boot de `../back`. HTML, CSS y módulos JavaScript nativos; servidor/proxy Node sin dependencias de producción. No necesita compilación ni recursos externos.

## Iniciar todo desde la raíz

```powershell
powershell -ExecutionPolicy Bypass -File .\front\scripts\start-local.ps1
```

Abre **http://127.0.0.1:5173/login**. Introduce usuario y contraseña y pulsa **Iniciar sesión**. El backend valida las credenciales y el rol SUPPORT mediante `GET /api/support/session`, sin leer ni modificar pagos. Solo después se muestra `/dashboard`, donde puedes introducir la referencia y pulsar **Conciliar pago**. La pantalla inicial no muestra referencias ni credenciales de ejemplo.

**Cerrar sesión** elimina credenciales en memoria, referencia, resultado, contadores y actividad local. Un 401/403 durante una operación también vuelve al login y limpia esos datos. Al recargar se requiere iniciar sesión nuevamente, porque no se persisten credenciales ni un indicador de acceso en almacenamiento del navegador.

El iniciador requiere Node 22+ y Java 21. En este workspace encuentra el JDK portátil de `back/.tools`; en otros equipos admite JAVA_HOME o java en PATH. Si no existe el JAR ejecuta la compilación/pruebas de Java. `-Build` fuerza esa compilación. `-Check` inicia ambos servicios, verifica HTTP 200/401 sin modificar pagos y los cierra. Ctrl+C detiene los procesos iniciados por el script. Los puertos 8080 y 5173 deben estar libres.

El iniciador selecciona el perfil **local H2**. Los datos de muestra se reinician al detener Java. Las credenciales locales predeterminadas son `n3` / `demo-change-me`; son exclusivas del laboratorio y se documentan aquí, sin incorporarlas a los archivos públicos de la consola. Si SUPPORT_USER o SUPPORT_PASSWORD fueron personalizados, ingresa esos valores.

## Iniciar solo el frontend

Con el backend iniciado en 8080:

```powershell
cd front
npm start
```

Node sirve la interfaz en 5173 y reenvía el GET de `/api/support/session` y los POST de `/api/support/payments/{reference}/reconcile` al backend. No se requiere CORS. BACKEND_URL permite configurar otro origen HTTP(S) sin ruta ni credenciales; PORT cambia el puerto de la interfaz. El servidor escucha únicamente en loopback. Es un servidor de desarrollo/evaluación local; no hay publicación ni despliegue productivo.

## Funciones

- Referencia manual validada, sin listado ni valores precargados de pagos.
- Login con validación real del rol de soporte y cierre de sesión; credenciales solo en memoria, sin localStorage, sessionStorage ni cookies de autenticación.
- Corrección, pago ya consistente y revisión manual con mensajes distintos.
- Débito neto anterior/final, movimientos creados y nueva auditoría según la respuesta real.
- Prevención de envíos duplicados durante una operación y ausencia de reintentos automáticos.
- Actividad de hasta 30 operaciones en esta ventana, con contadores de sesión. No es la auditoría persistida.
- Mensajes para acceso inválido, referencia inexistente, bloqueo, indisponibilidad y timeout; una respuesta perdida no se presenta como una corrección confirmada.
- Login y dashboard adaptables a escritorio y móvil, navegación por teclado, mostrar/ocultar contraseña y regiones de resultado anunciadas.

Los importes se muestran con dos decimales en formato es-CO, sin inventar una moneda que el modelo no declara. Los tokens monetarios del JSON se conservan como cadenas para mostrar NUMBER(18,2) sin redondeo de punto flotante. Se requiere un navegador moderno; si un navegador antiguo no puede preservar un importe grande, se rechaza la presentación del resultado en lugar de mostrar un valor redondeado. Las fechas de sesión usan America/Bogota.

## Carpetas

```text
index.html              Estructura semántica de la consola
styles.css              Diseño y adaptación por tamaño
src/app.js              Interacciones, estado y renderizado seguro
src/api.js              Cliente HTTP, autenticación y errores
src/presentation.js     Contrato, etiquetas y formato de importes
server.mjs              Archivos públicos y proxy restringido
scripts/start-local.ps1 Iniciador de frontend + backend
tests/*.test.mjs        Pruebas unitarias y del proxy
tests/browser/          Pruebas de interfaz con Playwright
```

## Validar

Desde `front`:

```powershell
npm ci
npm test
npm run test:e2e
```

Las pruebas del navegador detectan Chrome/Edge en las rutas habituales de Windows. En otro entorno configura CHROME_PATH o instala Chromium con `npx playwright install chromium`. Las pruebas e2e abren su propio servidor en 5174 y simulan respuestas para aislar los escenarios; no necesitan Java.

Resultados: **17 pruebas de cliente/proxy y 14 de navegador aprobadas**. Además se verificaron conciliaciones y el flujo login → dashboard → cierre de sesión desde Chrome contra Spring Boot real. Evidencias y límites: [docs/FRONTEND.md](../docs/FRONTEND.md).
