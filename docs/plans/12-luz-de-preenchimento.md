# 12 · Luz de preenchimento

**Status:** Feito · **Esforço:** baixo

## Objetivo

Iluminar o rosto no escuro usando a própria tela, como os "ring lights" dos apps
de espelho.

## Como

- Brilho da tela no máximo enquanto ativo (`screenBrightness = 1`, só nesta
  janela) e uma borda branca em volta da imagem, de 14% do lado menor da tela;
  a imagem encolhe para caber dentro da borda e fica recortada nela, para o
  zoom nunca cobrir a luz.
- Ligada e desligada pelo atalho **Luz**, no círculo de atalhos (plano 20). Não
  fica salva: o app sempre abre com ela desligada.

## Pronto quando

- Num quarto escuro, ligar a luz deixa o rosto visivelmente iluminado, e
  desligar devolve o brilho anterior.
