import { getLocales } from 'expo-localization';

import type { ToggleKey } from '../store/settings';

type Strings = {
  settings: string;
  close: string;
  options: Record<ToggleKey, { title: string; detail: string }>;
  sensitivity: {
    title: string;
    /** One per level, lowest first: its name and what it does. */
    levels: [name: string, detail: string][];
    onlyWithAutoLight: string;
  };
  zoomTitle: string;
  unsupported: string;
  gpuFailed: string;
  gesturesTitle: string;
  gestures: [gesture: string, effect: string][];
  camera: (width: number, height: number, gpu: boolean) => string;
  lightHold: (lock: boolean) => string;
  version: (version: string) => string;
};

const pt: Strings = {
  settings: 'Ajustes',
  close: 'Fechar',
  options: {
    autoLight: {
      title: 'Luz automática',
      detail:
        'A câmera ajusta a luz ao que está na tela. Desligada, a luz fica travada e só o deslizar na borda direita a muda.',
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
    volumeZoom: {
      title: 'Zoom pelo volume',
      detail: 'Os botões de volume aproximam e afastam. Desligado, voltam a mudar o volume.',
    },
    dragInertia: {
      title: 'Inércia no arraste',
      detail: 'Depois de um arraste rápido, a imagem continua deslizando um pouco.',
    },
    rememberZoom: {
      title: 'Lembrar o zoom',
      detail: 'Abre o app no zoom e na posição em que ele ficou.',
    },
    zoomHaptics: {
      title: 'Vibrar nos limites',
      detail: 'Um toque leve quando a pinça chega a 1× ou a 10×.',
    },
  },
  sensitivity: {
    title: 'Sensibilidade',
    levels: [
      ['Mínima', 'Só reajusta quando a luz muda muito, como ao apagar uma lâmpada.'],
      ['Baixa', 'Reajusta com mudanças grandes de luz.'],
      ['Média', 'Segura a luz quando você se aproxima ou se afasta.'],
      ['Alta', 'Reajusta com mudanças pequenas de luz.'],
      ['Máxima', 'Reajusta o tempo todo, como a câmera faz sozinha.'],
    ],
    onlyWithAutoLight: 'Só com a luz automática ligada.',
  },
  zoomTitle: 'Zoom',
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
    ['Botões de volume', 'aproximam e afastam'],
    ['Celular de cabeça para baixo', 'a imagem vira junto'],
  ],
  camera: (width, height, gpu) => `Câmera ${width}×${height} · ${gpu ? 'GPU' : 'padrão'}`,
  lightHold: (lock) => `Luz segurada ${lock ? 'pela trava da câmera' : 'pela compensação'}`,
  version: (version) => `Versão ${version}`,
};

const en: Strings = {
  settings: 'Settings',
  close: 'Close',
  options: {
    autoLight: {
      title: 'Automatic light',
      detail:
        'The camera fits the light to what is on screen. Off, the light stays locked and only the right-edge slide changes it.',
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
    volumeZoom: {
      title: 'Zoom with the volume keys',
      detail: 'The volume keys zoom in and out. Off, they change the volume again.',
    },
    dragInertia: {
      title: 'Drag inertia',
      detail: 'After a quick drag, the image keeps sliding a little.',
    },
    rememberZoom: {
      title: 'Remember the zoom',
      detail: 'Opens the app at the zoom and position it was left at.',
    },
    zoomHaptics: {
      title: 'Tick at the limits',
      detail: 'A light tick when the pinch reaches 1× or 10×.',
    },
  },
  sensitivity: {
    title: 'Sensitivity',
    levels: [
      ['Lowest', 'Adjusts only when the light changes a lot, like a lamp going off.'],
      ['Low', 'Adjusts for big changes in light.'],
      ['Medium', 'Holds the light as you move closer or further away.'],
      ['High', 'Adjusts for small changes in light.'],
      ['Highest', 'Adjusts all the time, as the camera does on its own.'],
    ],
    onlyWithAutoLight: 'Only with automatic light on.',
  },
  zoomTitle: 'Zoom',
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
    ['Volume keys', 'zoom in and out'],
    ['Phone upside down', 'the picture turns over too'],
  ],
  camera: (width, height, gpu) => `Camera ${width}×${height} · ${gpu ? 'GPU' : 'standard'}`,
  lightHold: (lock) => `Light held ${lock ? "by the camera's lock" : 'by the compensation'}`,
  version: (version) => `Version ${version}`,
};

export const strings = getLocales()[0]?.languageCode === 'pt' ? pt : en;
