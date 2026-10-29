# Drax Atomic

Mod de NeoForge para Minecraft 26.2 (versión del mod 0.7.0).

## Poder mágico

Barra violeta sobre la de experiencia (100 por defecto, se recarga 4 por segundo; no recarga mientras cargas el Atomic). Se conserva al morir. Gratis en creativo.

## Espada de Slime

Espada de nivel netherita (no se quema en lava). **Shift + clic derecho** cambia de modo; el modo actual se ve sobre la barra y en el tooltip.

| Modo | Uso | Poder |
|---|---|---|
| Látigo de slime | Clic derecho: la hoja se estira ~10 bloques y golpea todo en línea, acercando a los enemigos | 8 |
| Corte instantáneo | Clic derecho: avanzas ~8 bloques de golpe cortando todo lo que atraviesas | 20 |
| Lluvia de cuchillas | Clic derecho al suelo: 6 oleadas de agujas de slime en un área de radio 5 | 35 |
| Atomic | Mantén clic derecho 3 s: círculo mágico, levitación, la frase y la explosión atómica | 100 |
| All-Range Atomic | Mantén clic derecho 4 s: marca a cada enemigo en 40 bloques (hasta 24) y cae un Atomic pequeño sobre cada uno; no rompe el terreno | 140 |
| Recovery Atomic | Mantén clic derecho 2 s: esfera de luz verde que cura por completo a ti, tus aliadas y los jugadores cercanos, y quita efectos negativos | 60 |

Soltar el Atomic antes de tiempo lo cancela sin gastar poder. Tú, tus mascotas, tu equipo y los jugadores en creativo no reciben daño.

**Receta:** bloque de slime / bloque de slime / estrella del Nether (en columna). También en la pestaña Combate del creativo.

## Traje de Slime

Capucha (abierta, deja ver tu cara), abrigo, pantalones y botas (nivel netherita, no se queman). Cada pieza da +25 de poder máximo y +1 de recarga por segundo.

| Pieza | Efecto |
|---|---|
| Capucha | Visión nocturna |
| Abrigo | 35 % de probabilidad de desviar proyectiles |
| Pantalones | Velocidad |
| Botas | Sin daño por caída; doble salto en el aire (5 de poder) |
| Set completo | Recarga doble; agachado, los monstruos a más de 8 bloques pierden tu rastro |

**Recetas:** como la armadura vanilla, con bloques de slime y un lingote de netherita en el centro. Se reparan con bloques de slime.

## Shadow Garden

Usa el **Emblema de la Sombra** con clic derecho en el suelo para invocar a una integrante (cada emblema invoca a la siguiente del grupo). Te siguen, te defienden, atacan a los monstruos cercanos (menos creepers), se curan solas y no reciben daño de tus habilidades ni del Atomic. Clic derecho sobre una: alterna entre seguirte y esperar. Si cae, suelta su emblema.

| Integrante | Rol |
|---|---|
| Noctis | Espadachina |
| Umbra | Sigilo: invisible fuera de combate, debilita al golpear |
| Vesper | Maga: rayos explosivos a distancia |
| Lyra | Arquera: rayos rápidos de largo alcance |
| Selene | Sanadora: cura a ti y a las demás |
| Nyx | Berserker: golpes de barrido |
| Iris | Guardiana: mucha vida y armadura, con escudo |

**Receta del emblema:** oro arriba y abajo, bloque de slime – ojo de ender – bloque de slime en el centro.

## Culto de Diablos

- **Cultistas de Diablos:** de noche aparecen patrullas de 2–4 (caballeros cuerpo a cuerpo y magos con rayos dorados) a 24–40 bloques de los jugadores. Atacan a jugadores y a la Shadow Garden. Sueltan **Gotas de Diablos**.
- **Sello de Diablos:** gotas en las esquinas, bloques de oro en los lados y un Emblema de la Sombra al centro. Clic derecho en el suelo, de noche, invoca al jefe.
- **Caballero de la Mesa Redonda** (400 de vida, barra de jefe):
  - Fase 1: espada, rayos, se teletransporta detrás de ti y llama refuerzos.
  - El golpe que lo bajaría de la mitad de vida lo deja justo en la mitad; queda invulnerable 4 s, despierta con una onda expansiva y cambia de aspecto.
  - Fase 2: más rápido y fuerte, alterna rayos con un "juicio" en área y llama más refuerzos.
  - Al morir suelta el **Corazón de Diablos** (+50 de poder mágico máximo para siempre) y varias gotas.

