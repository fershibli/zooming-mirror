# 04 · Tela de ajustes

**Status:** Aprovado · **Esforço:** médio

## Objetivo

Com mais recursos, várias coisas passam a ser escolha do usuário. A tela de
ajustes concentra essas escolhas sem pôr nenhum botão permanente na imagem.

## Como

### Engrenagem escondida

- Arrastar **de fora para dentro na borda direita**, à altura do círculo do
  zoom (27% acima da base), mostra uma engrenagem em pixel art naquela altura,
  num círculo translúcido igual ao do zoom.
- Ela some sozinha depois de ~4 s se não for tocada. Tocar abre os ajustes.
- No Android com navegação por gestos, esse arraste é o mesmo do "voltar". O
  app reserva uma faixa de até 200dp da borda direita naquela altura
  (`setSystemGestureExclusionRects`, o máximo que o Android permite) para o
  gesto chegar ao app. Fora dessa faixa o "voltar" continua funcionando.

### Modal

- Tela cheia escura com 90% de opacidade, a imagem da câmera aparece de leve
  por trás. Fecha pelo ✕ ou pelo voltar do Android.
- Ajustes (salvos no aparelho, valem na próxima abertura):

| Ajuste                      | Padrão    | Plano |
| --------------------------- | --------- | ----- |
| Luz automática              | ligado    | 03    |
| Prioridade de rosto         | ligado    | 03    |
| Alta resolução              | ligado    | 01    |
| Nitidez (GPU, experimental) | desligado | 02    |
| Como os outros te veem      | desligado | 06    |

- Ajuste que o aparelho não suporta aparece apagado, com "não disponível neste
  aparelho".
- Rodapé com o que a câmera entregou (resolução, caminho de renderização) e a
  versão do app.
- Textos em português ou inglês conforme o idioma do aparelho.

### Implementação

- Estado em JS (zustand + AsyncStorage, como no sound-balancer-app), passado
  como props para a view nativa.
- A view nativa avisa o JS por eventos: `onSettingsGesture` (o arraste da
  engrenagem) e `onCameraInfo` (o que a câmera suporta e entregou).

## Riscos

- A faixa de exclusão de gesto é limitada a 200dp por borda; o arraste da
  engrenagem só funciona perto da altura dela.

## Pronto quando

- O arraste na borda direita mostra a engrenagem; tocar abre o modal; os
  ajustes mudam o comportamento na hora e continuam valendo depois de fechar e
  abrir o app.
