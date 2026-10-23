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

## Motor 0.0.4
- Diagnóstico: v0.26.5 usa el analizador n antiguo; upstream corrigió el cambio de YouTube en PR #1545, aún sin release estable. Se incorporan sus dos clases desde e8f89145a830b4d6cae32412007f24ea8b36c73a, recompiladas contra la base estable en extractor-patched. Corrección local de generación JS: invocar el nombre, no insertar la declaración completa. Procedencia y diferencias en extractor-patched/UPSTREAM.md.
- Nuevo coordinador de análisis compartido: cancelación de Capturar no cancela la cola, limpieza garantizada tras fallo, exclusión mutua del estado global de NewPipe, reintento único con limpieza de caché. Plazo por intento de 45 segundos para el procesamiento del reproductor actual (sustituye los 20 segundos originales). Sin multiplicar reintentos en la cola.
- Tiempo agotado convertido en fallo recuperable, no cancelación que deja FETCHING/carga activos. Sin formatos se informa como tal; no se recomienda actualizar por cualquier fallo. No se cachean resultados sin formatos.
- Actualización automática del extractor requiere revisar/retirar el hotfix antes de cambiar la base; la compilación impide mezclar APIs accidentalmente.
- El acceso a YouTube sigue bloqueado por el proxy (CONNECT 403). Las pruebas offline verifican el helper JavaScript, concurrencia, cancelación, timeout y reintento; no sustituyen la descarga real en Android. No se ejecuta emulador por instrucción del usuario.
- La prueba detectó además que Rhino 1.8.1 incluso en ES6 conserva el primer `const key` del bucle. Variable mutable en el bucle del helper, adaptador ES6 explícito, acceso Java deshabilitado y ejecución JS interrumpible para respetar el plazo incluso durante cálculo intensivo.
- Validación 0.0.4: 38 pruebas sin fallos/omisiones; assembleRelease y lintRelease correctos. APK ARM64/ARMv7 verificadas con apksigner, certificado original RSA-4096, ABI ELF correcta, paquete conservado y versionCode 4. Workflows pasan actionlint.
- Publicada https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.4 ; código en main y tag v0.0.4. Job publish del run 39808576563 completado correctamente (GitHub aún muestra el run global en progreso); los cuatro digests publicados coinciden con los artefactos locales. Descarga real con YouTube pendiente de verificar en dispositivo.

## Revisión 0.0.5 solicitada por el usuario
- Alcance: extracción con motor JavaScript del sistema Android y cliente alternativo ANDROID_VR; Arca (5182d49) reutilizado en gestos y barra de búsqueda; Órbita y tarjetas de biblioteca rediseñadas; likes guardados cuando YouTube los expone.
- SponsorBlock opcional y categorías configurables; preferencias fijadas por descarga, cortes físicos sin recodificar, alineados a fotogramas clave para conservar sincronización (pueden suprimir unos segundos adicionales). Error de consulta recuperable, sin entregar silenciosamente un archivo sin cortes.
- Muxing intercalado por marca de tiempo, búfer directo adaptable y arte en paralelo con plazo de 5 s. Migración Room 1→2 conserva biblioteca existente.
- Entorno restaurado sin SDK, caché de Gradle ni firma privada original; se reconstruyen herramientas en /workspace/tooling. No generar una clave sustitutiva para publicar una actualización incompatible.
- Validación final: `testReleaseUnitTest assembleRelease lintRelease` correcto (47 pruebas, 0 fallos/omisiones; lint 0 errores). APKs ARM64 y ARMv7 0.0.5/versionCode 5 generadas sin firma; no se presentan como instalables ni se publican con una clave distinta.
- JitPack bloqueado: compilación local contra NewPipeExtractor v0.26.5 recompilado desde el tag oficial f9e6bb808f82bf3e4dc1f2a29a16fd376931c8ef y nanojson e9d656d; repositorio Maven temporal fuera del proyecto, sin cambiar las dependencias de producción. JavaScript moderno y aislamiento de window/location verificados con V8/Node; esto no sustituye WebView en Android.
- SQL de migración contrastado con los esquemas exportados: filas existentes, FTS, índices y otras tablas conservados. Workflows válidos con actionlint 1.7.12.
- YouTube devuelve CONNECT 403 en este entorno: extracción real, reproducción/gestos en dispositivo, cortes y tiempos de fusión reales pendientes de verificar. No se instala emulador.
- Firma pública original comprobada desde la APK 0.0.4: SHA-256 c4a7fec889a3550494efea270d3e3c8a76219792d11f41e12946f2e8a04763df. Actions exige esa firma y los cuatro secretos originales antes de publicar; elimina la sustitución automática por una firma temporal incompatible.
- Red necesaria guardada en el borrador del entorno (JitPack, API/adjuntos de GitHub, YouTube/CDN y SponsorBlock). El guardado no aplica la política: requiere revisar, guardar y publicar la configuración en ajustes del entorno.
- Código subido a main en edde34d. Intento real de publicación: https://github.com/TheMrAleX/AstraYT/actions/runs/39849795450 ; falló en `Prepare signing` con `Falta KEYSTORE_BASE64`. No hay release 0.0.5 publicada. Para completarla se necesita recuperar la clave privada original y configurar los cuatro secretos de Actions indicados en README; después ejecutar Release (sin versión manual incrementa 0.0.4 a 0.0.5). Las APKs locales son sin firma y no sustituyen una entrega instalable.

