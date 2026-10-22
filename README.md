# Velocidad Real

App de Android que mide la velocidad de internet **real** y detecta si tu proveedor le da prioridad a los servidores de test populares para aparentar más velocidad.

## Cómo funciona

En cada prueba mide, en la misma sesión:

| Servidor | Tipo | Por qué |
|---|---|---|
| M-Lab (protocolo NDT7) | Neutral | Red abierta usada en investigación, difícil de identificar como "test comercial" |
| Cloudflare | Neutral | Su tráfico viaja por la misma red que sirve millones de webs |
| Servidor de test popular | Control | El que un proveedor tendría interés en priorizar |

- **Velocidad real** = mediana de los servidores neutrales.
- **Alerta de trato preferencial** si el servidor de control da 1,5 veces o más que los neutrales (y al menos 20 Mbps de diferencia).
- También mide latencia y **latencia con carga** (cuánto empeora la respuesta mientras se usa toda la conexión).
- Guarda el historial en el teléfono y exporta un **PDF** con todas las pruebas para usar como evidencia en un reclamo.

## Instalar

Cada cambio en `main` compila la app automáticamente. Ve a **Releases** en este repositorio, descarga `VelocidadReal.apk` desde el celular y ábrelo (Android pedirá permitir instalar apps de esta fuente).

## Limitaciones

- Por Wi‑Fi, la señal o el router también pueden bajar la velocidad. La evidencia más sólida es por cable o cerca del router.
- Los servidores de terceros pueden cambiar sus direcciones; si uno falla, la prueba sigue con los demás.
