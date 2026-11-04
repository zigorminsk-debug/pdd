"""Structured source data for the offline Russian PDD textbook."""

CHAPTERS = []
SIGNS = []
ACCENTS = ("#237A64", "#396FA8", "#9A681C", "#79579B")


def add(identifier, number, title, short_title, summary, sections, updated=False):
    normalized = []
    for heading, rule, comment, bullets in sections:
        normalized.append({
            "heading": heading,
            "body": rule.strip() + "\n\nКомментарий. " + comment.strip(),
            "bullets": bullets,
        })
    CHAPTERS.append({
        "id": identifier,
        "number": number,
        "title": title,
        "shortTitle": short_title,
        "summary": summary,
        "minutes": max(8, sum(len(s[1]) + len(s[2]) for s in sections) // 900 + len(sections)),
        "accent": ACCENTS[len(CHAPTERS) % len(ACCENTS)],
        "updated": updated,
        "sections": normalized,
    })


GROUP_PRACTICE = {
    "1. Предупреждающие знаки": "Заранее снизьте скорость и учтите опасность. Знак предупреждает, но сам по себе не устанавливает запрет движения.",
    "2. Знаки приоритета": "Определите очерёдность до въезда. На работающем регулируемом перекрёстке сигналы имеют приоритет перед знаками приоритета.",
    "3. Запрещающие знаки": "Проверьте таблички, вид транспорта и зону действия. Ограничение действует до его отмены или иной установленной Правилами границы.",
    "4. Предписывающие знаки": "Выполните указанное направление или режим, сохраняя безопасность и соблюдая требования к пешеходам и другим участникам.",
    "5. Информационно-указательные знаки": "Используйте информацию для выбора маршрута и режима. Знак не отменяет запреты, сигналы и требования безопасности.",
    "6. Знаки сервиса": "Знак сообщает об объекте обслуживания. Заранее выберите полосу и не совершайте резкий манёвр при пропущенном съезде.",
    "7. Знаки дополнительной информации": "Табличка уточняет основной знак и не применяется самостоятельно. Прочитайте её вместе со знаком, над или под которым она установлена.",
}


def sign(code, name, group, meaning, practice="", symbol=""):
    SIGNS.append({
        "code": code,
        "name": name,
        "group": group,
        "meaning": meaning.strip(),
        "practice": practice.strip() or GROUP_PRACTICE[group],
        "symbol": symbol,
    })
