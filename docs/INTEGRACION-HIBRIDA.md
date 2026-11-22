# Integración híbrida — La Clave

## Base y estado comprobado en el código

Este repositorio es un fork público del proyecto Android [Agent](https://github.com/Past-da-king/agent), licencia MIT, en la rama de trabajo `zapia/hybrid-integration`. Se usa Agent como base del asistente (chat/modelos, memoria, web, rutinas y herramientas).

Agent ya incluye un controlador de apps por «pantalla de fondo»: el modelo puede abrir una app en una pantalla aislada, leer los controles, tocar, escribir, deslizar y volver a leer. Las herramientas `screen_open_app`, `screen_look`, `screen_tap`, `screen_type`, `screen_swipe` y otras se registran en `AgentRuntime` **solo si la persona activa la función**. El interruptor está apagado por defecto. La implementación concentra las reglas de seguridad en `ScreenSession`.

El modo de control de pantalla visible toma como referencia la experiencia de [Mika](https://github.com/wangzan101/Mika), licencia Apache-2.0. Se añadió un servicio independiente de Accesibilidad escrito para este fork; no se copió código de Mika. La persona debe habilitarlo manualmente en los ajustes de Android; solo entonces se exponen las herramientas `mika_screen_read`, `mika_screen_open_app`, `mika_screen_tap` y `mika_screen_type`. El modo lee la pantalla activa bajo demanda, oculta el texto de campos de contraseña, no almacena eventos ni capturas, mantiene bloqueados los bancos y las billeteras no habilitadas, deja las billeteras opt-in solo lectura, descarta IDs viejos cuando cambia la pantalla y pide aprobación antes de botones sensibles. La pantalla aislada de Agent sigue como segundo modo independiente; ninguno se activa solo.

## Requisitos conocidos / pendientes de comprobar

- El módulo de pantalla de fondo requiere Android 11 o posterior; la app base declara Android 9 como mínimo.
- El helper de pantalla necesita permiso de shell: iniciar Shizuku y autorizar Agent, o ejecutar el comando ADB que muestra el asistente de configuración. Agent documenta la ruta **Settings → Background screen** y presenta los pasos cuando hacen falta.
- El ZTE Blade A55 concreto de Daniel no fue probado. Android 14 sería compatible por versión, pero faltan la configuración de Shizuku/ADB para el modo aislado y la comprobación del servicio de Accesibilidad sobre la pantalla visible en ese teléfono.

## Límites y controles que se conservan

- La pantalla de fondo se activa manualmente y queda apagada por defecto. No cambiamos esa decisión ni solicitamos permisos al instalar.
- Bancos, billeteras, pagos y cripto quedan bloqueados por defecto. Hay una excepción manual, apagada por defecto y limitada a los paquetes oficiales exactos de Prex Argentina (`air.PrexArgentina`) y Binance (`com.binance.dev`): la pantalla de fondo solo puede abrirlas y leerlas. El agente no puede tocar, escribir, deslizar, usar teclas ni compartir contenido en esas billeteras. Al apagar un permiso se cierra la sesión de pantalla de fondo para descartar enseguida la autorización anterior. Para interactuar, Daniel toma el control manualmente; ninguna operación financiera se automatiza.
- En las demás apps, enviar, pagar, publicar o borrar sigue pidiendo aprobación de la persona. Esta excepción no cambia el bloqueo de las otras apps financieras, no desinstala nada y no habilita actividad financiera fuera de la pantalla aislada.
- La persona puede detener la tarea o tomar el control en cualquier momento.
- Si una app se abre en la pantalla que está usando la persona, el controlador se niega a manipularla y procura devolver el foco.
- No almacenar claves en GitHub ni generar subidas de APK como parte de la CI.

## Validación

- Una compilación/CI no verifica la función en el teléfono.
- La CI enfocada pasó las pruebas de bloqueo financiero, aprobación, detención y consulta de Prex y Binance en modo de solo lectura. La ejecución valida el código y pruebas con teléfono simulado; no verifica interacción real con aplicaciones de billetera.
- El servicio nuevo de Accesibilidad para el modo visible y las herramientas ya están en la rama y compilan junto con la app; sus permisos y acciones de UI todavía requieren pruebas dedicadas y luego confirmación de Daniel en el Blade A55. No se ha probado el servicio en un teléfono real.
- Esta rama es de desarrollo; `main` conserva la base upstream sin cambios propios hasta revisar y aprobar el trabajo.
