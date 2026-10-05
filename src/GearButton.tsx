import { useEffect, useState } from 'react';
import { Animated, Image, Pressable, StyleSheet } from 'react-native';

import { colors } from './lib/colors';
import { strings } from './lib/strings';

/** How long the gear waits for a tap before it fades away again. */
const VISIBLE_MS = 4000;

type Props = {
  visible: boolean;
  onPress: () => void;
  onTimeout: () => void;
};

/**
 * The only way into the settings: it shows up on the right edge, at the zoom
 * circle's height, after an inward swipe there, and leaves on its own.
 */
export function GearButton({ visible, onPress, onTimeout }: Props) {
  const [opacity] = useState(() => new Animated.Value(0));

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: visible ? 1 : 0,
      duration: visible ? 120 : 200,
      useNativeDriver: true,
    }).start();
    if (!visible) {
      return;
    }
    const timer = setTimeout(onTimeout, VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [visible, opacity, onTimeout]);

  return (
    <Animated.View
      pointerEvents={visible ? 'box-none' : 'none'}
      style={[styles.anchor, { opacity }]}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={strings.settings}
        hitSlop={12}
        onPress={onPress}
        style={styles.circle}
      >
        <Image source={require('../assets/gear.png')} style={styles.icon} />
      </Pressable>
    </Animated.View>
  );
}

const SIZE = 56;

const styles = StyleSheet.create({
  anchor: {
    position: 'absolute',
    right: 16,
    // Same height as the native zoom circle: its centre sits 27% up.
    top: '73%',
    marginTop: -SIZE / 2,
  },
  circle: {
    width: SIZE,
    height: SIZE,
    borderRadius: SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.bubble,
    borderWidth: 1.5,
    borderColor: colors.bubbleBorder,
  },
  icon: {
    width: 32,
    height: 32,
  },
});
