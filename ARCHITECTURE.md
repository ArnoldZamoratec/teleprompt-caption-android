# GlassPrompt — Arquitectura técnica

> Teleprompter + grabación + subtítulos automáticos + exportación, con un design system "Liquid Glass" propio.
> Package: `com.arnoldcode.glassprompt` · Documento de la **Fase 1** · Fecha: 2026-09-24

---

## 1. Análisis del entorno (verificado)

| Elemento | Estado en esta máquina |
|---|---|
| Directorio del proyecto | `C:\Users\Arnole\Desktop\TECAP` — vacío, sin git. Proyecto desde cero. |
| JDK del sistema | Oracle JDK 17.0.14 (`JAVA_HOME` no definido) |
| JDK recomendado | JBR 21.0.9 de Android Studio 2025.3.1 (`C:\Program Files\Android\Android Studio\jbr`) |
| Android Studio | 2025.3.1 y 2026.1.3 instalados |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` — platforms 34, 35, 36, 36.1, 37.0 · build-tools hasta 37.0.0 · NDK y CMake presentes |
| Emulador | AVD `Pixel_5` (imagen `android-37.2-beta3`, google_apis_ps16k x86_64) |
| Gradle | No hay en el PATH. Wrappers en caché: 9.3.1, 9.5.0, 9.6.1 (versión actual publicada: 9.8.0) |

Decisiones derivadas:
- El build usa el **Gradle Wrapper** (no depende de una instalación global). El JDK se elige con `JAVA_HOME` apuntando al JBR 21 de Android Studio (Android Studio lo usa automáticamente). No queda fijado en `gradle.properties` para que el repositorio sea portable.
- Se generará `local.properties` con `sdk.dir`.
- Las pruebas instrumentadas corren en el AVD `Pixel_5`. Como su imagen es beta, la prueba final de cámara y micrófono se recomienda hacerla en un dispositivo físico.

---

## 2. Stack y versiones

Todas las versiones se consultaron el 2026-09-24 en Google Maven (`dl.google.com/android/maven2`) y Maven Central, y son **las últimas estables** publicadas. La compatibilidad entre ellas se confirma compilando en la Fase 2. Si alguna combinación falla, se baja a la estable anterior y queda anotado aquí.

| Área | Librería | Versión |
|---|---|---|
| Build | Android Gradle Plugin | 9.4.1 |
| Build | Gradle Wrapper | 9.8.0 |
| Lenguaje | Kotlin (+ plugin Compose Compiler) | 2.4.20 |
| Codegen | KSP (KSP2, versionado independiente) | 2.3.12 |
| UI | Compose BOM | 2026.09.00 |
| UI | Material 3 | vía BOM |
| UI | material-icons-extended | 1.7.8 (R8 elimina los íconos no usados) |
| UI | activity-compose | 1.13.0 |
| UI | core-splashscreen | 1.2.0 |
| UI (blur/vidrio) | Haze `haze`, `haze-blur`, `haze-glass` (`dev.chrisbanes.haze`) | 2.0.0 — aprobado, ver §6.3 |
| Arquitectura | lifecycle-runtime-compose / viewmodel | 2.11.0 |
| Navegación | navigation-compose (rutas tipadas) | 2.10.2 |
| Serialización | kotlinx-serialization-json | 1.11.0 |
| Async | kotlinx-coroutines | 1.11.0 |
| DI | Hilt (dagger) | 2.60.1 |
| DI | androidx.hilt (navigation-compose, work) | 1.4.0 |
| Persistencia | Room (runtime, ktx, compiler, gradle-plugin) | 2.8.5 |
| Persistencia | DataStore Preferences | 1.2.1 |
| Cámara | CameraX (core, camera2, lifecycle, video, compose) | 1.6.2 |
| Multimedia | Media3 (exoplayer, ui-compose, transformer, effect) | 1.11.1 |
| Background | WorkManager | 2.12.0 |
| Core | core-ktx | 1.19.1 |
| Test | JUnit 4 / androidx.test.ext:junit | 4.13.2 / 1.3.0 |
| Test | Turbine / MockK / Truth | 1.2.1 / 1.14.11 / 1.4.5 |
| Test | Robolectric | 4.17 |
| Test | Espresso / Compose UI Test | 3.7.0 / vía BOM |

**SDK:** `minSdk 26` · `targetSdk 37` · `compileSdk 37` (validado en la Fase 2: lint lo exige como última API estable).
Con `minSdk 26` se cubre prácticamente todo el parque activo y además hay `MediaCodec`/`MediaMuxer` modernos, notificaciones con canales y fuentes adaptativas. Las funciones que exigen APIs más nuevas (blur real en API 31+, reconocimiento de voz desde archivo en API 33+, marcas de tiempo por palabra en API 34+) tienen alternativa (*fallback*) explícita.

**Lo que no se agrega, y por qué:**
- **FFmpeg:** Media3 Transformer cubre el recorte, el redimensionado, la tasa de fotogramas, los overlays y la codificación H.264/AAC. FFmpeg sumaría unos 20–40 MB y problemas de licencia.
- **Retrofit/OkHttp:** en v1 no hay backend. Solo se agregarán si se implementa un motor de transcripción en la nube, con el consentimiento del usuario.
- **Coil/Glide:** las miniaturas de video se generan con `MediaMetadataRetriever` y una caché en disco propia.
- **Firebase/Crashlytics:** se respeta el principio de privacidad por defecto. Los errores se registran con un `Logger` abstracto (Logcat en debug) y se deja un punto de extensión.

---

## 3. Arquitectura

**Clean Architecture + MVVM con flujo de datos unidireccional (UDF).**

```text
 UI (Compose)  ──intents──▶  ViewModel  ──▶  UseCase  ──▶  Repository (interfaz, domain)
      ▲                         │                               │
      └──── StateFlow<UiState> ─┘                   RepositoryImpl (data)
                                                    ├── Room DAO
                                                    ├── DataStore
                                                    ├── FileStore (almacenamiento interno)
                                                    └── Multimedia (CameraX / Media3 / SpeechRecognizer)