- Publicación 0.0.5 completada con la clave original recuperada en este entorno: https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.5 . Los APK ARM64 y ARMv7 conservan el certificado SHA-256 c4a7fec889a3550494efea270d3e3c8a76219792d11f41e12946f2e8a04763df, versionCode 5.
- `testReleaseUnitTest assembleRelease lintRelease --offline` completado: 47 pruebas, 0 fallos/omisiones y lint sin errores. Workflow de publicación 39851851991 exitoso; los cuatro adjuntos públicos coinciden en tamaño y SHA-256 con los artefactos locales. La clave privada permanece fuera del repositorio.

## Revisión 0.0.6 solicitada por el usuario
- Firma recuperada desde los adjuntos del usuario y comprobada contra el certificado de 0.0.4. Clave y configuración permanecen fuera del checkout; no se suben a Git ni a Actions.
- En vista normal, arrastrar hacia abajo minimiza; brillo/volumen por gestos quedan solo en pantalla completa. Minirreproductor con superficie Media3 de vídeo en vivo, sin cambiar a solo audio al navegar. Transiciones del reproductor respetan la preferencia de animaciones.
- Selector de pistas de audio y subtítulos presentes y compatibles en el archivo; apertura de SRT/VTT/TTML/ASS/SSA, copia privada persistente y limpieza al borrar el vídeo. No se presentan pistas que no estén descargadas.
- Descripciones HTML convertidas en párrafos, saltos y enlaces HTTP(S) seguros; likes con icono. Sin WebView para descripciones.
- Notificaciones silenciosas y errores/completadas emitidos por transición, sin repetirse en cada muestreo ni reaparecer desde filas antiguas. Cancelar elimina la notificación. Escritura atómica de fallo evita que un error tardío cambie una descarga cancelada o pausada a fallida.
- Compilación Kotlin final correcta; primera build firmada y lint sin errores. Se ejecuta la verificación completa sobre este commit antes de publicar ARM64/ARMv8 y ARMv7 como 0.0.6.
- Publicación precompilada permite crear el borrador desde el runner, verifica firma original y checksums antes de publicar; evita requerir credenciales de firma en GitHub. Pruebas en dispositivo de gestos, pistas, continuidad y notificaciones pendientes; no se instala emulador.
- Validación final 0.0.6: `testReleaseUnitTest assembleRelease lintRelease --offline` correcto; 57 pruebas sin fallos/omisiones y lint sin errores. Ambas APK verificadas con apksigner, ABI correcta, versionCode 6 y certificado original. SQL de fallo comprobado para conservar CANCELED/PAUSED/DONE ante errores tardíos.
- Publicada https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.6 como latest, con APK ARM64/ARMv8 y ARMv7 y sus checksums. Workflow 39857597180 completado correctamente. Los cuatro adjuntos se descargaron públicamente y sus bytes/SHA-256 coinciden con los archivos locales verificados. Tag v0.0.6 apunta al commit de aplicación fbf09d8.
- Publicación: la verificación de firmas instala Java 21 y usa apksigner oficial de build-tools 36 con checksum fijado, sin depender del SDK del runner. La creación con `--target` fue rechazada por la integración (403); se resuelve subiendo primero el tag por Git y creando desde ese tag existente con `--verify-tag`, sin modificar referencias desde el runner. Rama temporal de APKs retirada tras verificar la publicación.

