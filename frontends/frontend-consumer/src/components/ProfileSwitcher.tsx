import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { FamilyMember } from '../types';
import { colors, font, radius, spacing } from '../theme';
import { relationLabel } from '../utils';
import { Avatar } from './Avatar';

interface ProfileSwitcherProps {
  members: readonly FamilyMember[];
  selectedId: string;
  onSelect: (id: string) => void;
}

/** Horizontal, scrollable family picker. Exposed to assistive tech as a tab list. */
export function ProfileSwitcher({ members, selectedId, onSelect }: ProfileSwitcherProps) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      accessibilityRole="tablist"
      contentContainerStyle={styles.row}>
      {members.map((member) => {
        const selected = member.id === selectedId;
        const relation = relationLabel(member.relation);
        return (
          <Pressable
            key={member.id}
            onPress={() => onSelect(member.id)}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={`${member.firstName} ${member.lastName}, ${relation}`}
            accessibilityHint="Zeigt das Impfdossier dieser Person an"
            style={({ pressed }) => [
              styles.chip,
              selected && styles.chipSelected,
              pressed && styles.chipPressed,
            ]}>
            <Avatar firstName={member.firstName} lastName={member.lastName} size={32} />
            <View style={styles.chipText}>
              <Text
                numberOfLines={1}
                style={[styles.name, selected && styles.nameSelected]}>
                {member.firstName}
              </Text>
              <Text numberOfLines={1} style={[styles.relation, selected && styles.relationSelected]}>
                {relation}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    gap: spacing.sm,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    minHeight: 48,
  },
  chipSelected: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  chipPressed: {
    opacity: 0.7,
  },
  chipText: {
    paddingRight: spacing.xs,
  },
  name: {
    fontSize: font.body,
    fontWeight: '600',
    color: colors.text,
  },
  nameSelected: {
    color: colors.surface,
  },
  relation: {
    fontSize: font.caption,
    color: colors.textMuted,
  },
  relationSelected: {
    color: colors.primarySoft,
  },
});
