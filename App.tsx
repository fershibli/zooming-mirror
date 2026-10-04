import { NavigationBar } from 'expo-navigation-bar';
import { StatusBar } from 'expo-status-bar';

import { Mirror } from './src/Mirror';

export default function App() {
  return (
    <>
      <StatusBar hidden />
      <NavigationBar hidden />
      <Mirror />
    </>
  );
}
