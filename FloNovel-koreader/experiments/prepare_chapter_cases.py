"""Synthetic cases for native regex characterization, not user books or patterns."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / ".work/results/focused"
cases = [
    {"name": "hash_long", "pattern": "^##.*$", "preset": True,
     "text": "## " + "긴 제목 " * 30 + "\nBody"},
    {"name": "custom_length_guard", "pattern": r"^제\s*\d+\s*화.*$",
     "text": "제 1 화 짧은 제목\nBody\n제 2 화 " + "긴 제목 " * 30 + "\nTail"},
    {"name": "trim_ascii", "pattern": r"^Chapter\s+\d+$", "text": "  Chapter 1  \nBody\nChapter 2\nTail"},
    {"name": "trim_nbsp", "pattern": r"^Chapter\s+\d+$", "text": "\u00a0Chapter 1\u00a0\nBody"},
    {"name": "digit_class", "pattern": r"^Chapter \d+$", "text": "Chapter １\nBody\nChapter 1\nTail"},
    {"name": "hangul", "pattern": r"^제[ ]*[0-9]+[ ]*장$", "text": "제 1 장\nBody\n제2장\nTail"},
    {"name": "full_line", "pattern": r"Chapter [0-9]+", "text": "prefix Chapter 1 suffix\nBody\nChapter 2\nTail"},
    {"name": "invalid", "pattern": "[", "text": "Chapter 1\nBody"},
    {"name": "inline_flag", "pattern": "(?i)^chapter [0-9]+$", "text": "Chapter 1\nBody"},
    {"name": "java_script_property", "pattern": r"^\p{IsHangul}+$", "text": "서장\nBody"},
    {"name": "alternation", "pattern": "^(서장|종장)$", "text": "서장\nBody\n종장\nTail"},
    {"name": "hit_limit", "pattern": r"^Chapter [0-9]+$", "max_hits": 3,
     "text": "".join(f"Chapter {i}\nBody\n" for i in range(10))},
]
root.mkdir(parents=True, exist_ok=True)
for case in cases:
    (root / ("chapter_" + case["name"] + ".txt")).write_text(case["text"], encoding="utf-8")
(root / "chapter-cases.json").write_text(json.dumps({"cases": cases}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("PREPARED_CHAPTER_CASES", len(cases))
