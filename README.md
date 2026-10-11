# Manual Limbs

A Fabric mod for Minecraft 26.3 that takes away "press W to walk". You move each leg yourself to take a step, and you move your arms with the mouse to punch, mine and open things, like QWOP-style ragdoll games.

Client-side only: it works in singleplayer and on servers without the server needing the mod. Other players see your normal animations.

> Early version. Built with AI assistance (Claude Code). Expect rough edges.

## Controls

| Action | Default |
|---|---|
| Left leg: swing forward / push back | `Q` / `A` |
| Right leg: swing forward / push back | `W` / `S` |
| Walk backwards (hold) | `Left Ctrl` |
| Move left arm / right arm | hold `Left Mouse` / `Right Mouse` and move the mouse |
| Grab / use with the hand | `Space` |
| Toggle the mod (back to normal controls) | `M` |
| Toggle falling over | `B` |
| Toggle the limb HUD | `H` |

Everything can be rebound under Options → Controls → Key Binds → Manual Limbs.

## How it plays

- **Walking.** A leg is in the air while you hold its swing key and plants when you let go. Pushing a planted leg back drags your body forward. Hold `Q`+`S`, then `W`+`A`, and keep alternating.
- **Jumping.** Press `A` and `S` at the same moment with both feet planted. Keep them held to lean forward through the air and land on a ledge.
- **Falling over.** Lifting both legs at once, standing on one leg too long, or getting both feet far ahead of or behind your body knocks you down. Mash the leg keys to get up.
- **Swimming.** Kick your legs in water to swim toward where you look.
- **Hitting.** Raise an arm until the hand touches a block or mob, then flick the mouse so the hand moves fast. Each hit keeps mining going for about a second, so keep swinging.
- **Using.** Rest a hand on a chest, crafting table, door or any block and press `Space`. With nothing under your hand, `Space` uses the held item (eating, bows).
- **Camera.** The mouse looks around only while no arm button is held. First person shows your real body; third person (`F5`) makes the legs easier to see.
- Normal controls come back while creative-flying, riding, or gliding with an elytra.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the Manual Limbs jar in your `mods` folder.

## Build

Needs Java 25.

```
./gradlew build
```

The jar is written to `build/libs/`.

## License

MIT
