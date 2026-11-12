#!/usr/bin/env python3
"""Flasher_Builder deposunun cihazda build için gereken kısmını app/src/main/assets/repo.zip olarak paketler.

Kullanım:
    python3 tools/bundle_repo.py                                   # koaaN/Flasher_Builder main
    python3 tools/bundle_repo.py --repo https://github.com/Senan521/Flasher_Builder.git --ref main
    python3 tools/bundle_repo.py --repo /yerel/klasör              # yerel kopya

x86 Linux/Windows araçları (lpmake/, otaripper/) ve masaüstü arayüzü dahil edilmez; cihazda arm64 sürümleri kullanılır.
"""
import argparse
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

INCLUDE_DIRS = ['flasher_builder', 'scripts', 'build_scripts', 'build_tools_assets', 'flasher_scripts',
                'Platform-Tools', 'bloat', 'otaripper', 'lpmake']
INCLUDE_FILES = ['.github/workflows/build_tools.yml', 'THIRD-PARTY-NOTICES.md', 'LICENSE', 'LICENSE.md']
EXCLUDE_NAMES = {'gui.py', 'updates.py', '__pycache__', '.git', '.DS_Store'}
# normalize_assets() otaripper/ ve lpmake/ klasörlerini kopyalar; klasörler kalır, x86 binary'leri çıkarılır.
HOST_BINARIES = {'otaripper', 'otaripper.exe', 'lpmake', 'lpmake.exe'}


def git(*args, cwd=None):
    return subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', default='https://github.com/koaaN/Flasher_Builder.git')
    parser.add_argument('--ref', default='main')
    parser.add_argument('--out', default=str(Path(__file__).resolve().parent.parent / 'app/src/main/assets/repo.zip'))
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        if Path(args.repo).is_dir():
            root = Path(args.repo).resolve()
        else:
            root = Path(tmp) / 'src'
            subprocess.run(['git', 'clone', '--depth', '1', '--branch', args.ref, args.repo, str(root)], check=True)
        try:
            version = git('rev-parse', 'HEAD', cwd=root)
        except (subprocess.CalledProcessError, FileNotFoundError):
            version = 'local'

        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        count = 0
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            def add(path):
                nonlocal count
                z.write(path, path.relative_to(root).as_posix())
                count += 1
            for name in INCLUDE_DIRS:
                base = root / name
                if not base.is_dir():
                    sys.exit(f'Eksik klasör: {name} (depo yapısı değişmiş olabilir)')
                for path in sorted(base.rglob('*')):
                    if not path.is_file() or path.is_symlink() or (set(path.parts) & EXCLUDE_NAMES):
                        continue
                    if name in ('otaripper', 'lpmake') and path.name in HOST_BINARIES:
                        continue
                    add(path)
            for name in INCLUDE_FILES:
                if (root / name).is_file():
                    add(root / name)
            if not (root / '.github/workflows/build_tools.yml').is_file():
                sys.exit('build_tools.yml bulunamadı: Catalog bu dosyayı okur.')
            z.writestr('REPO_VERSION', version + '\n')
    print(f'{out}: {count} dosya, {out.stat().st_size / 1e6:.1f} MB, sürüm {version[:12]}')


if __name__ == '__main__':
    main()
