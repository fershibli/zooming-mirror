import { getLocales } from 'expo-localization';

import type { SettingKey } from '../store/settings';

type Strings = {
  settings: string;
  close: string;
  options: Record<SettingKey, { title: string; detail: string }>;
  unsupported: string;
  gpuFailed: string;
  gesturesTitle: string;
  gestures: [gesture: string, effect: string][];
  camera: (width: number, height: number, gpu: boolean) => string;
  version: (version: string) => string;
};

const pt: Strings = {
  settings: 'Ajustes',
  close: 'Fechar',
  options: {
    autoLight: {
      title: 'Luz automática',
      detail: 'Com zoom, a exposição segue a área que está na tela.',
    },
    facePriority: {
      title: 'Prioridade de rosto',
      detail: 'A câmera prioriza rostos ao medir a luz.',
    },
    highResolution: {
      title: 'Alta resolução',
      detail: 'Pede até 4K à câmera, para o zoom ter mais detalhe. Desligue se a imagem travar.',
    },
    gpuSharpening: {
      title: 'Nitidez (GPU)',
      detail: 'Experimental. Amplia com filtro bicúbico e realça as bordas.',
    },
    trueView: {
      title: 'Como os outros te veem',
      detail: 'Mostra a imagem sem espelhar.',
    },
  },
  unsupported: 'Não disponível neste aparelho',
  gpuFailed: 'A GPU não iniciou neste aparelho; o modo padrão continua valendo.',
  gesturesTitle: 'Gestos',
  gestures: [
    ['Pinça', 'zoom de 1× a 10×'],
    ['Um dedo', 'move a imagem ampliada'],
    ['Toque duplo', 'vai para 1× e volta'],
    ['Toque longo', 'congela e descongela'],
    ['Borda direita, na vertical', 'clareia ou escurece'],
    ['Borda direita, para dentro', 'mostra a engrenagem'],
  ],
  camera: (width, height, gpu) => `Câmera ${width}×${height} · ${gpu ? 'GPU' : 'padrão'}`,
  version: (version) => `Versão ${version}`,
};

const en: Strings = {
  settings: 'Settings',
  close: 'Close',
  options: {
    autoLight: {
      title: 'Automatic light',
      detail: 'While zoomed, exposure follows what is on screen.',
    },
    facePriority: {
      title: 'Face priority',
      detail: 'The camera favours faces when it meters the light.',
    },
    highResolution: {
      title: 'High resolution',
      detail: 'Asks the camera for up to 4K, so the zoom has more detail. Turn off if it stutters.',
    },
    gpuSharpening: {
      title: 'Sharpening (GPU)',
      detail: 'Experimental. Enlarges with a bicubic filter and sharpens edges.',
    },
    trueView: {
      title: 'How others see you',
      detail: 'Shows the image without mirroring it.',
    },
  },
  unsupported: 'Not available on this phone',
  gpuFailed: 'The GPU did not start on this phone; the standard mode is still in use.',
  gesturesTitle: 'Gestures',
  gestures: [
    ['Pinch', 'zoom from 1× to 10×'],
    ['One finger', 'moves the zoomed image'],
    ['Double tap', 'to 1× and back'],
    ['Long press', 'freezes and unfreezes'],
    ['Right edge, up or down', 'brighter or darker'],
    ['Right edge, inwards', 'shows the gear'],
  ],
  camera: (width, height, gpu) => `Camera ${width}×${height} · ${gpu ? 'GPU' : 'standard'}`,
  version: (version) => `Version ${version}`,
};

export const strings = getLocales()[0]?.languageCode === 'pt' ? pt : en;
