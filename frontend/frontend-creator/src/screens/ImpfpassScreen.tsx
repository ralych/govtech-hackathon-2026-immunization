import { useMemo, useState } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { DiseaseGroup } from '../types';
import { family } from '../data';
import { colors, font, radius, spacing } from '../theme';
import { calcAge, groupByDisease, relationLabel } from '../utils';
import { Avatar } from '../components/Avatar';
import { ProfileSwitcher } from '../components/ProfileSwitcher';
import { VaccinationGroup } from '../components/VaccinationGroup';

export function ImpfpassScreen() {
  const insets = useSafeAreaInsets();
  const [selectedId, setSelectedId] = useState(family[0].id);

  const member = useMemo(
    () => family.find((m) => m.id === selectedId) ?? family[0],
    [selectedId],
  );
  const groups = useMemo(() => groupByDisease(member.vaccinations), [member]);

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />

      <View style={[styles.appBar, { paddingTop: insets.top + spacing.sm }]}>
        <View style={styles.brand}>
          <SwissCross />
          <View>
            <Text accessibilityRole="header" style={styles.brandTitle}>
              Impfdossier <Text style={styles.brandAccent}>CH</Text>
            </Text>
            <Text style={styles.brandSub}>Elektronisches Impfdossier</Text>
          </View>
        </View>
        <ProfileSwitcher members={family} selectedId={selectedId} onSelect={setSelectedId} />
      </View>

      <FlatList<DiseaseGroup>
        data={groups}
        keyExtractor={(group) => group.disease}
        renderItem={({ item }) => <VaccinationGroup group={item} />}
        ListHeaderComponent={<ProfileSummary member={member} groupCount={groups.length} />}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>Keine Impfungen erfasst</Text>
            <Text style={styles.emptySub}>Für {member.firstName} liegen noch keine Einträge vor.</Text>
          </View>
        }
        contentContainerStyle={[
          styles.listContent,
          { paddingBottom: insets.bottom + spacing.xl },
        ]}
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
}

function ProfileSummary({
  member,
  groupCount,
}: {
  member: (typeof family)[number];
  groupCount: number;
}) {
  const age = calcAge(member.dob);
  const total = member.vaccinations.length;
  const sex = member.sex === 'F' ? 'weiblich' : 'männlich';
  return (
    <>
      <View
        style={styles.summary}
        accessible
        accessibilityLabel={`${member.firstName} ${member.lastName}, ${relationLabel(member.relation)}, ${age} Jahre, ${total} Impfungen erfasst`}>
        <Avatar firstName={member.firstName} lastName={member.lastName} size={56} />
        <View style={styles.summaryText}>
          <Text style={styles.summaryName}>
            {member.lastName}, {member.firstName}
          </Text>
          <Text style={styles.summaryMeta}>
            {relationLabel(member.relation)} · {age} Jahre · {sex}
          </Text>
        </View>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{total}</Text>
          <Text style={styles.summaryStatLabel}>Impfungen</Text>
        </View>
      </View>

      <Text accessibilityRole="header" style={styles.sectionTitle}>
        Impfausweis
      </Text>
      <Text style={styles.sectionSub}>
        {groupCount} {groupCount === 1 ? 'Krankheit' : 'Krankheiten'} abgedeckt
      </Text>
    </>
  );
}

/** Minimal Swiss flag mark built from plain views — no image asset needed. */
function SwissCross() {
  return (
    <View style={styles.flag} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <View style={styles.flagBarV} />
      <View style={styles.flagBarH} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  appBar: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingBottom: spacing.xs,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
  },
  brandTitle: {
    fontSize: font.display,
    fontWeight: '700',
    color: colors.text,
  },
  brandAccent: {
    color: colors.swissRed,
  },
  brandSub: {
    fontSize: font.label,
    color: colors.textMuted,
  },
  flag: {
    width: 32,
    height: 32,
    borderRadius: radius.sm,
    backgroundColor: colors.swissRed,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flagBarV: {
    position: 'absolute',
    width: 6,
    height: 18,
    borderRadius: 1,
    backgroundColor: colors.surface,
  },
  flagBarH: {
    position: 'absolute',
    width: 18,
    height: 6,
    borderRadius: 1,
    backgroundColor: colors.surface,
  },
  listContent: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.xl,
  },
  summaryText: {
    flex: 1,
  },
  summaryName: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
  },
  summaryMeta: {
    marginTop: 2,
    fontSize: font.label,
    color: colors.textMuted,
  },
  summaryStat: {
    alignItems: 'center',
    paddingLeft: spacing.md,
  },
  summaryStatValue: {
    fontSize: font.display,
    fontWeight: '700',
    color: colors.primary,
  },
  summaryStatLabel: {
    fontSize: font.caption,
    color: colors.textMuted,
  },
  sectionTitle: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
  },
  sectionSub: {
    marginTop: 2,
    marginBottom: spacing.md,
    fontSize: font.label,
    color: colors.textMuted,
  },
  empty: {
    alignItems: 'center',
    paddingVertical: spacing.xxl,
  },
  emptyTitle: {
    fontSize: font.body,
    fontWeight: '600',
    color: colors.text,
  },
  emptySub: {
    marginTop: spacing.xs,
    fontSize: font.label,
    color: colors.textMuted,
  },
});
