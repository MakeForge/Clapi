#!/usr/bin/env python3
"""
Helper script to fetch Material Design icon paths from Google Fonts CDN,
convert them to Android Vector XML (<vector>), and save them into the CMP drawable directory.
"""

import argparse
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_DRAWABLE_DIR = Path("ui-shared/src/commonMain/composeResources/drawable")
DEFAULT_SCAN_DIRS = [
    Path("ui-shared/src/commonMain/kotlin"),
    Path("ui-desktop/src/desktopMain/kotlin"),
    Path("editor-ui/src/commonMain/kotlin"),
]
RES_DRAWABLE_PATTERN = re.compile(r'\bRes\.drawable\.([a-zA-Z0-9_]+)\b')


def normalize_icon_name(raw_name: str) -> str:
    """Ensures icon identifier is in snake_case without the 'ic_' prefix."""
    name = raw_name.strip()
    if name.startswith("ic_"):
        name = name[3:]
    return name.lower()


def svg_to_vector_drawable(svg_bytes: bytes) -> str | None:
    """Extracts pathData from standard Material 24px SVG and wraps in Android Vector XML."""
    try:
        root = ET.fromstring(svg_bytes)
        paths = []
        for elem in root.iter():
            tag = elem.tag.split("}")[-1]
            if tag == "path":
                d = elem.attrib.get("d")
                fill = elem.attrib.get("fill", "").lower()
                # Ignore zero-fill bounding boxes (e.g. fill="none" or M0 0h24v24H0z)
                if d and fill != "none" and "M0 0h24v24H0" not in d:
                    paths.append(d)

        if not paths:
            for elem in root.iter():
                tag = elem.tag.split("}")[-1]
                if tag == "path" and "d" in elem.attrib:
                    paths.append(elem.attrib["d"])

        if not paths:
            return None

        path_elements = "\n".join(
            f'  <path\n      android:fillColor="#FF000000"\n      android:pathData="{p}"/>'
            for p in paths
        )

        return (
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="24"\n'
            '    android:viewportHeight="24">\n'
            f'{path_elements}\n'
            '</vector>\n'
        )
    except Exception:
        return None


def fetch_and_write_icon(icon_name: str, target_dir: Path) -> bool:
    """Fetches an icon from Google Fonts CDN and saves it as ic_<icon_name>.xml."""
    clean_name = normalize_icon_name(icon_name)
    file_name = f"ic_{clean_name}.xml"
    out_file = target_dir / file_name

    target_dir.mkdir(parents=True, exist_ok=True)

    urls = [
        f"https://fonts.gstatic.com/s/i/materialicons/{clean_name}/v1/24px.svg",
        f"https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/{clean_name}/default/24px.svg",
        f"https://raw.githubusercontent.com/marella/material-design-icons/main/svg/filled/{clean_name}.svg",
    ]

    req_headers = {"User-Agent": "Mozilla/5.0"}

    for url in urls:
        try:
            req = urllib.request.Request(url, headers=req_headers)
            with urllib.request.urlopen(req, timeout=5) as response:
                if response.status == 200:
                    svg_data = response.read()
                    vector_xml = svg_to_vector_drawable(svg_data)
                    if vector_xml:
                        out_file.write_text(vector_xml, encoding="utf-8")
                        print(f"Created: {out_file}")
                        return True
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
            continue

    print(f"FAILED: Could not download icon '{clean_name}'")
    return False


def scan_and_download_missing(scan_dirs: list[Path], target_dir: Path) -> bool:
    """Scans Kotlin source files for Res.drawable.* usages and downloads missing XMLs."""
    target_dir.mkdir(parents=True, exist_ok=True)
    existing_drawables = {f.stem for f in target_dir.glob("*.xml")}

    used_drawables = set()
    for directory in scan_dirs:
        if not directory.exists():
            continue
        for file in directory.rglob("*.kt"):
            content = file.read_text(encoding="utf-8")
            for match in RES_DRAWABLE_PATTERN.findall(content):
                used_drawables.add(match)

    missing = sorted([name for name in used_drawables if name not in existing_drawables])

    if not missing:
        print(f"All {len(used_drawables)} referenced Res.drawable.* icons exist in {target_dir}.")
        return True

    print(f"Found {len(missing)} missing icon(s). Fetching...")
    all_ok = True
    for name in missing:
        if not fetch_and_write_icon(name, target_dir):
            all_ok = False

    return all_ok


def main():
    parser = argparse.ArgumentParser(
        description="Fetch Material Design vector XML drawables for Compose Multiplatform."
    )
    parser.add_argument(
        "icons",
        nargs="*",
        help="One or more icon names (e.g. visibility, ic_menu, format_align_left)",
    )
    parser.add_argument(
        "--scan",
        action="store_true",
        help="Scan project Kotlin files for missing Res.drawable.* usages and download them",
    )
    parser.add_argument(
        "--drawable-dir",
        type=Path,
        default=DEFAULT_DRAWABLE_DIR,
        help="Target drawable directory path",
    )

    args = parser.parse_args()

    if args.scan:
        success = scan_and_download_missing(DEFAULT_SCAN_DIRS, args.drawable_dir)
        sys.exit(0 if success else 1)

    if not args.icons:
        parser.print_help()
        sys.exit(1)

    all_ok = True
    for icon in args.icons:
        if not fetch_and_write_icon(icon, args.drawable_dir):
            all_ok = False

    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()