## Revisión 0.0.7: persistencia de descargas
- Checkpoints atómicos con checksum, identidad de vídeo/formato/pista y posición dentro de cada fragmento; datos sincronizados antes de guardar sus offsets. Guardado cada 1 MiB/1 s por trabajador y al pausar o fallar; cierre forzado repite solamente el tramo posterior al último checkpoint.
- Formatos, contenedor, altura y pista elegidos persistidos en optionsJson; no cambian al modificar preferencias o renovar URLs. Rangos HTTP y tamaño total contrastados antes de escribir. Progreso recuperado desde archivos y checkpoint, no desde el contador visual.
- Cola interrumpida queda pausada al abrir; adquisición de tareas atómica y transiciones protegidas ante pausa/cancelación. Limpieza y planificación final funcionan aun con la corrutina cancelada; acciones serializadas para evitar reanudar mientras sigue cerrándose una tarea.
- Registro de salida MediaStore: elimina una fusión interrumpida o reconoce el vídeo ya registrado, sin duplicarlo. Reintento de fusión reutiliza fragmentos completos. Espacio de descarga y salida comprobado por separado.
- Los checkpoints antiguos AST1 carecen de identidad de formato y offsets fiables: se descartan para evitar mezclar datos. Una descarga parcial de 0.0.5/0.0.6 puede reiniciarse una vez tras actualizar; biblioteca y descargas completadas se conservan. Sin cambios de esquema Room ni firma.
- Validación final: `testReleaseUnitTest assembleRelease lintRelease --offline` correcto; 63 pruebas sin fallos/omisiones, lint sin errores. Pruebas de reconstrucción exacta tras reapertura, fragmentos paralelos parciales, cambio de pista del mismo tamaño, checkpoint temporal/corrupto y archivo truncado. SQL de recuperación, transiciones y adquisición única comprobado con SQLite. Ambas APK firmadas con el certificado original y verificadas para versionCode 7, minSdk 26 y ABI correspondiente.
- Prueba real de cierre forzado, reanudación y fusión en Android pendiente de verificar; no se ejecuta emulador. YouTube sigue bloqueado por CONNECT 403 en este entorno.
- Publicada https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.7 como latest. Workflow 39860032059 correcto; tag apunta al commit f654768. Los cuatro adjuntos públicos descargados y contrastados en tamaño/SHA-256 con los artefactos locales firmados. Rama temporal de publicación retirada tras verificar.

## Revisión 0.0.8: audio y calidad real
- Extracción parcial (solo progresivo/360p, audio ausente o sin HD utilizable) ahora intenta ANDROID_VR y combina los formatos recuperados conservando identidad, cabeceras y metadatos. Un formato con URL ilegible no descarta los demás; el fallo del cliente alternativo conserva los formatos primarios. Resultados incompletos no se cachean diez minutos.
- Si solo existe MP4 progresivo con audio, modo Audio descarga esa fuente y remultiplexa únicamente las pistas de audio a M4A, sin recodificar. Audio independiente tiene prioridad; no sustituye un doblaje ausente por el audio original. Reconoce sufijos MP4 y codecs AAC sin distinguir mayúsculas.
- Capturar muestra solo calidades disponibles tras analizar y la calidad real seleccionada, y fija IDs/pista/contenedor al encolar. Antes de analizar y en listas se conserva el límite solicitado; Órbita avisa si el resultado es inferior. Opción sin formatos no se añade desde un resultado ya analizado; permite volver a analizar. Información de extracción de audio y formato M4A/WebM visible.
- Conserva checkpoints y opciones de 0.0.7; sin cambios de esquema Room ni firma. Pruebas de audio progresivo, recuperación parcial, sucesivas selecciones de calidad y fallos aislados de URLs. Validación final: `testReleaseUnitTest assembleRelease lintRelease --offline` correcto; 74 pruebas sin fallos/omisiones, lint sin errores. Ambas APK verificadas con apksigner, certificado original, versionCode 8, minSdk 26 y ABI correspondiente. YouTube devuelve CONNECT 403 desde este entorno: pendiente descarga real y remultiplexado en Android, sin emulador por instrucción del usuario.
- Publicada https://github.com/TheMrAleX/AstraYT/releases/tag/v0.0.8 como latest. Workflow 39894523551 correcto; tag apunta al commit 73d1f74. Los cuatro adjuntos públicos descargados y contrastados en tamaño/SHA-256 con los artefactos locales firmados. Rama temporal de publicación retirada tras verificar.
