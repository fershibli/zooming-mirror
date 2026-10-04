import { requireNativeView } from 'expo';
import type { ViewProps } from 'react-native';

const NativeMirrorView = requireNativeView<ViewProps>('MirrorView');

/** The front camera, full size, zoomed by pinching. Android only. */
export function MirrorView(props: ViewProps) {
  return <NativeMirrorView {...props} />;
}
