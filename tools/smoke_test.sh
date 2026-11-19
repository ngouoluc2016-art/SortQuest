#!/usr/bin/env bash
# Test de fumée sur émulateur : installe l'APK release (minifié R8), joue un parcours utilisateur
# réel et échoue si l'application plante. Produit des captures d'écran et le journal système.
set -u
PKG=com.sortquest.app
APK=${1:-app/build/outputs/apk/release/app-release.apk}
OUT=smoke-out
mkdir -p "$OUT"

read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr 'x' ' ')
DENSITY=$(adb shell wm density | grep -oE '[0-9]+' | tail -1)
dp() { echo $(( $1 * DENSITY / 160 )); }
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "capture $1"; }
step() { echo; echo "=== $*"; }
echo "Écran ${W}x${H}, densité ${DENSITY}"

step "Installation de l'APK release"
adb install -r "$APK" || exit 1

step "Dépôt de fichiers de test dans Download"
for f in tools/fixtures/*; do adb push "$f" "/sdcard/Download/" > /dev/null; done
adb shell ls -la /sdcard/Download/

adb logcat -c

step "1er lancement : onboarding"
adb shell am start -W -n "$PKG/.MainActivity"
shot 01-onboarding-swipe 5
adb shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 300   # page suivante
shot 02-onboarding-confidentialite
adb shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 300
shot 03-onboarding-permission

step "Accès à tous les fichiers accordé (équivalent du réglage système)"
adb shell appops set --uid "$PKG" MANAGE_EXTERNAL_STORAGE allow
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am start -W -n "$PKG/.MainActivity"   # retour : ON_RESUME détecte l'accès et ouvre l'écran de tri
shot 04-tri-scan-en-cours 4
shot 05-tri-cartes 20                              # laisse le scan + la recherche de doublons se terminer

step "Swipe à droite (classer) puis à gauche (corbeille)"
CY=$((H * 55 / 100))
adb shell input swipe $((W / 2)) "$CY" $((W * 95 / 100)) "$CY" 220
shot 06-apres-classement 3
adb shell input swipe $((W / 2)) "$CY" $((W * 5 / 100)) "$CY" 220
shot 07-apres-corbeille 3

step "Navigation dans les onglets (barre inférieure)"
NAV_Y=$((H - $(dp 56)))
tab() { adb shell input tap $(( W * (2 * $1 + 1) / 8 )) "$NAV_Y"; }
tab 1; shot 08-onglet-nettoyer 6
tab 2; shot 09-onglet-autopilot 3
tab 3; shot 10-onglet-corbeille 3
tab 0; shot 11-retour-tri 3

step "Rotation (recréation de l'activité : fuite ou perte d'état ?)"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 12-paysage 4
adb shell settings put system user_rotation 0
shot 13-portrait 3

step "Vérification de l'état des fichiers sur le disque"
adb shell "ls -la /sdcard/Download/ /sdcard/Documents/SortQuest/ 2>&1; find /sdcard/Documents/SortQuest /sdcard/.SortQuest_Trash -type f 2>/dev/null" | tee "$OUT/fichiers.txt"

step "Analyse du journal"
adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -s AndroidRuntime:E > "$OUT/crash.txt"
PID_ALIVE=$(adb shell pidof "$PKG" || true)
echo "Processus vivant : ${PID_ALIVE:-NON}" | tee "$OUT/resultat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/crash.txt" && grep -q "$PKG" "$OUT/crash.txt"; then
  echo "CRASH détecté :" | tee -a "$OUT/resultat.txt"
  grep -A 30 "FATAL EXCEPTION" "$OUT/crash.txt" | tee -a "$OUT/resultat.txt"
  exit 2
fi
if [ -z "$PID_ALIVE" ]; then echo "L'application n'est plus en cours d'exécution" | tee -a "$OUT/resultat.txt"; exit 3; fi
echo "AUCUN CRASH - parcours complet réussi" | tee -a "$OUT/resultat.txt"
