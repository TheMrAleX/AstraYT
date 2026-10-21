# AstraYT
Aplicación Android nativa en Kotlin y Compose. Android 8.0 o posterior.
Paquete: `con.themralex.astrayt`. Interfaz de agujero negro, español e inglés.
Compilar: `./gradlew assembleRelease`; pruebas: `./gradlew testReleaseUnitTest`.
La firma local lee `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
GitHub Actions usa cuatro secretos: `KEYSTORE_BASE64` (archivo codificado),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
Conserva la clave: Android exige la misma firma para instalar actualizaciones.
Sin secretos, CI genera una firma temporal y advierte que no permite actualizar.
Un push a main genera la siguiente release; la ejecución manual acepta una versión.
APKs: `AstraYT-vVERSION-arm64-v8a.apk` y `AstraYT-vVERSION-armeabi-v7a.apk`, con SHA-256.
El extractor se comprueba semanalmente y solo se actualiza si compila.
GPL-3.0; incluye NewPipeExtractor. Consulta LICENSE.
Usa contenido propio, libre o con permiso y respeta los términos de YouTube.
El avance y las limitaciones verificadas se registran en PROGRESS.md.
