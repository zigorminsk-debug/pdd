package by.pdd.study.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Loads the versioned, offline JSON content shipped inside the APK. */
public final class ContentRepository {
    private final Content.Meta meta = new Content.Meta();
    private final List<Content.Topic> topics = new ArrayList<>();
    private final List<Content.Question> questions = new ArrayList<>();
    private final Map<String, Content.Topic> topicsById = new LinkedHashMap<>();
    private final Map<String, Content.Question> questionsById = new LinkedHashMap<>();

    public ContentRepository(Context context) {
        try {
            load(readAsset(context));
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Cannot load bundled PDD content", exception);
        }
    }

    private static String readAsset(Context context) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStream stream = context.getAssets().open("content.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    private void load(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject sourceMeta = root.getJSONObject("meta");
        meta.title = sourceMeta.getString("title");
        meta.edition = sourceMeta.getString("edition");
        meta.legalBasis = sourceMeta.getString("legalBasis");
        meta.officialRulesUrl = sourceMeta.getString("officialRulesUrl");
        meta.amendmentUrl = sourceMeta.getString("amendmentUrl");
        meta.notice = sourceMeta.getString("notice");

        JSONArray sourceTopics = root.getJSONArray("topics");
        for (int i = 0; i < sourceTopics.length(); i++) {
            JSONObject item = sourceTopics.getJSONObject(i);
            Content.Topic topic = new Content.Topic();
            topic.id = item.getString("id");
            topic.number = item.getString("number");
            topic.title = item.getString("title");
            topic.shortTitle = item.getString("shortTitle");
            topic.summary = item.getString("summary");
            topic.accent = item.getString("accent");
            topic.minutes = item.getInt("minutes");
            topic.updated = item.getBoolean("updated");
            JSONArray sourceSections = item.getJSONArray("sections");
            for (int j = 0; j < sourceSections.length(); j++) {
                JSONObject sourceSection = sourceSections.getJSONObject(j);
                Content.Section section = new Content.Section();
                section.heading = sourceSection.getString("heading");
                section.body = sourceSection.getString("body");
                JSONArray bullets = sourceSection.getJSONArray("bullets");
                for (int k = 0; k < bullets.length(); k++) {
                    section.bullets.add(bullets.getString(k));
                }
                topic.sections.add(section);
            }
            topics.add(topic);
            topicsById.put(topic.id, topic);
        }

        JSONArray sourceQuestions = root.getJSONArray("questions");
        for (int i = 0; i < sourceQuestions.length(); i++) {
            JSONObject item = sourceQuestions.getJSONObject(i);
            Content.Question question = new Content.Question();
            question.id = item.getString("id");
            question.topicId = item.getString("topicId");
            question.question = item.getString("question");
            JSONArray answers = item.getJSONArray("answers");
            for (int j = 0; j < answers.length(); j++) {
                question.answers.add(answers.getString(j));
            }
            question.correct = item.getInt("correct");
            question.explanation = item.getString("explanation");
            question.reference = item.getString("reference");
            questions.add(question);
            questionsById.put(question.id, question);
        }
    }

    public Content.Meta getMeta() {
        return meta;
    }

    public List<Content.Topic> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    public List<Content.Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public Content.Topic topic(String id) {
        return topicsById.get(id);
    }

    public Content.Question question(String id) {
        return questionsById.get(id);
    }

    public List<Content.Question> questionsForTopic(String topicId) {
        List<Content.Question> result = new ArrayList<>();
        for (Content.Question question : questions) {
            if (question.topicId.equals(topicId)) {
                result.add(question);
            }
        }
        return result;
    }

    public List<Content.Question> questionsByIds(Set<String> ids) {
        List<Content.Question> result = new ArrayList<>();
        for (Content.Question question : questions) {
            if (ids.contains(question.id)) {
                result.add(question);
            }
        }
        return result;
    }

    /** Builds a varied 20-question practice exam with broad topic coverage. */
    public List<Content.Question> createExam(long seed) {
        return createExam(seed, 20);
    }

    /** Builds a reproducible generated ticket. These are not official GAI tickets. */
    public List<Content.Question> createExam(long seed, int count) {
        int target = Math.min(count, questions.size());
        List<Content.Question> pool = new ArrayList<>(questions);
        Collections.shuffle(pool, new Random(seed));
        List<Content.Question> result = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        int coverageTarget = Math.min(target, topics.size());
        for (Content.Question question : pool) {
            if (covered.add(question.topicId)) {
                result.add(question);
            }
            if (result.size() == coverageTarget) {
                break;
            }
        }
        for (Content.Question question : pool) {
            if (result.size() >= target) {
                break;
            }
            if (!result.contains(question)) {
                result.add(question);
            }
        }
        Collections.shuffle(result, new Random(seed + 1));
        return result;
    }

    public List<Content.Question> createTicket(int number) {
        if (number < 1 || number > 40) throw new IllegalArgumentException("Ticket must be 1..40");
        return createExam(20250901L + number * 7919L, 10);
    }

    public List<Content.Topic> searchTopics(String query) {
        String needle = normalize(query);
        List<Content.Topic> result = new ArrayList<>();
        if (needle.isEmpty()) {
            return result;
        }
        for (Content.Topic topic : topics) {
            StringBuilder haystack = new StringBuilder()
                    .append(topic.title).append(' ')
                    .append(topic.summary).append(' ');
            for (Content.Section section : topic.sections) {
                haystack.append(section.heading).append(' ').append(section.body).append(' ');
                for (String bullet : section.bullets) {
                    haystack.append(bullet).append(' ');
                }
            }
            if (normalize(haystack.toString()).contains(needle)) {
                result.add(topic);
            }
        }
        return result;
    }

    public List<Content.Question> searchQuestions(String query) {
        String needle = normalize(query);
        List<Content.Question> result = new ArrayList<>();
        if (needle.length() < 2) {
            return result;
        }
        for (Content.Question question : questions) {
            if (normalize(question.question + " " + question.explanation).contains(needle)) {
                result.add(question);
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.forLanguageTag("ru")).replace('ё', 'е').trim();
    }
}
