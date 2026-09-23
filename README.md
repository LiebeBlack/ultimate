# PayLens U

Escáner de precios y calculadora financiera flotante para **Android de gama baja** (Android Go, 1 GB RAM, quad-core). Visor HUD OLED puro con neón vectorial, OCR *event-driven* y burbuja flotante sobre otras apps.

```
Kotlin 2.0 · Jetpack Compose · CameraX · ML Kit · minSdk 24 · sin Hilt/Room
```

## Estrategia Zero-Overhead

| Presupuesto | Técnica |
|---|---|
| **OCR < 100 ms percibidos** | *Tap-to-Scan*: la CPU procesa **solo al tocar**. Ráfaga de 3 frames, se elige el más nítido (varianza de gradiente del plano Y) y solo ese pasa al OCR con timeout de 400 ms. Entre toques el `ImageAnalysis` queda sin analyzer: no adquiere buffers. |
| **RAM < 50 MB** | Dinero en `Long` centavos (nunca Double), DI manual (`PayLensApp.Graph`), SQLite directo sin Room, bitmap gris ≤ 1080 px reciclado al terminar, un solo motor OCR activo. |
| **Batería OLED** | Fondo `#000000` absoluto, status/nav bar negras, splash sin imagen. |
| **0 frames perdidos** | Neón = strokes planos (2 draw calls, sin blur/RenderEffect). Pulso de la retícula solo vía `graphicsLayer` (spring < 150 ms, sin re-layout). Preview en `PERFORMANCE` (SurfaceView). |

## Módulos

```
app/src/main/java/com/paylensu/
├── core/       Money (centavos, half-up) · ExchangeRate (tasa + umbral)
├── parser/     PriceParser: smart regex ES/EN ($12.50 · 1.234,56 Bs) + heurística $/Bs
├── finance/    PriceMath (IVA 16% configurable) · MixedPayment (matrix de vuelto)
├── data/       CartDb (SQLite) · CartRepository · RateStore (DataStore) · RateSync (BCV)
├── camera/     CameraController (720p + ráfaga) · FramePrepper (Y→gris, nitidez)
├── ocr/        OcrEngine/OcrClient + factories por flavor
├── hud/        Tema OLED, retícula spring, ResultCard, Sticky Bar, Sheet, Keypad
├── overlay/    BubbleService (foreground + WindowManager) con ComposeView propia
└── ui/         ScannerScreen · ScannerModel · SettingsScreen · AppRoot
```

**Smart Regex**: el símbolo manda (`$`/`USD` → dólares, `Bs`/`VES` → bolívares). Sin símbolo, heurística por magnitud contra la tasa guardada (umbral $20 configurable en Ajustes). Devuelve top-3 tocable; el teclado numérico corrige dígitos sin nueva foto.

**Matrix de pago mixto**: por cada billete ($1…$100 + monto libre) muestra efectivo aplicado, resto por Pago Móvil en Bs a la tasa BCV y vuelto exacto en $ y Bs, con fila óptima resaltada. `MixedPayment.resolve` comparte la fila elegida entre la UI y el cobro: lo que ves es lo que se asienta.

**Cobro e impuestos**: COBRAR asienta la transacción (historial en SQLite) y aplica el saldo de la **CUENTA** (efectivo $ neto del vuelto + Pago Móvil Bs). Los impuestos a cobrar suman el IVA contenido y el **IGTF 3%** sobre la parte pagada en efectivo $.

## Compilación

```bash
# OCR incluido en el APK (~6 MB de modelo, funciona sin Play Services)
./gradlew :app:assembleBundledOcrDebug

# OCR thin vía Play Services (APK mínimo)
./gradlew :app:assembleThinOcrDebug

# Tests JVM (parser, Money, finanzas, carrito)
./gradlew testBundledOcrDebugUnitTest
```

JDK 17 requerido. Los dos flavors comparten el mismo FQCN `com.paylensu.ocr.OcrFactoryImpl`, por lo que el resto del código no cambia.

## Burbuja flotante

El chip **BURBUJA** abre el permiso *Mostrar sobre otras apps* y lanza un servicio foreground (`specialUse`) con un `ComposeView` en `TYPE_APPLICATION_OVERLAY`: head arrastrable de 84 dp ↔ panel de 300 dp con mini-escáner, ResultCard y carrito. Nota: apps con `FLAG_SECURE` (bancos) bloquean lo que hay detrás; la calculadora manual y el carrito siguen operativos.

## Tasa BCV

Auto-sync al abrir (dolarapi → pydolarve, timeout 3 s, cascada) con la última tasa guardada como offline-first. La tasa también se puede fijar manualmente en Ajustes.

## Web del proyecto (docs/)

El directorio `docs/` contiene la landing page oficial (HTML/CSS/JS puro, sin build step) con la identidad OLED/neón de la app:

```bash
# Servir localmente para probar
python -m http.server 8734 --directory docs
# → http://127.0.0.1:8734
```

### Publicar en GitHub Pages

En **Settings → Pages** del repo, elige *Deploy from a branch* → rama `main` → carpeta **`/docs`**. La web quedará en `https://<usuario>.github.io/ultimate/` y se actualiza con cada push a `main`.

## CI/CD y releases

- **CI** (`.github/workflows/ci.yml`): en cada push/PR corre los unit tests y compila `bundledOcr` (debug+release) y `thinOcr` (debug). Sube los APK como artefacto.
- **Release** (`.github/workflows/release.yml`): al pushear un tag `v*` (p. ej. `v1.0.0`) compila, firma (si hay secrets), publica un **GitHub Release** con AAB bundled + APK bundled + APK thin.

### Firma de release

```bash
keytool -genkeypair -v -keystore paylensu-release.jks -alias paylensu \
  -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties   # rellenar credenciales
git tag v1.0.0 && git push origin v1.0.0             # dispara el release
```

En CI la firma llega por secrets: `KEYSTORE_B64` (el `.jks` en base64, `base64 -w0 paylensu-release.jks`) y `KEYSTORE_PROPERTIES` (el contenido de `keystore.properties`). Sin secrets, el release sale sin firmar. `keystore.properties`, `*.jks` y `*.keystore` están en `.gitignore` — **nunca se suben**.

### Animaciones (presupuesto de frames)

Toda animación vive en transform de capa (`graphicsLayer`): press-scale spring en chips/botones/teclas (<150 ms), pulso de totales en la Sticky Bar, pulso de retícula al disparar, entrada animada de mensajes y transición visor↔ajustes. Cero re-layouts, cero blur, cero sombras gaussianas.
