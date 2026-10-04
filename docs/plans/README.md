# Planos

Cada arquivo é um plano: o que fazer, como, riscos e quando está pronto.
**Aprovado** entra na próxima versão; **Backlog** fica guardado para depois.

| #   | Plano                                                      | Status   | Esforço |
| --- | ---------------------------------------------------------- | -------- | ------- |
| 01  | [Alta resolução na câmera](01-alta-resolucao.md)           | Aprovado | Baixo   |
| 02  | [Nitidez por GPU](02-nitidez-gpu.md)                       | Aprovado | Alto    |
| 03  | [Exposição que acompanha o zoom](03-exposicao.md)          | Aprovado | Médio   |
| 04  | [Tela de ajustes](04-ajustes.md)                           | Aprovado | Médio   |
| 05  | [Toque duplo vai e volta](05-toque-duplo.md)               | Aprovado | Baixo   |
| 06  | [Como os outros te veem](06-como-os-outros-te-veem.md)     | Aprovado | Baixo   |
| 07  | [Congelar a imagem](07-congelar-imagem.md)                 | Aprovado | Médio   |
| 08  | [APK menor](08-apk-menor.md)                               | Aprovado | Baixo   |
| 09  | [Cache do Gradle no build do APK](09-cache-gradle.md)      | Aprovado | Baixo   |
| 10  | [Zoom híbrido](10-zoom-hibrido.md)                         | Backlog  | Alto    |
| 11  | [Super-resolução com IA](11-super-resolucao-ia.md)         | Backlog  | Alto    |
| 12  | [Luz de preenchimento](12-luz-de-preenchimento.md)         | Backlog  | Baixo   |
| 13  | [Inércia no arraste](13-inercia-no-arraste.md)             | Backlog  | Baixo   |
| 14  | [Lembrar zoom e posição](14-lembrar-zoom.md)               | Backlog  | Baixo   |
| 15  | [Vibração nos limites do zoom](15-vibracao-nos-limites.md) | Backlog  | Baixo   |
| 16  | [Zoom pelos botões de volume](16-zoom-pelo-volume.md)      | Backlog  | Médio   |
| 17  | [APK em todo PR](17-apk-nos-prs.md)                        | Backlog  | Baixo   |
| 18  | [Testes da matemática do zoom](18-testes-do-zoom.md)       | Backlog  | Baixo   |

## Gestos, depois dos aprovados

Como o app não tem botões, os gestos são a interface. Este é o mapa completo
que os planos aprovados formam juntos:

| Gesto                        | Onde                                       | Faz                                         |
| ---------------------------- | ------------------------------------------ | ------------------------------------------- |
| Pinça                        | qualquer lugar                             | zoom de 1× a 10× (mostra o círculo do zoom) |
| Arrastar com um dedo         | qualquer lugar                             | move a imagem ampliada                      |
| Toque duplo                  | qualquer lugar                             | vai para 1× e volta ao zoom anterior        |
| Toque longo                  | qualquer lugar                             | congela / descongela a imagem               |
| Deslizar na vertical         | borda direita                              | clareia / escurece (exposição manual)       |
| Arrastar de fora para dentro | borda direita, à altura do círculo do zoom | mostra a engrenagem dos ajustes             |