```

Reglas de dependencia:
- `domain` es **Kotlin puro**: no importa `android.*` ni `androidx.*`. Así los casos de uso y la lógica del teleprompter y de los captions se prueban con JUnit en la JVM, sin emulador.
- `data` implementa las interfaces de `domain` y es la única capa que conoce Room, DataStore, CameraX, Media3 y SpeechRecognizer.
- `feature/*` (presentación) depende solo de `domain` y `core`. Nunca de `data`.
- Toda operación falible devuelve `AppResult<T>` (`Success` / `Failure(AppError)`). `AppError` es un `sealed interface` con errores de dominio (`CameraUnavailable`, `PermissionDenied`, `StorageFull`, `TranscriptionUnavailable`, `ExportFailed`…). La UI los traduce a mensajes legibles con `stringResource` y nunca muestra texto técnico.
- Un `StateFlow` que se lee **fuera** de la UI (por ejemplo, la condición del splash) usa `SharingStarted.Eagerly`. Con `WhileSubscribed` solo carga cuando la UI se suscribe; eso bloqueó los tests instrumentados en la Fase 3 y sería frágil en producción.
- Los estados son explícitos y usan `sealed interface`: `RecordingState`, `TranscriptionState`, `ExportState`, `TeleprompterState` y un `UiState` por pantalla.

### 3.1 Módulos

Un solo módulo Gradle `:app`, con las capas separadas por paquetes y la estructura exacta del requerimiento (§27).
Motivo: el proyecto parte de cero y lo mantiene una sola persona. Un solo módulo compila más rápido y es más fácil de refactorizar mientras el diseño se estabiliza. Como `domain` ya queda aislado (sin Android), extraer `:core:designsystem`, `:domain`, `:data` y `:feature:*` a módulos propios más adelante es mecánico. Queda anotado en el roadmap.

### 3.2 Estructura de paquetes

```text
com.arnoldcode.glassprompt/
├── GlassPromptApp.kt                 @HiltAndroidApp
├── MainActivity.kt                   edge-to-edge, SplashScreen, NavHost
├── core/
│   ├── common/                       AppResult, AppError, DispatcherProvider, Logger, TimeFormat
│   ├── designsystem/
│   │   ├── theme/                    GlassTheme, GlassColors, GlassTypography, GlassShapes,
│   │   │                             GlassElevation, GlassAnimations, GlassSpacing
│   │   ├── glass/                    Modifier.glassSurface(), GlassBackdrop, highlights/reflejos
│   │   └── component/                GlassCard, GlassButton, GlassIconButton, GlassBottomBar,
│   │                                 GlassTopBar, GlassDialog, GlassSlider, GlassChip,
│   │                                 GlassTextField, GlassPanel, GlassFloatingButton
│   ├── navigation/                   rutas @Serializable, GlassNavHost, TopLevelDestination
│   ├── permissions/                  PermissionState, rememberPermissionRequester, rationale sheets
│   └── utils/                        WindowSizeClass helpers, Haptics, FileProvider helpers
├── data/
│   ├── local/
│   │   ├── database/                 GlassPromptDatabase, Converters, Migrations
│   │   ├── dao/                      ProjectDao, ScriptDao, TakeDao, CaptionDao, CaptionStyleDao, ExportDao
│   │   ├── entities/                 *Entity + mappers a domain
│   │   └── preferences/              UserPreferencesDataSource (DataStore)
│   ├── repository/                   *RepositoryImpl
│   ├── storage/                      MediaFileStore (rutas, limpieza, espacio libre), MediaStoreSaver
│   ├── multimedia/
│   │   ├── camera/                   CameraXController (bind, zoom, focus, exposure, torch, record)
│   │   ├── audio/                    AudioExtractor (MediaExtractor+MediaCodec → PCM 16 kHz mono), Vad
│   │   ├── transcription/            TranscriptionEngine impls + EngineSelector
│   │   ├── captions/                 CaptionRenderer (Canvas/StaticLayout), CaptionBitmapOverlay
│   │   ├── export/                   VideoExporter (Media3 Transformer), ExportWorker
│   │   └── thumbnails/               ThumbnailProvider
│   └── di/                           módulos Hilt (Database, DataStore, Repositories, Multimedia, Dispatchers)
├── domain/
│   ├── model/                        Project, Script, Take, Caption, CaptionWord, CaptionStyle,
│   │                                 TeleprompterSettings, RecordingSettings, ExportSettings, Transcript…
│   ├── repository/                   interfaces
│   ├── teleprompter/                 ScrollEngine (lógica pura de velocidad/posición)
│   ├── captions/                     CaptionSegmenter, CaptionEditor ops (split/merge/retime), SrtWriter
│   └── usecase/                      ver §7
└── feature/
    ├── splash/ onboarding/ home/ projects/ script/ teleprompter/
    ├── camera/ video/ captions/ export/ templates/ settings/
    └── (cada una: Screen.kt, ViewModel.kt, UiState.kt, components/)
```

---

## 4. Modelo de dominio y persistencia

```kotlin
data class Project(
    val id: String, val name: String, val scriptId: String,
    val recording: RecordingSettings,        // lente, orientación, resolución, fps
    val teleprompter: TeleprompterSettings,  // velocidad, tamaño, espaciado, márgenes, posición, espejo, opacidad, alineación
    val captionStyleId: String,
    val createdAt: Long, val updatedAt: Long,
)
data class Script(val id: String, val title: String, val body: String, val updatedAt: Long)
data class Take(val id: String, val projectId: String, val filePath: String,
                val durationMs: Long, val width: Int, val height: Int, val fps: Int, val createdAt: Long)
data class Caption(val id: String, val text: String, val startTime: Long, val endTime: Long,
                   val words: List<CaptionWord> = emptyList())   // words → karaoke / resaltado por palabra
data class CaptionWord(val text: String, val startTime: Long, val endTime: Long)
data class CaptionStyle(val id: String, val name: String, val preset: CaptionPreset,
                        val fontSizeSp: Float, val fontWeight: Int, val textColor: Long,
                        val highlightColor: Long, val backgroundColor: Long, val backgroundOpacity: Float,
                        val strokeColor: Long, val strokeWidth: Float, val shadow: Boolean,
                        val position: CaptionPosition, val animation: CaptionAnimation,
                        val maxWordsPerLine: Int, val uppercase: Boolean, val isBuiltIn: Boolean)
enum class CaptionPreset { CLASSIC, BOLD, MINIMAL, CREATOR, NEON, GLASS, KARAOKE, CUSTOM }
enum class CaptionAnimation { NONE, FADE, POP, SLIDE, SCALE, WORD_HIGHLIGHT, KARAOKE }
```

**Room** (tablas): `projects`, `scripts`, `takes`, `captions` (FK → takes, `ON DELETE CASCADE`), `caption_styles` (con los presets precargados en `onCreate`), `exports` (historial de videos exportados: ruta, uri de MediaStore, resolución, fps, fecha).
- `exportSchema = true` con esquemas versionados en `app/schemas` y pruebas de migración desde el día 1.
- Las listas (`words`) se guardan como JSON mediante un `TypeConverter` con kotlinx-serialization.

**Implementado (Fase 4):** base de datos v1 con `projects` y `scripts` (el resto de tablas llega con sus fases, cada una con su migración). Un proyecto tiene exactamente un guion; las escrituras que tocan ambas tablas (crear, duplicar, eliminar, guardar el guion y actualizar el `updatedAt` del proyecto) son transacciones del `ProjectDao`. `scripts.wordCount` está desnormalizado para que las listas no carguen el texto completo. Los ajustes de grabación y teleprompter se guardan como columnas embebidas y los enums por nombre (un valor desconocido vuelve al predeterminado). La búsqueda de proyectos ignora mayúsculas y tildes ("cancion" encuentra "Canción"). Las plantillas son recursos (`res/values/templates.xml`), listas para traducir. El editor guarda 600 ms después de dejar de escribir y fuerza el guardado al salir de la pantalla o al pasar la app a segundo plano.

**DataStore** (`UserPreferences`): onboarding completado, tema (oscuro/claro/sistema), valores por defecto del teleprompter (velocidad, tamaño, espaciado, espejo), cámara por defecto, estilo de caption por defecto, motor de transcripción, idioma de transcripción y consentimiento de nube (siempre `false` por defecto).

**Archivos:** los videos y audios intermedios se guardan en almacenamiento interno de la app (`filesDir/takes`, `filesDir/exports`, `cacheDir/audio`). No se necesita ningún permiso de almacenamiento. "Guardar en galería" copia el archivo a `MediaStore` (`Movies/GlassPrompt`); en API 26–28 se pide `WRITE_EXTERNAL_STORAGE` con `maxSdkVersion=28`. Compartir usa `FileProvider` + Sharesheet.

---

## 5. Funciones clave: diseño técnico

### 5.1 Teleprompter

- **`ScrollEngine` (domain, puro):** `offset(t) = offset0 + basePxPerSec × speed × Δt`, con `speed ∈ [0.1, 3.0]`. `basePxPerSec` se deriva del tamaño de fuente y del interlineado, para que "1.0x" se lea igual con cualquier tamaño de texto (≈ 150 palabras por minuto). Tiene pruebas unitarias.
- **UI:** `ScrollState` controlado por un bucle `withFrameNanos` dentro de un `LaunchedEffect`. Solo el offset cambia en cada frame; el texto se mide una sola vez con `rememberTextMeasurer`.
- **Gestos:** arrastre vertical = modo manual (pausa el auto-scroll y retoma desde la nueva posición), doble toque = play/pausa, pellizco = tamaño (`detectTransformGestures`), además de botones Play, Pause, Restart, velocidad y tamaño.
- **Ajustes:** interlineado, espaciado entre letras, márgenes, posición (arriba/centro/abajo), opacidad del fondo, alineación y **modo espejo** (`graphicsLayer { scaleX = -1f }`).
- **Línea guía de lectura** y cuenta atrás de 3 segundos opcional antes de empezar.
- **Estimación de lectura** (editor): palabras ÷ 150 ppm, ajustada por la velocidad → "1,245 palabras · 8 min 12 s".

### 5.2 Cámara y grabación (CameraX 1.6)

- `CameraXViewfinder` (camera-compose) con los casos de uso Preview + `VideoCapture<Recorder>`.
- `QualitySelector` con las calidades soportadas, consultadas en `Recorder.getVideoCapabilities(cameraInfo)`. **4K solo aparece si el dispositivo lo soporta.** La tasa de fotogramas se fija con `VideoCapture.Builder.setTargetFrameRate` y se validan los rangos soportados.
- Controles: cambiar cámara, linterna (`enableTorch`, solo si `hasFlashUnit`), zoom (pellizco + `setLinearZoom`), toque para enfocar (`FocusMeteringAction`) y exposición (`setExposureCompensationIndex`, solo si el rango lo permite).
- Grabación: `start` / `pause` / `resume` / `stop` con `withAudioEnabled()`. El `RecordingState` se deriva de `VideoRecordEvent`.
- Durante la grabación la interfaz se reduce: queda REC con la duración, pausa, finalizar y el teleprompter. El resto se desvanece tras 3 segundos y vuelve con un toque.
- El teleprompter se superpone **solo en la vista previa**. Nunca queda grabado en el video.
- Errores comunes (cámara ocupada, permiso revocado, sin espacio) se muestran como `AppError` con mensajes claros. Antes de grabar se comprueba el espacio libre.

**Implementado (Fase 5):** `ScrollEngine` deriva la velocidad del propio guion maquetado (distancia total ÷ tiempo de lectura a 150 ppm), así que "1.0x" es igual con cualquier tamaño, interlineado o pantalla. `PrompterSettingsSession` comparte carga del guion y ajustes entre el teleprompter de ensayo y la cámara, y guarda los cambios con debounce para que un pellizco no escriba en cada frame. La cámara vive tras la interfaz `CameraController` (implementada con CameraX en `data/multimedia`), de modo que `CameraViewModel` se prueba sin hardware; la configuración pedida se ajusta a lo que el dispositivo soporta y se informa lo realmente aplicado. Antes de grabar se reserva el archivo y se comprueba el espacio (`StorageManager.getAllocatableBytes`); una grabación vacía o fallida borra su archivo en vez de crear una toma. La base de datos pasa a v2 con la tabla `takes` y una migración escrita a mano, probada con `MigrationTestHelper`. La revisión de la toma usa Media3 (`ContentFrame`) con la posición observable, preparada para superponer captions en la Fase 6.

### 5.3 Transcripción (desacoplada del proveedor)

```kotlin
interface TranscriptionEngine {
    val id: String
    suspend fun isAvailable(language: String): Boolean
    suspend fun transcribe(audio: File, language: String, onProgress: (Float) -> Unit): Transcript
}
data class Transcript(val segments: List<TranscriptSegment>, val language: String, val hasWordTimings: Boolean)
```

Pipeline: `Take (mp4)` → **AudioExtractor** (MediaExtractor + MediaCodec → PCM 16 kHz mono, por streaming y sin cargar todo en memoria) → **VAD** por energía (detecta los tramos con voz) → **EngineSelector** → `Transcript` → **CaptionSegmenter** (agrupa palabras en captions de 1–2 líneas, respetando `maxWordsPerLine` y la puntuación) → Room.

Motores previstos:

| Motor | Cuándo | Cómo |
|---|---|---|
| `AndroidSpeechEngine` | API 33+ con reconocedor en el dispositivo | `SpeechRecognizer.createOnDeviceSpeechRecognizer` + `RecognizerIntent.EXTRA_AUDIO_SOURCE` (se le entrega el PCM mediante `ParcelFileDescriptor`). En API 34+ pide `EXTRA_REQUEST_WORD_TIMING`. Sin marcas por palabra, transcribe por tramos del VAD y reparte los tiempos según la longitud de las palabras. |
| `ScriptAlignmentEngine` | Siempre disponible, sin conexión | Como el usuario leyó un guion conocido, reparte las palabras del guion sobre los tramos de voz del VAD de forma proporcional. Es el fallback universal y funciona en API 26. |
| `WhisperEngine` / `CloudEngine` | Roadmap | Se implementan la misma interfaz. La nube exige el consentimiento explícito guardado en DataStore. |

> ⚠️ **Riesgo técnico #1:** el soporte de `EXTRA_AUDIO_SOURCE` y de las marcas por palabra depende del reconocedor que traiga cada dispositivo (normalmente los servicios de voz de Google) y de que el paquete de idioma esté instalado. Por eso existe el `ScriptAlignmentEngine`: siempre habrá captions. Se valida en la Fase 6 con el emulador y con un dispositivo real.

La transcripción corre en un `CoroutineWorker` (WorkManager + Hilt), así sobrevive si el usuario sale de la pantalla y su progreso se expone como `TranscriptionState`.

### 5.4 Editor de captions

- Vista previa con `ExoPlayer` (media3-ui-compose) y overlay de captions sincronizado con `player.currentPosition` (sondeo por frame mientras reproduce).
- Timeline horizontal con `LazyRow`: bloques proporcionales a su duración, cursor de reproducción, selección y asas para ajustar inicio y fin.
- Operaciones puras en `domain/captions` (con pruebas): `split(at: Long | wordIndex)`, `merge(a, b)`, `retime(start, end)` (sin solapes y con duración mínima), `insert`, `delete`, `edit text` (recalcula las palabras).
- Deshacer/rehacer con una pila de estados en el ViewModel.
- Panel de estilo: presets (Classic, Bold, Minimal, Creator, Neon, Glass, Karaoke) + personalización (fuente, tamaño, color, trazo, sombra, fondo, posición, animación, mayúsculas).
- Exportación extra de `.srt` (subtítulos como archivo aparte), útil para YouTube.

### 5.5 Render de captions — WYSIWYG

Un único **`CaptionRenderer`** dibuja sobre un `android.graphics.Canvas` (con `StaticLayout`), dados un tiempo `t`, un `Caption` y un `CaptionStyle`. Calcula las animaciones (fade, pop, slide, scale, word highlight, karaoke) como funciones del tiempo.
- **En la vista previa** se usa con `drawIntoCanvas { it.nativeCanvas }`.
- **En la exportación** se usa dentro de un `BitmapOverlay` de Media3 (`getBitmap(presentationTimeUs)`).

Así, lo que el usuario ve es exactamente lo que se exporta, y todo sale de un solo código.

### 5.6 Exportación (Media3 Transformer)

- `EditedMediaItem` con estos efectos de video: `Presentation.createForHeight(720 | 1080 | 2160)`, `FrameDropEffect` (para 24/30/60) y `OverlayEffect(listOf(CaptionBitmapOverlay))`. El audio se conserva.
- Salida H.264 + AAC en MP4. Si falla la codificación HEVC o 4K, Transformer recurre a la configuración compatible (`DefaultEncoderFactory` con fallback habilitado).
- Se ejecuta en un `ExportWorker` en primer plano (notificación con progreso; `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` en API 35+ y `DATA_SYNC` en las anteriores). El progreso se lee de `Transformer.getProgress(ProgressHolder)` y el tiempo restante se estima con una media móvil. La UI nunca se bloquea y la exportación se puede cancelar.
- **Límites honestos:** la tasa de fotogramas exportada es ≤ la grabada (no se inventan frames), y la resolución de salida es ≤ la de origen. La UI deshabilita las opciones que no aplican.

### 5.7 Compartir

Pantalla de resultado con: **Guardar** (MediaStore), **Compartir** (Sharesheet con `ACTION_SEND` y `video/mp4` mediante `FileProvider`) y accesos a Instagram, TikTok, YouTube y WhatsApp. Estos accesos son el mismo `ACTION_SEND` con `setPackage(...)`; solo aparecen si la app está instalada (se comprueba con `resolveActivity` y las declaraciones `<queries>` del manifiesto) y, si falla, se abre el Sharesheet general. No se usa ninguna API privada.

---

## 6. Design system "Liquid Glass"

### 6.1 Tokens

- **GlassColors** (oscuro primero): fondo `#07080C` a `#0E1016`; superficie de vidrio con blanco al 6–14 % de opacidad; borde con degradado blanco del 28 % → 4 %; acento azul `#5B8CFF`; violeta `#8E7CFF`; texto `#F5F7FA` / `#A9B0BC`. También hay variante clara. Todos los pares de texto cumplen un contraste ≥ 4.5:1.
- **GlassTypography:** la fuente del sistema (Roboto Flex donde exista) con una escala M3 ajustada. Los tamaños del teleprompter son independientes.
- **GlassShapes:** radios de 12 / 20 / 28 / 36 dp y píldora. **GlassElevation:** 3 niveles de profundidad (opacidad + sombra difusa + intensidad del brillo). **GlassAnimations:** springs y duraciones estándar (120 / 220 / 320 ms).
- Retícula de espaciado de 4 dp. Se mapea sobre `MaterialTheme`, así los componentes M3 heredan la identidad.

### 6.2 Anatomía de una superficie de vidrio (`Modifier.glassSurface`)

1. Desenfoque del fondo que hay detrás (API 31+).
2. Tinte translúcido en degradado.
3. Borde luminoso en degradado, más brillante arriba a la izquierda para simular la luz.
4. Reflejo especular en la mitad superior.
5. Sombra suave de color.
6. Al presionar: escala a 0.97 con un spring, más intensidad de brillo y respuesta háptica.

Detrás de todo hay un **fondo "aurora"**: manchas de azul y violeta que se mueven muy despacio y se dibujan en un `Canvas` barato. Sin ese fondo, el vidrio no tendría nada que refractar.

### 6.3 Blur: decisión técnica

Compose **no trae** un desenfoque del contenido de fondo: `Modifier.blur` desenfoca el propio contenido, no lo que está detrás. Opciones:
- **(Recomendada) Haze 2.0.0.** Librería mantenida y específica para Compose que hace el desenfoque del fondo con `RenderEffect` en API 31+ y cae a un tinte translúcido en versiones anteriores. Es una dependencia pequeña y justificada.
- **Implementación propia.** Capturar el fondo con `GraphicsLayer` + `RenderEffect.createBlurEffect`, recortado por cada superficie. Es viable, pero reimplementa lo que Haze ya resuelve.

**Implementado (Fase 3):** Haze 2.0 separa el núcleo (`hazeSource`) de los efectos. Se usa `hazeBlur` (API estable) en tarjetas, barras, paneles y diálogos mediante `Modifier.glassSurface`, y `hazeGlass` (refracción con shaders AGSL, `@ExperimentalHazeApi`, con fallback propio en Android < 13) solo en elementos protagonistas mediante `Modifier.liquidGlass`: la barra inferior y el botón de grabar. El fondo aurora se registra como fuente en la capa 0 y el contenido de cada pantalla en la capa 1; así las barras flotantes refractan el contenido y las tarjetas solo el fondo. Los diálogos (otra ventana) anulan la fuente y usan el tinte de respaldo.

En API < 31, con "Reducir efectos" activo o con "Quitar animaciones" del sistema activo, siempre se usa el tinte translúcido (sin desenfoque), y las animaciones ambientales quedan estáticas. **Sobre la cámara en vivo no se aplica desenfoque** (cuesta rendimiento y térmica): se usan paneles translúcidos sin blur.

### 6.4 Componentes

`GlassCard, GlassButton, GlassIconButton, GlassBottomBar, GlassTopBar, GlassDialog, GlassSlider, GlassChip, GlassTextField, GlassPanel, GlassFloatingButton`. Todos se construyen sobre `glassSurface` (sin duplicar código), con touch target ≥ 48 dp, `contentDescription`/`semantics` y una vista previa `@Preview` en oscuro y claro.

---

## 7. Casos de uso

| Grupo | Casos de uso |
|---|---|
| Proyectos | `CreateProjectUseCase`, `UpdateProjectUseCase`, `DeleteProjectUseCase`, `DuplicateProjectUseCase`, `ObserveProjectsUseCase` |
| Guion | `SaveScriptUseCase` (autosave con debounce de 600 ms), `ImportScriptUseCase` (.txt vía SAF, portapapeles), `ComputeScriptStatsUseCase` |
| Grabación | `StartRecordingUseCase`, `StopRecordingUseCase`, `PauseResumeRecordingUseCase` |
| Captions | `TranscribeAudioUseCase`, `UpdateCaptionUseCase`, `SplitCaptionUseCase`, `MergeCaptionUseCase`, `AddCaptionUseCase`, `DeleteCaptionUseCase`, `ApplyCaptionStyleUseCase`, `ExportSrtUseCase` |
| Exportación | `ExportVideoUseCase`, `CancelExportUseCase`, `SaveToGalleryUseCase`, `ShareVideoUseCase` |
| Privacidad | `DeleteAllDataUseCase`, `DeleteTranscriptsUseCase`, `DeleteVideosUseCase` |

---

## 8. Navegación

Navigation Compose 2.10 con **rutas tipadas** (`@Serializable`).

```text
Splash (SplashScreen API) ──▶ Onboarding (solo la primera vez) ──▶ Main
Main (GlassBottomBar): Home · Projects · Record(FAB central) · Templates · Settings
Home/Projects ──▶ ProjectSetup(projectId?) ──▶ ScriptEditor(projectId)
ScriptEditor ──▶ Teleprompter(projectId)   (ensayo sin cámara)
ScriptEditor ──▶ Camera(projectId)         (teleprompter sobre cámara)
Camera ──(al terminar la toma)──▶ VideoReview(takeId) ──▶ CaptionEditor(takeId)
CaptionEditor ──▶ Export(takeId) ──▶ ExportResult(exportId)  (Guardar / Compartir)
Settings ──▶ PrivacySettings · TranscriptionSettings · About
```

**Camino más corto** (§33): Home → "Nuevo proyecto" (la hoja de configuración trae valores por defecto razonables, así que basta con escribir el nombre) → Editor → **Grabar** → al terminar se genera la transcripción automáticamente → Editor de captions → Exportar. Son unos 5 toques además de escribir el texto.

En tablets (ancho ≥ 600 dp) se usa `NavigationRail` en lugar de la barra inferior, y el editor de captions pasa a dos paneles (video | timeline + estilo).

---

## 9. Permisos

| Permiso | Cuándo se pide | Justificación mostrada al usuario |
|---|---|---|
| `CAMERA` | Al entrar a Camera | "Para grabarte mientras lees el guion." |
| `RECORD_AUDIO` | Al entrar a Camera | "Para grabar tu voz y generar subtítulos automáticos." |
| `POST_NOTIFICATIONS` (33+) | Al iniciar la primera exportación | "Para avisarte cuando tu video esté listo." (opcional) |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | Al pulsar "Guardar" en Android 8–9 | "Para guardar el video en tu galería." |
| `FOREGROUND_SERVICE` + `_MEDIA_PROCESSING`/`_DATA_SYNC` | Normales, sin diálogo | — |

No se piden `READ_MEDIA_*`: la importación usa el selector del sistema (SAF / Photo Picker). Tampoco `INTERNET` en v1. Si el permiso se deniega de forma permanente, se muestra una hoja que lleva a los ajustes de la app.

---

## 10. Calidad transversal

- **Rendimiento:** clases de estado `@Immutable`, lambdas estables, `derivedStateOf` para lo que cambia en cada frame, listas lazy con `key`, procesamiento en IO/Default, Baseline Profile (Fase 9), R8 completo en release, audio y video por streaming (nunca completos en memoria), miniaturas en caché.
- **Accesibilidad:** semántica en todos los controles, acciones personalizadas en el timeline, respeto de la escala de fuente y de "quitar animaciones", contraste AA, orden de foco lógico y probado con TalkBack.
- **Errores:** `CoroutineExceptionHandler` en los scopes de trabajo, `runCatching` en las fronteras con data, `Logger` con etiquetas por área, y ningún `!!`.
- **i18n:** todo el texto en `strings.xml` (español por defecto y estructura lista para inglés).

---

## 11. Estrategia de testing

| Tipo | Alcance | Herramientas |
|---|---|---|
| Unit (JVM) | ScrollEngine, cálculo de tiempo de lectura, CaptionSegmenter, split/merge/retime, SrtWriter, casos de uso, ViewModels | JUnit4, Truth, MockK, Turbine, coroutines-test |
| Unit (Robolectric) | Repositorios con Room en memoria, DataStore, CaptionRenderer (métricas) | Robolectric |
| Integración (instrumentado) | DAOs y migraciones de Room, MediaFileStore, flujo de grabación con CameraX (`FakeCamera` / emulador) | androidx.test, Room testing |
| UI (instrumentado) | Navegación, crear proyecto, editor de guion, teleprompter (play/pausa/velocidad), editor de captions | Compose UI Test, Hilt testing |

Cada fase termina con `./gradlew assembleDebug testDebugUnitTest lint` en verde, y las fases que tocan la UI o los datos, además con `connectedDebugAndroidTest` en el AVD.

---

## 12. Plan por fases

| Fase | Entregable | Criterio de "estable" |
|---|---|---|
| **1** | Este documento | Aprobado por ti |
| **2** | Proyecto Gradle (wrapper, version catalog, AGP/Kotlin/KSP/Hilt/Compose/Nav/Room vacíos), `GlassPromptApp`, `MainActivity` edge-to-edge, tema base, `.gitignore`, `git init` | `assembleDebug` + tests vacíos en verde; la app arranca en el AVD |
| **3** | Design system Liquid Glass completo + fondo aurora, Splash, Onboarding (3 páginas), shell con bottom bar/rail, Home con datos de muestra | Previews y capturas en el AVD; pruebas de navegación |
| **4** | Room + DataStore, proyectos (crear/editar/duplicar/eliminar), editor de guion (autosave, importar .txt/portapapeles, buscar/reemplazar, estadísticas), Templates | Tests de DAO, repositorios y ViewModels |
| **5** | Teleprompter independiente + cámara con overlay, grabación (pausa, zoom, enfoque, exposición, linterna, cambio de cámara), permisos | ScrollEngine probado; grabación verificada en el AVD |
| **6** | Extracción de audio, VAD, motores de transcripción, segmentador, editor de captions, estilos y animaciones, CaptionRenderer | Tests de dominio; transcripción real verificada |
| **7** | Reproductor, exportación con Transformer + worker + notificación, guardar en galería, compartir, SRT | Exportación 720p/1080p verificada con reproducción del resultado |
| **8** | Completar las suites de unit, integración y UI | Suites en verde |
| **9** | Baseline Profile, revisión de recomposiciones, R8, accesibilidad, fallback de blur | Sin regresiones; lint limpio |
| **10** | Build release (firma de debug o keystore que proporciones), README con capturas | APK/AAB generado |

---

## 13. Riesgos y mitigaciones

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | El reconocimiento de voz desde archivo depende del dispositivo | `ScriptAlignmentEngine` sin conexión + interfaz lista para Whisper (whisper.cpp vía NDK, que ya está instalado) |
| 2 | La imagen del AVD es beta (API 37.2) | Probar también en un dispositivo real; opcionalmente instalar una imagen estable de API 36 |
| 3 | El costo del blur en equipos modestos | Blur solo en API 31+, nunca sobre la cámara; toggle "reducir efectos" |
| 4 | Exportar en 4K / 60 fps en hardware limitado | Opciones filtradas por capacidades + fallback del encoder de Transformer |
| 5 | Combinación AGP 9.4 + Kotlin 2.4.20 + KSP 2.3.12 + Hilt 2.60.1 | Se valida en la Fase 2 antes de escribir código de negocio |