## Mitsugoshi y la capital de Midgar

- **Tienda Mitsugoshi:** edificio de cuarzo y oro con una "M" dorada, vitrinas, alfombra roja y un mostrador. Aparece sola en llanuras, praderas, sabanas, bosques, taigas, desiertos y cerezales (`/locate structure draxatomic:mitsugoshi_store`).
- **Empleado de Mitsugoshi:** profesión de aldeano cuyo puesto es el **Mostrador Mitsugoshi** (cualquier aldeano sin trabajo lo toma). Vende:
  - Nivel 1: Chocolate Mitsugoshi (velocidad II + regeneración) y Café Mitsugoshi (prisa II + visión nocturna); compra cacao y azúcar.
  - Nivel 2: Champú Mitsugoshi (quita efectos negativos, da suerte) y pasteles; compra bolas de slime.
  - Nivel 3: bloques de slime y el Emblema de la Sombra; compra Gotas de Diablos.
  - Nivel 4: capucha y botas de slime.
  - Nivel 5: la Espada de Slime y el Sello de Diablos.
- **Capital de Midgar:** ciudad amurallada de 48×48 con torres, dos puertas, plaza con fuente, ocho casas, una catedral con campanario y una sucursal de Mitsugoshi. Aparece en llanuras, praderas, sabanas y bosques (`/locate structure draxatomic:midgar_capital`).
- Las estructuras se generan con `tools/structures.py` (requiere `pip install nbtlib`).

**Receta del mostrador:** oro – bloque de cuarzo – oro / tablones – esmeralda – tablones / tres tablones.

## Configuración (`world/serverconfig/draxatomic-server.toml`)

- `[mana]` `maxMana`, `manaRegenPerSecond`
- `[whip]` `cost`, `damage`, `range`
- `[dash]` `cost`, `damage`, `distance`
- `[blade_rain]` `cost`, `damage`, `radius`
- `[all_range_atomic]` `cost`, `radius`, `maxTargets`, `blastRadius`, `damage`, `destroyBlocks`
- `[recovery_atomic]` `cost`, `radius`
- `[diablos_cult]` `patrols`, `patrolChance`
- `[atomic]` `cost`, `radius`, `damage`, `destroyBlocks`, `protectContainers`, `chargeSeconds`, `cooldownSeconds`

Los textos (incluida la frase) están en `assets/draxatomic/lang/*.json`.

## Historial

- 0.7.0: Mitsugoshi (tienda, empleado, mostrador y productos) y la capital de Midgar.
- 0.6.0: All-Range Atomic y Recovery Atomic; capucha abierta; el culto pasa a ser el Culto de Diablos y el jefe un Caballero de la Mesa Redonda.
- 0.5.0: Orden del Eclipse — cultistas nocturnos, Sello del Eclipse, jefe de dos fases y Corazón del Eclipse.
- 0.4.0: Shadow Garden — 7 aliadas con roles distintos y el Emblema de la Sombra.
- 0.3.0: traje de slime (4 piezas) con bonos de poder, doble salto y set completo.
- 0.2.0: barra de poder mágico; modos Látigo, Corte instantáneo y Lluvia de cuchillas; el Atomic usa poder en vez de XP.
- 0.1.1: textura de la espada.
- 0.1.0: espada de slime con el Atomic.

## Compilar

Java 25 y `./gradlew build`; el jar queda en `build/libs/`. Con el repositorio en GitHub, el workflow incluido compila en cada push y publica el jar como release.
