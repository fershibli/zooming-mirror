import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

/** Levels of `lightSensitivity`, 0 to 4; the highest never holds the light. */
export const SENSITIVITY_LEVELS = 5;

/** What the GPU path does after enlarging, in the order the settings show. */
export const SHARPENING_TYPES = ['smooth', 'natural', 'detail'] as const;
export type SharpeningType = (typeof SHARPENING_TYPES)[number];

export type Settings = {
  /** The camera fits the light to what is on screen; off, it stays locked. */
  autoLight: boolean;
  /**
   * How far the light on screen has to change before the camera fits it again:
   * 0 only for big changes, 4 all the time (the camera's own behaviour).
   */
  lightSensitivity: number;
  facePriority: boolean;
  /** Ask the camera for up to 4K instead of its default (≤ 1080p). */
  highResolution: boolean;
  /** Experimental GPU path: bicubic upscaling plus sharpening. */
  gpuSharpening: boolean;
  /** Smoother skin, the default sharpening, or strong detail for shaving. */
  sharpening: SharpeningType;
  /** Not mirrored: how others see you. */
  trueView: boolean;
  /** A light tick when the pinch reaches 1× or the top zoom. */
  zoomHaptics: boolean;
  /** A quick one-finger drag keeps sliding a little after the finger leaves. */
  dragInertia: boolean;
  /** Opens at the zoom and position the app was left at. */
  rememberZoom: boolean;
  /** The volume keys zoom in and out instead of changing the volume. */
  volumeZoom: boolean;
};

/** Everything a fresh install starts with; saved settings are merged over it. */
const DEFAULTS: Settings = {
  autoLight: true,
  lightSensitivity: 2,
  facePriority: true,
  highResolution: true,
  gpuSharpening: false,
  sharpening: 'natural',
  trueView: false,
  zoomHaptics: true,
  dragInertia: true,
  rememberZoom: true,
  volumeZoom: true,
};

const KEYS = Object.keys(DEFAULTS) as (keyof Settings)[];

/** Just the settings out of the store's state, for saving and for the camera view. */
export function pickSettings(state: Settings): Settings {
  return Object.fromEntries(KEYS.map((key) => [key, state[key]])) as Settings;
}

/** The settings that are a plain switch. */
export type ToggleKey = {
  [K in keyof Settings]: Settings[K] extends boolean ? K : never;
}[keyof Settings];

type SettingsState = Settings & {
  hydrated: boolean;
  setSetting: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
};

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      ...DEFAULTS,
      hydrated: false,
      setSetting: (key, value) => set({ [key]: value } as Pick<Settings, typeof key>),
    }),
    {
      name: 'zooming-mirror/settings',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => pickSettings(state),
      onRehydrateStorage: () => () => {
        useSettings.setState({ hydrated: true });
      },
    },
  ),
);
