# AstraYT
- [ ] 1. Scaffold, diseño completo y canal de release compilado.
- [ ] 2. Persistencia, extractor, selección de streams, captura y pruebas.
- [ ] 3. Motor de descarga, servicio, notificaciones y Órbita.
- [ ] 4. Biblioteca y Canal.
- [ ] 5. Reproductor, gestos, segundo plano y PiP.
- [ ] 6. Ajustes, bienvenida y actualizaciones.
- [ ] 7. Accesibilidad, R8 y validación final release.

Decisiones: identificador solicitado por el usuario `con.themralex.astrayt`; firma local persistente fuera del repositorio. Java 21 ejecuta Gradle; bytecode Java/Kotlin 17 para Android. No se publican releases sin validar la compilación.

## Validación del primer incremento
- `assembleRelease` completado con R8 y reducción de recursos; APK firmada RSA-4096 verificada con apksigner.
- `testReleaseUnitTest`: 12 pruebas ejecutadas, 0 fallos, 0 omitidas.
- Navegación, Room/FTS, DataStore, adaptador NewPipe, motor por rangos, MediaStore, notificaciones y Media3 implementados y compilados.
- Bloqueo externo: el proxy devuelve 403 a YouTube; no se ha validado extracción ni descarga reales. Dominios requeridos guardados en el borrador.
- Falta prueba en dispositivo. Las fases permanecen abiertas hasta completar su detalle funcional, accesibilidad y pulido del documento maestro; compilar no verifica toda la experiencia.
- Compatibilidad: las últimas bibliotecas que exigen SDK 37 se sustituyeron por las últimas versiones con minCompileSdk <= 36 según sus AAR publicados.
