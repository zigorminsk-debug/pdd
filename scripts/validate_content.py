#!/usr/bin/env python3
"""Fail CI if the bundled learning material is incomplete or internally inconsistent."""

import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "app/src/main/assets/content.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def main() -> None:
    with CONTENT.open(encoding="utf-8") as source:
        data = json.load(source)

    require(set(data) == {"meta", "topics", "questions"}, "Unexpected root content fields")
    meta = data["meta"]
    require("1 сентября 2025" in meta["edition"], "Wrong legal edition")
    for field in ("officialRulesUrl", "amendmentUrl"):
        parsed = urlparse(meta[field])
        require(parsed.scheme == "https" and parsed.netloc == "pravo.by", f"Invalid source URL: {field}")
    require("не официальные" in meta["notice"], "Training-question disclaimer is missing")

    topics = data["topics"]
    questions = data["questions"]
    require(len(topics) >= 12, "At least 12 learning topics are required")
    require(len(questions) >= 60, "At least 60 practice questions are required")

    topic_ids = [topic["id"] for topic in topics]
    require(len(topic_ids) == len(set(topic_ids)), "Duplicate topic id")
    require("changes" in topic_ids and "micromobility" in topic_ids, "September 2025 topics are missing")

    for topic in topics:
        require(topic["title"] and topic["summary"], f"Empty topic text: {topic['id']}")
        require(topic["minutes"] > 0, f"Invalid reading time: {topic['id']}")
        require(topic["accent"].startswith("#") and len(topic["accent"]) == 7, f"Invalid color: {topic['id']}")
        require(len(topic["sections"]) >= 3, f"Too few sections: {topic['id']}")
        for section in topic["sections"]:
            require(len(section["body"]) >= 100, f"Section body is too short: {topic['id']}")
            require(len(section["bullets"]) >= 3, f"Too few key points: {topic['id']}")

    question_ids = [question["id"] for question in questions]
    require(len(question_ids) == len(set(question_ids)), "Duplicate question id")
    per_topic = {topic_id: 0 for topic_id in topic_ids}
    for question in questions:
        require(question["topicId"] in per_topic, f"Unknown topic: {question['id']}")
        per_topic[question["topicId"]] += 1
        require(len(question["answers"]) == 4, f"Question must have four answers: {question['id']}")
        require(len(set(question["answers"])) == 4, f"Duplicate answer: {question['id']}")
        require(0 <= question["correct"] < 4, f"Correct answer is out of range: {question['id']}")
        require(len(question["question"]) >= 20, f"Question is too short: {question['id']}")
        require(len(question["explanation"]) >= 30, f"Explanation is too short: {question['id']}")
        require(question["reference"], f"Reference is missing: {question['id']}")

    require(all(count >= 5 for count in per_topic.values()), f"A topic has too few questions: {per_topic}")
    updated_questions = sum(q["topicId"] in {"changes", "micromobility", "equipment"} for q in questions)
    require(updated_questions >= 17, "Not enough questions about September 2025 changes")

    print(f"OK: {len(topics)} topics, {len(questions)} questions, {updated_questions} questions about updates")
    print("OK: unique IDs, four answers per question, explanations, references and official source URLs")


if __name__ == "__main__":
    main()
