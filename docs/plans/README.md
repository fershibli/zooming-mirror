# Planos

Cada arquivo é um plano: o que fazer, como, riscos e quando está pronto.
**Feito** já está no app, **Aprovado** entra na próxima versão e **Backlog**
fica guardado para depois.

| #   | Plano                                                         | Status  | Esforço |
| --- | ------------------------------------------------------------- | ------- | ------- |
| 01  | [Alta resolução na câmera](01-alta-resolucao.md)              | Feito   | Baixo   |
| 02  | [Nitidez por GPU](02-nitidez-gpu.md)                          | Feito   | Alto    |
| 03  | [Exposição que acompanha o zoom](03-exposicao.md)             | Feito   | Médio   |
| 04  | [Tela de ajustes](04-ajustes.md)                              | Feito   | Médio   |
| 05  | [Toque duplo vai e volta](05-toque-duplo.md)                  | Feito   | Baixo   |
| 06  | [Como os outros te veem](06-como-os-outros-te-veem.md)        | Feito   | Baixo   |
| 07  | [Congelar a imagem](07-congelar-imagem.md)                    | Feito   | Médio   |
| 08  | [APK menor](08-apk-menor.md)                                  | Feito   | Baixo   |
| 09  | [Cache do Gradle no build do APK](09-cache-gradle.md)         | Feito   | Baixo   |
| 10  | [Zoom híbrido](10-zoom-hibrido.md)                            | Backlog | Alto    |
| 11  | [Super-resolução com IA](11-super-resolucao-ia.md)            | Backlog | Alto    |
| 12  | [Luz de preenchimento](12-luz-de-preenchimento.md)            | Feito   | Baixo   |
| 13  | [Inércia no arraste](13-inercia-no-arraste.md)                | Feito   | Baixo   |
| 14  | [Lembrar zoom e posição](14-lembrar-zoom.md)                  | Feito   | Baixo   |
| 15  | [Vibração nos limites do zoom](15-vibracao-nos-limites.md)    | Feito   | Baixo   |
| 16  | [Zoom pelos botões de volume](16-zoom-pelo-volume.md)         | Feito   | Médio   |
| 17  | [APK em todo PR](17-apk-nos-prs.md)                           | Backlog | Baixo   |
| 18  | [Testes da matemática do zoom](18-testes-do-zoom.md)          | Backlog | Baixo   |
| 19  | [Tipo de nitidez](19-tipo-de-nitidez.md)                      | Feito   | Médio   |
| 20  | [Círculo de atalhos](20-circulo-de-atalhos.md)                | Feito   | Médio   |
| 21  | [Verificar a barba com IA no aparelho](21-verificar-barba.md) | Backlog | Alto    |

## Gestos

Como o app não tem botões, os gestos são a interface. Este é o mapa completo
do que já está no app:

| Gesto                        | Onde                                       | Faz                                         |
| ---------------------------- | ------------------------------------------ | ------------------------------------------- |
| Pinça                        | qualquer lugar                             | zoom de 1× a 10× (mostra o círculo do zoom) |
| Arrastar com um dedo         | qualquer lugar                             | move a imagem ampliada, com inércia         |
| Toque duplo                  | qualquer lugar                             | vai para 1× e volta ao zoom anterior        |
| Toque longo                  | qualquer lugar                             | congela / descongela a imagem               |
| Deslizar na vertical         | borda direita                              | clareia / escurece (exposição manual)       |
| Arrastar de fora para dentro | borda direita, à altura do círculo do zoom | abre o círculo de atalhos                   |
| Botões de volume             | —                                          | aproximam e afastam                         |
