"""Trouve un élément d'interface par son texte dans un dump uiautomator et affiche "x y" (son centre).

Usage : python3 find_node.py ui.xml "Texte" [exact|prefix]
Cherche dans les attributs text et content-desc. Code de sortie 1 si introuvable.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "exact"

def match(value: str) -> bool:
    if not value:
        return False
    return value == target if mode == "exact" else value.startswith(target)

try:
    root = ET.parse(path).getroot()
except ET.ParseError:
    sys.exit(1)

for node in root.iter("node"):
    if match(node.get("text", "")) or match(node.get("content-desc", "")):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
