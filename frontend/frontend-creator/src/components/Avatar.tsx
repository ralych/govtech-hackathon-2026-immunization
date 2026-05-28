import { StyleSheet, Text, View } from 'react-native';

import { avatarPalette } from '../theme';
import { initials } from '../utils';

interface AvatarProps {
  firstName: string;
  lastName: string;
  size?: number;
}

/**
 * Initials avatar with a deterministic colour. Decorative only — the person's
 * name is always shown alongside, so it is hidden from screen readers.
 */
export function Avatar({ firstName, lastName, size = 40 }: AvatarProps) {
  const { bg, fg } = avatarPalette(firstName + lastName);
  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[
        styles.avatar,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: bg },
      ]}>
      <Text style={[styles.text, { color: fg, fontSize: size * 0.38 }]}>
        {initials(firstName, lastName)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  avatar: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    fontWeight: '700',
  },
});
