import Constants from 'expo-constants';
import { useEffect, useState } from 'react';
import {
  Animated,
  BackHandler,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

import type { CameraInfo } from '../modules/mirror-view';
import { colors } from './lib/colors';
import { strings } from './lib/strings';
import { type SettingKey, useSettings } from './store/settings';

const ORDER: SettingKey[] = [
  'autoLight',
  'facePriority',
  'highResolution',
  'gpuSharpening',
  'trueView',
];

/** False only when the camera said it cannot; unknown counts as available. */
function isAvailable(key: SettingKey, camera: CameraInfo | null): boolean {
  if (!camera) {
    return true;
  }
  switch (key) {
    case 'autoLight':
      return camera.meteringArea || camera.exposureCompensation;
    case 'facePriority':
      return camera.facePriority;
    case 'gpuSharpening':
      return !camera.gpuFailed;
    default:
      return true;
  }
}

type Props = {
  visible: boolean;
  camera: CameraInfo | null;
  onClose: () => void;
};

/** Full-screen settings at 90% opacity, over the still-running camera. */
export function Settings({ visible, camera, onClose }: Props) {
  const [opacity] = useState(() => new Animated.Value(0));

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: visible ? 1 : 0,
      duration: 160,
      useNativeDriver: true,
    }).start();
  }, [visible, opacity]);

  useEffect(() => {
    if (!visible) {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [visible, onClose]);

  const version = Constants.expoConfig?.version;

  return (
    // Stays mounted so it can fade out; while closed it takes no touches and is
    // hidden from screen readers.
    <Animated.View
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      pointerEvents={visible ? 'auto' : 'none'}
      style={[styles.overlay, { opacity }]}
    >
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <Image source={require('../assets/gear.png')} style={styles.headerIcon} />
          <Text style={styles.title}>{strings.settings}</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={strings.close}
            hitSlop={16}
            onPress={onClose}
          >
            <Text style={styles.close}>✕</Text>
          </Pressable>
        </View>

        {ORDER.map((key) => (
          <SettingRow key={key} setting={key} available={isAvailable(key, camera)} />
        ))}
        {camera?.gpuFailed ? <Text style={styles.warning}>{strings.gpuFailed}</Text> : null}

        <Text style={styles.section}>{strings.gesturesTitle}</Text>
        {strings.gestures.map(([gesture, effect]) => (
          <View key={gesture} style={styles.gesture}>
            <Text style={styles.gestureName}>{gesture}</Text>
            <Text style={styles.gestureEffect}>{effect}</Text>
          </View>
        ))}

        <View style={styles.footer}>
          {camera && camera.width > 0 ? (
            <Text style={styles.footerText}>
              {strings.camera(camera.width, camera.height, camera.renderer === 'gpu')}
            </Text>
          ) : null}
          {version ? <Text style={styles.footerText}>{strings.version(version)}</Text> : null}
        </View>
      </ScrollView>
    </Animated.View>
  );
}

function SettingRow({ setting, available }: { setting: SettingKey; available: boolean }) {
  const value = useSettings((state) => state[setting]);
  const setSetting = useSettings((state) => state.setSetting);
  const { title, detail } = strings.options[setting];
  const toggle = () => setSetting(setting, !value);

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value && available, disabled: !available }}
      disabled={!available}
      onPress={toggle}
      style={[styles.row, !available && styles.rowDisabled]}
    >
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>{title}</Text>
        <Text style={styles.rowDetail}>{available ? detail : strings.unsupported}</Text>
      </View>
      <Switch
        disabled={!available}
        onValueChange={toggle}
        thumbColor={value && available ? colors.gold : colors.mist}
        trackColor={{ false: colors.slate, true: colors.orange }}
        value={value && available}
      />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: colors.overlay,
  },
  content: {
    paddingTop: 56,
    paddingHorizontal: 24,
    paddingBottom: 48,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerIcon: {
    width: 32,
    height: 32,
    marginRight: 12,
  },
  title: {
    flex: 1,
    color: colors.white,
    fontSize: 24,
    fontWeight: '700',
  },
  close: {
    color: colors.mist,
    fontSize: 22,
    paddingHorizontal: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.slate,
  },
  rowDisabled: {
    opacity: 0.5,
  },
  rowText: {
    flex: 1,
    paddingRight: 16,
  },
  rowTitle: {
    color: colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  rowDetail: {
    color: colors.mist,
    fontSize: 13,
    marginTop: 3,
  },
  warning: {
    color: colors.orange,
    fontSize: 13,
    marginTop: 12,
  },
  section: {
    color: colors.gold,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginTop: 32,
    marginBottom: 8,
  },
  gesture: {
    flexDirection: 'row',
    paddingVertical: 6,
  },
  gestureName: {
    flex: 1,
    color: colors.white,
    fontSize: 14,
  },
  gestureEffect: {
    flex: 1,
    color: colors.mist,
    fontSize: 14,
  },
  footer: {
    marginTop: 32,
    gap: 4,
  },
  footerText: {
    color: colors.steel,
    fontSize: 12,
  },
});
