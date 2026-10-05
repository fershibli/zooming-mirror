# 16 · Zoom pelos botões de volume

**Status:** Backlog · **Esforço:** médio

## Objetivo

Dar zoom com uma mão só, segurando o celular.

## Como

- Volume + aproxima e volume − afasta, em passos (por exemplo ×1,25), centrado
  na tela e com o círculo do zoom.
- Os eventos de tecla chegam na Activity, não na view: precisa de um listener
  de ciclo de vida do Expo ou de um config plugin que encaminhe as teclas.
- Ajuste para ligar/desligar, porque ele "rouba" o controle de volume.

## Pronto quando

- Com o ajuste ligado, os botões de volume mudam o zoom e não o volume.
