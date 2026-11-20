#!/usr/bin/env bash
# Test de l'APK release sur émulateur : mise à jour depuis la 1.0.0 (si fournie), puis parcours
# complet des fonctions d'organisation et de consultation. Échoue si l'application plante.
#   $1 = APK à tester    $2 = APK de l'ancienne version (facultatif, pour le test de mise à jour)
set -u
PKG=com.sortquest.app
APK=${1:-app/build/outputs/apk/release/app-release.apk}
OLD_APK=${2:-}
OUT=smoke-out
mkdir -p "$OUT"
MISSING=0

read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr 'x' ' ')
DENSITY=$(adb shell wm density | grep -oE '[0-9]+' | tail -1)
dp() { echo $(( $1 * DENSITY / 160 )); }
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "capture $1"; }
step() { echo; echo "=== $*"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
NAV_Y=$((H - $(dp 56)))
tab() { adb shell input tap $(( W * (2 * $1 + 1) / 8 )) "$NAV_Y"; }   # 0 Organiser, 1 Trier, 2 Optimiser, 3 Auto-Pilot

# Touche un élément par son texte (arbre d'accessibilité), 3 essais
tap_text() {
  local mode=${2:-exact}
  for _ in 1 2 3; do
    if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1; then
      if coords=$(python3 tools/find_node.py /tmp/ui.xml "$1" "$mode"); then
        adb shell input tap $coords; echo "toucher « $1 » -> $coords"; return 0
      fi
    fi
    sleep 2
  done
  echo "ÉLÉMENT INTROUVABLE : « $1 »"; MISSING=$((MISSING + 1)); return 1
}
grant() { adb shell appops set --uid "$PKG" MANAGE_EXTERNAL_STORAGE allow; }
launch() { adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; }

echo "Écran ${W}x${H}, densité ${DENSITY}"
for f in tools/fixtures/*; do adb push "$f" "/sdcard/Download/" > /dev/null; done
adb logcat -c

if [ -n "$OLD_APK" ] && [ -f "$OLD_APK" ]; then
  step "Version 1.0.0 : installation et utilisation (crée une base au schéma v3)"
  adb install "$OLD_APK" || exit 1
  grant; launch
  shot 00-v100-tri 20
  CY=$((H * 60 / 100))
  adb shell input swipe $((W / 2)) "$CY" $((W * 95 / 100)) "$CY" 220   # un fichier classé => historique
  sleep 3
  adb shell input tap $((W / 2)) $((H * 8 / 100)); sleep 1               # ferme une éventuelle félicitation
  shot 01-v100-apres-tri 2
  step "MISE À JOUR vers la nouvelle version (migration de base v3 -> v4)"
  adb install -r "$APK" || { echo "ÉCHEC DE LA MISE À JOUR (signature ?)" | tee "$OUT/resultat.txt"; exit 4; }
  launch
  shot 02-v110-premier-lancement 15
else
  step "Installation neuve + accès accordé"
  adb install -r "$APK" || exit 1
  grant; launch
  shot 02-v110-premier-lancement 15
fi

step "Organiser : relance d'une analyse pour indexer tous les fichiers de test"
tap_text "Analyser le stockage" || true
shot 03-organiser 20

step "Consulter un fichier AVANT déplacement (PDF multipage)"
tap_text "Facture_EDF_octobre_2026.pdf" && { shot 04-visionneuse-pdf-avant 4; back; }

step "Choisir une réorganisation"
tap_text "Réorganisation" && { shot 05-choix-reorganisation 2; back; }

step "Gamme de dossiers thématiques"
tap_text "Dossiers" && { shot 06-dossiers-thematiques 3; back; }

step "Tout ranger (plan visuel puis confirmation)"
tap_text "Tout ranger" prefix && { shot 07-confirmation-plan 2; tap_text "Ranger"; shot 08-apres-rangement 4; }

step "Rangés : parcourir les dossiers puis consulter un fichier APRÈS déplacement"
tap_text "Rangés"; shot 09-ranges 4
if tap_text "Administratif"; then
  shot 10-dossier-administratif 3
  if tap_text "Factures"; then
    shot 11-dossier-factures 3
    tap_text "Facture_EDF_octobre_2026.pdf" && { shot 12-visionneuse-pdf-apres 4; back; }
    back
  fi
  back
fi

step "Autres onglets"
tab 1; shot 13-trier 4
tab 2; shot 14-optimiser 5
tab 3; shot 15-autopilot 3
tap_text "Historique" && shot 16-historique 3
tab 0; shot 17-retour-organiser 3

step "État des fichiers sur le disque"
adb shell "echo '--- Download'; ls /sdcard/Download/; echo '--- Rangés'; find /sdcard/Documents/SortQuest -type f; echo '--- Corbeille'; ls /sdcard/.SortQuest_Trash/ 2>/dev/null" | tee "$OUT/fichiers.txt"

step "Analyse du journal"
adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -s AndroidRuntime:E > "$OUT/crash.txt"
PID_ALIVE=$(adb shell pidof "$PKG" || true)
{
  echo "Processus vivant : ${PID_ALIVE:-NON}"
  echo "Éléments introuvables : $MISSING"
} | tee "$OUT/resultat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/crash.txt" && grep -q "$PKG" "$OUT/crash.txt"; then
  echo "CRASH détecté :" | tee -a "$OUT/resultat.txt"
  grep -A 40 "FATAL EXCEPTION" "$OUT/crash.txt" | tee -a "$OUT/resultat.txt"
  exit 2
fi
if [ -z "$PID_ALIVE" ]; then echo "L'application n'est plus en cours d'exécution" | tee -a "$OUT/resultat.txt"; exit 3; fi
echo "AUCUN CRASH - parcours complet réussi" | tee -a "$OUT/resultat.txt"
