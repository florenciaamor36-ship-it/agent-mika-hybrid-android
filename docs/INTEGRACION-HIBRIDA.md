# Integración híbrida — La Clave

## Base y estado comprobado en el código

Este repositorio es un fork público del proyecto Android [Agent](https://github.com/Past-da-king/agent), licencia MIT, en la rama de trabajo `zapia/hybrid-integration`. Se usa Agent como base del asistente (chat/modelos, memoria, web, rutinas y herramientas).

Agent ya incluye un controlador de apps por «pantalla de fondo»: el modelo puede abrir una app en una pantalla aislada, leer los controles, tocar, escribir, deslizar y volver a leer. Las herramientas `screen_open_app`, `screen_look`, `screen_tap`, `screen_type`, `screen_swipe` y otras se registran en `AgentRuntime` **solo si la persona activa la función**. El interruptor está apagado por defecto. La implementación concentra las reglas de seguridad en `ScreenSession`.

La idea de operar apps en el teléfono está también en [Mika](https://github.com/wangzan101/Mika), licencia Apache-2.0. No copiamos código de Mika a este fork: el controlador equivalente ya existe en Agent, y mezclar dos motores de pantalla distintos agregaría riesgo y complejidad sin una función demostrada. Si una prueba revela una carencia concreta, se evaluará incorporar solo el componente necesario y conservar su `LICENSE` y `NOTICE`.

## Requisitos conocidos / pendientes de comprobar

- El módulo de pantalla de fondo requiere Android 11 o posterior; la app base declara Android 9 como mínimo.
- El helper de pantalla necesita permiso de shell: iniciar Shizuku y autorizar Agent, o ejecutar el comando ADB que muestra el asistente de configuración. Agent documenta la ruta **Settings → Background screen** y presenta los pasos cuando hacen falta.
- El ZTE Blade A55 concreto de Daniel no fue probado. Android 14 sería compatible por versión, pero faltan la configuración de Shizuku/ADB y la comprobación de que el fabricante permite la pantalla secundaria.

## Límites y controles que se conservan

- La pantalla de fondo se activa manualmente y queda apagada por defecto. No cambiamos esa decisión ni solicitamos permisos al instalar.
- Las apps de bancos/dinero están bloqueadas por el módulo; enviar, pagar, publicar o borrar requiere aprobación de la persona. La persona puede detener la tarea o tomar el control.
- Si una app se abre en la pantalla que está usando la persona, el controlador se niega a manipularla y procura devolver el foco.
- No almacenar claves en GitHub ni generar subidas de APK como parte de la CI.

## Validación

- Una compilación/CI no verifica la función en el teléfono.
- Próximo paso: correr pruebas unitarias en CI sin guardar APKs; después Daniel tendrá que confirmar la instalación y el setup en el Blade A55 antes de afirmar compatibilidad real.
- Esta rama es de desarrollo; `main` conserva la base upstream sin cambios propios hasta revisar y aprobar el trabajo.
