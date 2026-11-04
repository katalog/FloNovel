"""The user's preprocessed ## prefix requirement, including source-offset boundaries."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / ".work/results/focused"
cases = {
    "requested": "## 제1장\n본문\n## 제2장\n다음 본문",
    "crlf": "## 제1장\r\n본문\r\n## 제2장\r\n마지막\r\n",
    "prefix_only": "본문 ## 제0장\n### 제1장\n##\n# 제2장\n##제3장",
    "long_title": "## 제1장 " + "길이가 긴 제목 " * 30 + "\n본문",
    "emoji_before": "가😀나\n## 제1장\n본문😀\n## 제2장\n끝",
    "unicode_trim": "\t\u00a0\u3000## 제1장\u00a0\u3000\n본문\n\u2003## 제2장\u202f\n끝",
    "zero_width_not_trimmed": "\u200b## 제0장\n\ufeff## 제0장\n## 제1장",
    "no_chapters": "챕터 표식 없는 본문\n다음 줄",
    "empty": "",
    "twenty_lines": "## 제1장\n" + "본문\n" * 19 + "## 제2장\n끝",
    "twenty_one_lines": "## 제1장\n" + "본문\n" * 20 + "## 제2장\n끝",
    "adjacent_headings": "## 제1장\n## 제2장\n## 제3장\n끝",
}
root.mkdir(parents=True, exist_ok=True)
(root / "marker-cases.json").write_text(json.dumps({"cases": [
    {"name": name, "text": text} for name, text in cases.items()
]}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("PREPARED_MARKER_CASES", len(cases))
