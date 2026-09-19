# Boletas — escanear documentos y exportar a Excel

App Android aparte de la del inventario. Se instala al lado, no la reemplaza.

## Qué hace
1. Escaneas una boleta, factura o voucher con la cámara.
2. Lee sola número, total, neto, IVA, fecha, RUT y últimos 4 de la tarjeta.
3. Muestra lo que leyó para que lo revises y corrijas. Nada se guarda sin tu confirmación.
4. Se van acumulando en una lista con el total sumado arriba.
5. "Exportar Excel" arma un .xlsx y lo pasas por el menú de compartir (correo, Drive, lo que sea).

Lo escaneado queda guardado en el teléfono: si cierras la app, no se pierde.
La foto NO se guarda, solo el texto que sacó.

## Instalar
1. Sube todo esto a un repo nuevo en GitHub (ej. `boletas-android`).
2. Actions corre solo. Cuando termine: pestaña **Releases** → el más reciente → **Assets** → el `.apk`.
3. Ábrelo en el celular y se instala.

Usa la misma llave de firma que la app de inventario, así que se instala
sobre versiones anteriores sin el "paquete no válido". Necesita Android 12+.

## El lector
Está calibrado con tus 11 boletas reales (ESTEL, Alex Ruz, GetNet, Astro,
Transbank, Los Cisnes, etc.). Si aparece una que lee mal, mándamela.
