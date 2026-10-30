import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AppState,
  type LayoutChangeEvent,
  Linking,
  type NativeSyntheticEvent,
  PermissionsAndroid,
  Pressable,
  StyleSheet,
  View,
} from 'react-native';
import { useShallow } from 'zustand/react/shallow';

import {
  type CameraInfo,
  MirrorView,
  type MirrorViewRef,
  type ShortcutsDrag,
} from '../modules/mirror-view';
import { Settings } from './Settings';
import { type Shortcut, ShortcutWheel, type Size, shortcutAt } from './ShortcutWheel';
import { pickSettings, SHARPENING_TYPES, useSettings } from './store/settings';

type Access = 'checking' | 'granted' | 'denied' | 'blocked';

const CAMERA = PermissionsAndroid.PERMISSIONS.CAMERA;

async function askForCamera(): Promise<Access> {
  const result = await PermissionsAndroid.request(CAMERA);
  if (result === PermissionsAndroid.RESULTS.GRANTED) {
    return 'granted';
  }
  return result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN ? 'blocked' : 'denied';
}

export function Mirror() {
  useKeepAwake();

  const [access, setAccess] = useState<Access>('checking');
  const [wheelOpen, setWheelOpen] = useState(false);
  const [hovered, setHovered] = useState<Shortcut | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [camera, setCamera] = useState<CameraInfo | null>(null);
  const [size, setSize] = useState<Size>({ width: 0, height: 0 });
  // Not saved: the light and the freeze are for the moment.
  const [fillLight, setFillLight] = useState(false);
  const [frozen, setFrozen] = useState(false);
  const mirror = useRef<MirrorViewRef>(null);
  const settings = useSettings(useShallow(pickSettings));
  const setSetting = useSettings((state) => state.setSetting);
  const sharpeningAvailable = settings.gpuSharpening && !camera?.gpuFailed;
  // The camera waits for the saved settings, so it starts once, as they say.
  const hydrated = useSettings((state) => state.hydrated);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const granted = await PermissionsAndroid.check(CAMERA);
      const next = granted ? 'granted' : await askForCamera();
      if (!cancelled) {
        setAccess(next);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Coming back from the system settings with the permission granted.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void PermissionsAndroid.check(CAMERA).then((granted) => {
          if (granted) {
            setAccess('granted');
          }
        });
      }
    });
    return () => subscription.remove();
  }, []);

  // No UI at all: without the permission the screen stays black and a tap asks
  // again, or opens the app settings once Android stops showing the prompt.
  const retry = useCallback(() => {
    if (access === 'blocked') {
      void Linking.openSettings();
    } else if (access === 'denied') {
      void askForCamera().then(setAccess);
    }
  }, [access]);

  const openWheel = useCallback(() => setWheelOpen(true), []);
  const closeWheel = useCallback(() => {
    setWheelOpen(false);
    setHovered(null);
  }, []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);

  const pick = useCallback(
    (shortcut: Shortcut) => {
      switch (shortcut) {
        case 'fillLight':
          setFillLight((on) => !on);
          closeWheel();
          break;
        case 'sharpening': {
          // Natural → Detail → Smooth → Natural; the wheel stays to go on.
          const index = SHARPENING_TYPES.indexOf(settings.sharpening);
          setSetting('sharpening', SHARPENING_TYPES[(index + 1) % SHARPENING_TYPES.length]);
          break;
        }
        case 'freeze':
          void mirror.current?.toggleFreeze();
          closeWheel();
          break;
        case 'settings':
          closeWheel();
          setSettingsOpen(true);
          break;
      }
    },
    [closeWheel, setSetting, settings.sharpening],
  );

  // The finger that opened the wheel can go on to a shortcut and let go on it.
  const onShortcutsDrag = useCallback(
    (event: NativeSyntheticEvent<ShortcutsDrag>) => {
      const { x, y, end, cancelled } = event.nativeEvent;
      const hit = cancelled ? null : shortcutAt(x, y, size);
      if (!end) {
        setHovered(hit);
        return;
      }
      setHovered(null);
      if (hit && (hit !== 'sharpening' || sharpeningAvailable)) {
        pick(hit);
      }
    },
    [pick, sharpeningAvailable, size],
  );

  const onLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize({ width, height });
  }, []);

  if (access === 'granted' && hydrated) {
    return (
      <View style={styles.fill} onLayout={onLayout}>
        <MirrorView
          ref={mirror}
          style={StyleSheet.absoluteFill}
          {...settings}
          fillLight={fillLight}
          onShortcutsGesture={openWheel}
          onShortcutsDrag={onShortcutsDrag}
          onFreezeChange={(event) => setFrozen(event.nativeEvent.frozen)}
          onCameraInfo={(event) => setCamera(event.nativeEvent)}
        />
        <ShortcutWheel
          visible={wheelOpen && !settingsOpen && size.width > 0}
          size={size}
          hovered={hovered}
          fillLight={fillLight}
          frozen={frozen}
          sharpening={settings.sharpening}
          sharpeningAvailable={sharpeningAvailable}
          onPick={pick}
          onClose={closeWheel}
        />
        <Settings visible={settingsOpen} camera={camera} onClose={closeSettings} />
      </View>
    );
  }
  return <Pressable style={styles.fill} onPress={retry} />;
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
    backgroundColor: '#000',
  },
});
