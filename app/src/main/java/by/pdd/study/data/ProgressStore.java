package by.pdd.study.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Local-only learning progress. No account, analytics or network permission is used. */
public final class ProgressStore {
    private static final String PREFS = "learning_progress_v1";
    private static final String READ = "read_topics";
    private static final String FAVORITES = "favorites";
    private static final String CORRECT = "correct_questions";
    private static final String WRONG = "wrong_questions";
    private static final String ANSWERS = "answers";
    private static final String CORRECT_ANSWERS = "correct_answers";
    private static final String EXAMS = "exams";
    private static final String BEST_EXAM = "best_exam";

    private final SharedPreferences preferences;

    public ProgressStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private Set<String> set(String key) {
        return new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
    }

    public boolean isRead(String topicId) {
        return set(READ).contains(topicId);
    }

    public int readCount() {
        return set(READ).size();
    }

    public void setRead(String topicId, boolean read) {
        Set<String> values = set(READ);
        if (read) values.add(topicId); else values.remove(topicId);
        preferences.edit().putStringSet(READ, values).apply();
    }

    public boolean isFavorite(String topicId) {
        return set(FAVORITES).contains(topicId);
    }

    public boolean toggleFavorite(String topicId) {
        Set<String> values = set(FAVORITES);
        boolean favorite;
        if (values.contains(topicId)) {
            values.remove(topicId);
            favorite = false;
        } else {
            values.add(topicId);
            favorite = true;
        }
        preferences.edit().putStringSet(FAVORITES, values).apply();
        return favorite;
    }

    public Set<String> favorites() {
        return Collections.unmodifiableSet(set(FAVORITES));
    }

    public Set<String> wrongIds() {
        return Collections.unmodifiableSet(set(WRONG));
    }

    public int masteredCount() {
        return set(CORRECT).size();
    }

    public int totalAnswers() {
        return preferences.getInt(ANSWERS, 0);
    }

    public int correctAnswers() {
        return preferences.getInt(CORRECT_ANSWERS, 0);
    }

    public int examCount() {
        return preferences.getInt(EXAMS, 0);
    }

    public int bestExam() {
        return preferences.getInt(BEST_EXAM, -1);
    }

    public void recordAnswer(Content.Question question, boolean correct) {
        Set<String> mastered = set(CORRECT);
        Set<String> wrong = set(WRONG);
        if (correct) {
            mastered.add(question.id);
            wrong.remove(question.id);
        } else {
            wrong.add(question.id);
        }
        preferences.edit()
                .putStringSet(CORRECT, mastered)
                .putStringSet(WRONG, wrong)
                .putInt(ANSWERS, totalAnswers() + 1)
                .putInt(CORRECT_ANSWERS, correctAnswers() + (correct ? 1 : 0))
                .apply();
    }

    public void recordExam(int score, int total) {
        int percent = total == 0 ? 0 : Math.round(score * 100f / total);
        preferences.edit()
                .putInt(EXAMS, examCount() + 1)
                .putInt(BEST_EXAM, Math.max(bestExam(), percent))
                .apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }
}
