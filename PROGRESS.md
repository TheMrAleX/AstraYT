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

## Cierre de este incremento
- Compilación y pruebas mediante el wrapper verificado: `./gradlew testReleaseUnitTest assembleRelease lintRelease` completado con éxito.
- 16 pruebas ejecutadas, 0 errores/fallos/omisiones. Incluyen persistencia del bitmap y rechazo de parciales de longitud incorrecta.
- Lint completo sin errores. Quedan advertencias de versiones más nuevas incompatibles con SDK 36 y recomendaciones no bloqueantes.
- Workflows validados con actionlint 1.7.12; acciones fijadas a SHAs consultados en sus repositorios oficiales.
- Biblioteca con Paging 3, FTS, cuadrícula adaptable, selección múltiple y eliminación; listas por páginas hasta 200 entradas; modo solo audio y PiP preparados.
- Firma local persistente fuera del checkout en `/workspace/private/astrayt`. No incluir esos archivos en commits ni mostrar sus valores.
- `install_script`, `start_skill` y dominios necesarios guardados en el borrador del entorno. El script de instalación se ejecutó con éxito y mantuvo la clave existente.

## Pendiente para cumplir íntegramente agent.md
- Pruebas reales de extracción, transferencia, muxing, reproducción y recuperación en Android. El contenedor no tiene emulador y el proxy bloquea YouTube con HTTP 403.
- Acabado exacto del catálogo visual: animaciones ambientales, bordes de enfoque, transiciones, gestos de hojas y navegación que se oculta al desplazar.
- Bienvenida mediante pager horizontal, detección de portapapeles al reanudar y refinamientos del reproductor, incluida la rotación bloqueable.
- Miniaturas en todas las notificaciones activas y revisión completa de cada caso de error y accesibilidad en dispositivo.
- Publicación del código/primera release en GitHub y prueba de restauración en una tarea nueva. Los commits existen localmente; no se ha verificado la restauración de commits locales desde un snapshot.

Las casillas de las siete fases permanecen abiertas porque representan la especificación completa. Esta entrega es una primera versión compilada y firmada, no una declaración de que todos sus detalles estén terminados.

## Publicación 0.0.1 solicitada por el usuario
- Paquete conservado: `con.themralex.astrayt`; versión inicial publicada prevista: `0.0.1`, versionCode `1`.
- Splits de release para `arm64-v8a` (ARMv8 de 64 bits) y `armeabi-v7a` (ARMv7 de 32 bits), sin APK universal.
- Releases futuras: incremento semántico de parche o versión manual; ambos APKs y sus checksums.
- La primera subida omite CI para publicar los binarios ya compilados con la clave local y evitar una segunda firma automática.
- La API de secretos de Actions devuelve 403; no se han leído ni sustituido secretos del repositorio. La firma de esta release se conserva localmente.
- El comprobador de actualizaciones selecciona la APK según `Build.SUPPORTED_ABIS`; incluye cinco pruebas para ARM64, ARMv7, x86, APK universal antigua y checksums.
- Publicación de adjuntos mediante `publish-prebuilt.yml` desde una rama temporal: el proxy bloquea `uploads.github.com`. El runner verifica checksums antes y después de subir y publica el borrador con la firma local intacta.
- Validación de 0.0.1 completada: `testReleaseUnitTest assembleRelease lintRelease`, 21 pruebas aprobadas, 0 fallos/omisiones y lint sin errores. Ambos APKs verificados con apksigner y ELF (AArch64/ARM), misma firma RSA-4096.
