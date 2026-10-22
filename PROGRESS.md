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

## Publicación completada en GitHub
- Código subido a `TheMrAleX/AstraYT`, rama `main`, tag `v0.0.1`.
- Release publicada: https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.1
- Incluye APK ARM64/ARMv8 y ARMv7 con SHA-256, ambas versión 0.0.1 y firmadas con la clave local original.
- Workflow `Publish verified APKs` completado correctamente; archivos descargados y verificados por el runner. Digests publicados por GitHub coinciden con los cuatro archivos locales.
- La subida inicial del borrador rechazó un commit abreviado; se corrigió con el SHA completo y la reejecución terminó correctamente.
- La rama temporal de artefactos se retira tras la publicación para mantener el trabajo futuro en main.
- Las menciones anteriores a commits solo locales o a la primera publicación pendiente son históricas. La restauración en una tarea nueva y las pruebas reales en Android/YouTube siguen sin verificarse.

## Corrección 0.0.2: formatos vacíos y captura sin bloquear
- Las capturas del usuario muestran metadatos disponibles, opciones vacías y botón deshabilitado. Sin acceso funcional a YouTube/dispositivo no se ha confirmado la causa concreta de esa extracción.
- Opciones de vídeo/audio y calidades comunes 144/240/360/480/720/1080/1440/2160 visibles con un enlace de vídeo válido, incluso mientras se analiza o si falla el análisis.
- La selección es un límite solicitado, independiente de los formatos recibidos. El análisis no la sobrescribe; la cola selecciona el máximo compatible hasta ese límite, o el mínimo disponible si no existe ninguno inferior.
- Encolar guarda ID/enlace, calidad y modo aunque no haya metadatos; el análisis continúa mediante el backend compartido y se completa en FETCHING con la concurrencia de la cola. Las listas también se encolan sin analizar cada entrada en Capturar.
- La cola espera red antes de analizar, vuelve a extraer hasta dos veces si no hay formatos compatibles, actualiza título/miniatura y muestra un error de extractor con reintento cuando sigue sin formatos.
- No se cachean resultados sin audio/vídeo; recuperación de altura/FPS desde la etiqueta de resolución y de compatibilidad AVC/VP9 mediante itags conocidos cuando falta códec. Los códecs desconocidos/AV1 no se tratan como AVC.
- Una renovación de URL durante la transferencia conserva los IDs y el contenedor para evitar mezclar formatos entre fragmentos. JSON de la cola antigua compatible, sin migración de Room.
- Decisión: usar la misma firma local y publicar 0.0.2 para ambas arquitecturas, manteniendo actualizaciones sobre 0.0.1.
- Corrección adicional del planificador: si falta AAC para H.264, intenta VP9/Opus de esa calidad en vez de descartarla. Audio prioriza M4A y permite WebM/Opus sin recodificar cuando falta M4A (MIME/extensión correctos); evita bloquear audio compatible.
- Verificación de lógica: 30 pruebas aprobadas, sin fallos/omisiones; lintRelease sin errores. Compilación Kotlin de release correcta; empaquetado final y firma se verifican antes de publicar.
- Verificación final 0.0.2: `testReleaseUnitTest assembleRelease lintRelease` completado; 30 pruebas aprobadas, 0 fallos/omisiones, lint sin errores. Ambos APKs RSA-4096 con certificado original, ELF ARM/AArch64, paquete con.themralex.astrayt, minSdk 26, versionCode 2.
- Release publicada: https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.2 ; los cuatro digests de GitHub coinciden con los archivos locales. Workflow de publicación 39638812943 correcto. Descarga real en Android/YouTube y causa exacta de la extracción vacía de las capturas pendientes de verificar.

## Rediseño 0.0.3 solicitado por el usuario
- Dirección visual registrada en AGENTS.md: superficies carbón, tipografía con jerarquía, acento ámbar contenido y agujero negro de líneas finas; sustituye la receta visual anterior por petición expresa.
- Rehechos tema, botones, tarjetas, campos, segmentos, selección de calidad, navegación, listas, ajustes, hojas y controles de reproducción. Captura con formulario compacto, cuadrícula 144p–4K y acción independiente; Órbita con miniaturas y progreso; biblioteca más limpia y minirreproductor compacto.
- Navegación y minirreproductor ocupan su altura medida, sin márgenes inferiores fijos. Roles accesibles en pestañas/selecciones y opciones de audio/cola de 0.0.2 conservadas.
- No se instala ni se ejecuta emulador: la revisión visual en Android corresponde al usuario. Se valida compilación release, pruebas existentes, lint y firma.
- Validación 0.0.3: `testReleaseUnitTest assembleRelease lintRelease --offline` correcto, 30 pruebas sin fallos/omisiones, lint sin errores. Dos APKs ARM64/ARMv7 verificados con el certificado original, ABI correcta y versionCode 3.
- Publicado código y release https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.3 ; workflow 39708383462 correcto y los cuatro digests publicados coinciden con los artefactos locales. Revisión visual en emulador pendiente del usuario.
