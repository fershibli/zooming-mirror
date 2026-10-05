import { requireNativeView } from 'expo';
import type { NativeSyntheticEvent, ViewProps } from 'react-native';

/** What the camera turned out to support and deliver on this phone. */
export type CameraInfo = {
  /** Stream size, in the camera's own (landscape) orientation. */
  width: number;
  height: number;
  renderer: 'preview' | 'gpu';
  /** The GPU path failed to start here and the app fell back to the other. */
  gpuFailed: boolean;
  meteringArea: boolean;
  facePriority: boolean;
  exposureCompensation: boolean;
};

export type MirrorViewProps = ViewProps & {
  autoLight: boolean;
  facePriority: boolean;
  highResolution: boolean;
  gpuSharpening: boolean;
  trueView: boolean;
  /** An inward swipe from the right edge, at the zoom circle's height. */
  onSettingsGesture?: () => void;
  onCameraInfo?: (event: NativeSyntheticEvent<CameraInfo>) => void;
};

const NativeMirrorView = requireNativeView<MirrorViewProps>('MirrorView');

/** The front camera, full size, with every gesture of the mirror. Android only. */
export function MirrorView(props: MirrorViewProps) {
  return <NativeMirrorView {...props} />;
}
