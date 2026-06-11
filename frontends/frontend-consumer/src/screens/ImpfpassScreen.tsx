import { useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View, ActivityIndicator } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { DiseaseGroup, PatientDossier } from '../types';
import { colors, font, radius, spacing } from '../theme';
import { groupByDisease } from '../utils';
import { Avatar } from '../components/Avatar';
import { VaccinationGroup } from '../components/VaccinationGroup';

export function ImpfpassScreen() {
  const insets = useSafeAreaInsets();
  const [dossier, setDossier] = useState<PatientDossier | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const logout = () => {
    try { sessionStorage.removeItem('auth'); } catch {}
    window.location.replace('/');
  };

  useEffect(() => {
    let patientId = '0000000-0000-0000-0000-000000000001'; // Default Fallback
    try {
      const authStr = sessionStorage.getItem('auth');
      if (authStr) {
        const auth = JSON.parse(authStr);
        if (auth.userId) {
          patientId = auth.userId;
        }
      }
    } catch (e) {
      console.warn('sessionStorage is not available or auth parse failed', e);
    }

    fetch(`/api/bff-consumer/patients/${patientId}`)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`Server lieferte Status ${res.status}`);
        }
        return res.json();
      })
      .then((data: any) => {
        // Map API response to our client-side type
        const vaccinationsMapped = (data.vaccinations || []).map((v: any, i: number) => ({
          id: v.id || `v-${i}`,
          disease: v.targetDisease || '',
          vaccine: v.vaccineName || '',
          vaccineCode: v.vaccineCode || '',
          date: v.vaccinationDate || '',
          dose: v.doseSequence || '',
          doseNumber: v.doseNumber || undefined,
          seriesDoses: v.seriesDoses || undefined,
          vaccinationReason: v.vaccinationReason || undefined,
          manufacturer: v.manufacturer || '',
          batch: v.lotNumber || '',
          route: v.administrationRoute || '',
          site: v.siteOfAdministration || '',
          season: v.season || undefined,
        }));
        setDossier({
          firstName: data.firstName,
          lastName: data.lastName,
          age: data.age,
          gender: data.gender,
          vaccinations: vaccinationsMapped,
        });
        setLoading(false);
      })
      .catch((err) => {
        console.error('Error loading patient dossier:', err);
        setError(err.message || 'Verbindungsfehler beim Laden des Impfdossiers.');
        setLoading(false);
      });
  }, []);

  const groups = useMemo(() => {
    if (!dossier) return [];
    return groupByDisease(dossier.vaccinations);
  }, [dossier]);

  if (loading) {
    return (
      <View style={[styles.root, styles.center]}>
        <StatusBar style="dark" />
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={[styles.messageText, { marginTop: spacing.md }]}>Lade Impfdossier...</Text>
      </View>
    );
  }

  if (error || !dossier) {
    return (
      <View style={[styles.root, styles.center]}>
        <StatusBar style="dark" />
        <Text style={[styles.messageText, styles.errorText]}>
          {error || 'Ein unerwarteter Fehler ist aufgetreten.'}
        </Text>
      </View>
    );
  }

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
          <View style={{ flex: 1 }} />
          <Pressable style={styles.logoutBtn} onPress={logout} accessibilityLabel="Abmelden" accessibilityRole="button">
            <Text style={styles.logoutText}>Abmelden</Text>
          </Pressable>
        </View>
      </View>

      <FlatList<DiseaseGroup>
        data={groups}
        keyExtractor={(group) => group.disease}
        renderItem={({ item }) => <VaccinationGroup group={item} />}
        ListHeaderComponent={<ProfileSummary dossier={dossier} groupCount={groups.length} />}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>Keine Impfungen erfasst</Text>
            <Text style={styles.emptySub}>Für {dossier.firstName} liegen noch keine Einträge vor.</Text>
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
  dossier,
  groupCount,
}: {
  dossier: PatientDossier;
  groupCount: number;
}) {
  const total = dossier.vaccinations.length;
  return (
    <>
      <View
        style={styles.summary}
        accessible
        accessibilityLabel={`${dossier.firstName} ${dossier.lastName}, ${dossier.age} Jahre, ${total} Impfungen erfasst`}>
        <Avatar firstName={dossier.firstName} lastName={dossier.lastName} size={56} />
        <View style={styles.summaryText}>
          <Text style={styles.summaryName}>
            {dossier.lastName}, {dossier.firstName}
          </Text>
          <Text style={styles.summaryMeta}>
            Ich · {dossier.age} Jahre · {dossier.gender}
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
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  messageText: {
    fontSize: font.body,
    color: colors.text,
    textAlign: 'center',
  },
  errorText: {
    color: colors.swissRed || '#d32f2f',
    fontWeight: '600',
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
  logoutBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    marginRight: spacing.lg,
  },
  logoutText: {
    fontSize: font.label,
    fontWeight: '500',
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

