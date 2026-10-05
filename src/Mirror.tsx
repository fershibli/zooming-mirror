import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useEffect, useState } from 'react';
import { AppState, Linking, PermissionsAndroid, Pressable, StyleSheet, View } from 'react-native';
import { useShallow } from 'zustand/react/shallow';

import { type CameraInfo, MirrorView } from '../modules/mirror-view';
import { GearButton } from './GearButton';
import { Settings } from './Settings';
import { useSettings } from './store/settings';

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
  const [gearVisible, setGearVisible] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [camera, setCamera] = useState<CameraInfo | null>(null);
  const settings = useSettings(
    useShallow(({ autoLight, facePriority, highResolution, gpuSharpening, trueView }) => ({
      autoLight,
      facePriority,
      highResolution,
      gpuSharpening,
      trueView,
    })),
  );
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

  const showGear = useCallback(() => setGearVisible(true), []);
  const hideGear = useCallback(() => setGearVisible(false), []);
  const openSettings = useCallback(() => {
    setGearVisible(false);
    setSettingsOpen(true);
  }, []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);

  if (access === 'granted' && hydrated) {
    return (
      <View style={styles.fill}>
        <MirrorView
          style={StyleSheet.absoluteFill}
          {...settings}
          onSettingsGesture={showGear}
          onCameraInfo={(event) => setCamera(event.nativeEvent)}
        />
        <GearButton
          visible={gearVisible && !settingsOpen}
          onPress={openSettings}
          onTimeout={hideGear}
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
