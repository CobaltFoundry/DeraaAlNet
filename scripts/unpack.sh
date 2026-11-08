#!/usr/bin/env bash
# بيفك زيب التحديث وبيستبدل ملفات المشروع بمحتواه.
#
# الاستخدام:   scripts/unpack.sh <ملف.zip> [المجلد-الهدف]
#
# - لو الزيب جواه فولدر واحد بس (زي زيب GitHub: DeraaAlNet-main/) بيدخل جواه تلقائيًا.
# - المشروع بيتطابق مع الزيب: الملفات الجديدة بتتضاف، المتغيّرة بتتبدّل،
#   والملفات اللي اتشالت من الزيب بتتمسح من المشروع.
# - بيحمي: .git و .github و ملفات الزيب نفسها و scripts/unpack.sh و local.properties و ملفات التوقيع.
set -euo pipefail

ZIP="${1:?اكتب مسار ملف الزيب}"
TARGET="${2:-.}"

[ -f "$ZIP" ] || { echo "❌ الملف مش موجود: $ZIP"; exit 1; }
command -v unzip >/dev/null || { echo "❌ unzip مش متسطّب"; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q -o "$ZIP" -d "$TMP"
rm -rf "$TMP/__MACOSX"

# لو فيه فولدر واحد بس في الجذر، نشتغل من جواه
shopt -s dotglob nullglob
entries=("$TMP"/*)
if [ "${#entries[@]}" -eq 1 ] && [ -d "${entries[0]}" ]; then
  SRC="${entries[0]}"
else
  SRC="$TMP"
fi
shopt -u dotglob nullglob

# حماية: لازم يبقى شكله مشروع أندرويد، وإلا ممكن نمسح الريبو بالغلط
if [ ! -f "$SRC/settings.gradle.kts" ] && [ ! -f "$SRC/settings.gradle" ]; then
  echo "❌ الزيب مش شكله مشروع أندرويد (مفيش settings.gradle). مفيش حاجة اتغيّرت."
  exit 1
fi
if [ ! -d "$SRC/app/src" ]; then
  echo "❌ مفيش app/src جوه الزيب. مفيش حاجة اتغيّرت."
  exit 1
fi

# الحاجات المحمية: متتمسحش من المشروع ومتتكتبش من الزيب
is_protected() {
  case "$1" in
    .git|.git/*|.github|.github/*|scripts/unpack.sh|local.properties) return 0 ;;
    *.zip|*.jks|*.keystore) return 0 ;;
  esac
  return 1
}

echo "📦 فك: $ZIP"
TARGET="$(cd "$TARGET" && pwd)"

# 1) امسح من المشروع أي ملف مش موجود في الزيب (الملفات اللي اتشالت)
while IFS= read -r -d '' f; do
  rel="${f#"$TARGET"/}"
  is_protected "$rel" && continue
  [ -e "$SRC/$rel" ] || { rm -f -- "$f"; echo "🗑  $rel"; }
done < <(find "$TARGET" -type f -not -path "$TARGET/.git/*" -print0)

# 2) انسخ ملفات الزيب فوق المشروع (من غير المحمي)
while IFS= read -r -d '' f; do
  rel="${f#"$SRC"/}"
  is_protected "$rel" && continue
  mkdir -p -- "$TARGET/$(dirname "$rel")"
  cp -f -- "$f" "$TARGET/$rel"
done < <(find "$SRC" -type f -print0)

# 3) نضّف الفولدرات الفاضية
find "$TARGET" -mindepth 1 -type d -empty -not -path "$TARGET/.git/*" -not -path "$TARGET/.git" -delete 2>/dev/null || true

echo "✅ تم استبدال الملفات."
