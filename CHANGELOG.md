# Changelog

Formato: [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) · Versionado: [SemVer](https://semver.org/lang/es/)

## [1.0.0] — 2026-09-23

### Añadido
- Escáner **Tap-to-Scan**: ráfaga de 3 frames al tocar, selección del más nítido y OCR único con presupuesto de 400 ms. Cero procesamiento entre toques.
- Parser Smart Regex ES/EN (`$12.50`, `1.234,56 Bs`) con heurística $/Bs por magnitud (umbral configurable) y top-3 de precios tocables.
- Card HUD: conversión exacta $↔Bs a la tasa BCV, IVA incluido con desglose neto/IVA y matrix de pago mixto (billetes $1–$100 + monto libre, vuelto en $ y Bs, fila óptima).
- Carrito Flotante Express: Sticky Bar con pulso al cambiar totales, sheet deslizable con borrar/vaciar + deshacer, entradas ≤ 1200 B en SQLite.
- Micro-calculadora táctil para corregir dígitos sin nueva foto.
- Burbuja flotante (servicio foreground + `TYPE_APPLICATION_OVERLAY`): head arrastrable ↔ panel con mini-escáner, ResultCard y teclado sobre otras apps.
- Tasa BCV: auto-sync (dolarapi → pydolarve, timeout 3 s) con fallback manual en Ajustes; umbral $/Bs configurable.
- HUD OLED: negro `#000000` absoluto, neón por strokes planos (`#00F3FF`/`#9D00FF`), retícula con pulso spring <150 ms solo vía `graphicsLayer`.
- Animaciones: press-scale en chips/botones/teclas, entrada animada del mensaje, transición visor↔ajustes, haptics al disparar el escaneo.
- Sabores `bundledOcr` (modelo en APK, sin GMS) y `thinOcr` (Play Services, APK mínimo).
- CI de GitHub Actions (tests + ambos flavors) y release firmado por tag `v*`.

### Cambiado
- **Parser**: las palabras de recibo (TOTAL, PAGAR, IMPORTE…) impulsan la confianza de cifras sin símbolo; cotas de sanidad descartan códigos largos/fechas fuera de rango.
- **Matrix de pago**: la fila óptima es ahora el mayor billete que cubre SIN exceder el total (los sobrepagos nunca son óptimos; quedan como opción con su vuelto).
- Un escaneo sin resultados abre el teclado de corrección automáticamente.
- Animaciones de disposición: la ResultCard expande/recoge al aparecer cada escaneo, el keypad sube deslizándose (visor y burbuja) y los ítems del carrito se reordenan/borran con movimiento.

### Añadido (finanzas de cobro)
- **COBRAR** en la ResultCard y en el carrito: asienta la transacción con su fila de pago real (`MixedPayment.resolve`) y descuenta/suma el saldo de la cuenta.
- **Impuestos a cobrar**: IVA contenido en el total + **IGTF 3%** cuando hay parte en efectivo $ (misma fórmula en UI y cobro).
- **CUENTA**: sheet con saldo de efectivo $ y Pago Móvil Bs (equivalencias cruzadas), fijado manual del saldo contado e historial de movimientos con impuestos (borrado de historial incluido).
- Saldo visible en la barra superior (chip CUENTA) y en el panel de la burbuja, que también cobra.
