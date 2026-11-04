#!/usr/bin/env python3
"""Build the bundled offline textbook from maintainable Russian source modules."""

import json
from pathlib import Path

import textbook_data as data
import textbook_chapters_01_07  # noqa: F401
import textbook_chapters_08_13  # noqa: F401
import textbook_chapters_14_20  # noqa: F401
import textbook_chapters_21_27  # noqa: F401
import textbook_appendices  # noqa: F401
import textbook_signs_1_3  # noqa: F401
import textbook_signs_4_7  # noqa: F401

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/textbook.json"


def main() -> None:
    ids = [topic["id"] for topic in data.CHAPTERS]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate chapter IDs")
    codes = [sign["code"] for sign in data.SIGNS]
    if len(codes) != len(set(codes)):
        raise ValueError("Duplicate sign codes")
    OUTPUT.write_text(
        json.dumps({"topics": data.CHAPTERS, "signs": data.SIGNS}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    sections = sum(len(topic["sections"]) for topic in data.CHAPTERS)
    characters = sum(len(section["body"]) + sum(map(len, section["bullets"]))
                     for topic in data.CHAPTERS for section in topic["sections"])
    print(f"Built {len(data.CHAPTERS)} topics, {sections} lessons, {len(data.SIGNS)} sign cards, {characters} textbook characters")


if __name__ == "__main__":
    main()
