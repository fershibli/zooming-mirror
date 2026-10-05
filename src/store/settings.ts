import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type Settings = {
  /** Meter the light on what is on screen while zoomed. */
  autoLight: boolean;
  facePriority: boolean;
  /** Ask the camera for up to 4K instead of its default (≤ 1080p). */
  highResolution: boolean;
  /** Experimental GPU path: bicubic upscaling plus sharpening. */
  gpuSharpening: boolean;
  /** Not mirrored: how others see you. */
  trueView: boolean;
};

export type SettingKey = keyof Settings;

type SettingsState = Settings & {
  hydrated: boolean;
  setSetting: (key: SettingKey, value: boolean) => void;
};

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      autoLight: true,
      facePriority: true,
      highResolution: true,
      gpuSharpening: false,
      trueView: false,
      hydrated: false,
      setSetting: (key, value) => set({ [key]: value } as Pick<Settings, typeof key>),
    }),
    {
      name: 'zooming-mirror/settings',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ autoLight, facePriority, highResolution, gpuSharpening, trueView }) => ({
        autoLight,
        facePriority,
        highResolution,
        gpuSharpening,
        trueView,
      }),
      onRehydrateStorage: () => () => {
        useSettings.setState({ hydrated: true });
      },
    },
  ),
);
