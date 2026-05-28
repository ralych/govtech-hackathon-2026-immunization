import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ImpfpassScreen } from './src/screens/ImpfpassScreen';

export default function App() {
  return (
    <SafeAreaProvider>
      <ImpfpassScreen />
    </SafeAreaProvider>
  );
}
