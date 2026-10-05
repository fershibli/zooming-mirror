# 15 · Vibração nos limites do zoom

**Status:** Backlog · **Esforço:** baixo

## Objetivo

Sentir quando a pinça chega em 1× ou em 10×, sem olhar o círculo.

## Como

- `performHapticFeedback` com um toque leve (`CLOCK_TICK` / `SEGMENT_TICK`)
  na primeira vez que o zoom encosta num limite durante a pinça.
- Talvez também nos inteiros (2×, 3×, …), mais fraco.

## Pronto quando

- A pinça vibra de leve ao bater em 1× e em 10×, uma vez por encostada.
