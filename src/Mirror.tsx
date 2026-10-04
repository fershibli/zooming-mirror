import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useEffect, useState } from 'react';
import { AppState, Linking, PermissionsAndroid, Pressable, StyleSheet } from 'react-native';

import { MirrorView } from '../modules/mirror-view';

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

  if (access === 'granted') {
    return <MirrorView style={styles.fill} />;
  }
  return <Pressable style={styles.fill} onPress={retry} />;
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
    backgroundColor: '#000',
  },
});
