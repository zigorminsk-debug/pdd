package by.pdd.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import by.pdd.study.data.Content;
import by.pdd.study.data.ContentRepository;
import by.pdd.study.data.ProgressStore;
import by.pdd.study.ui.IconView;
import by.pdd.study.ui.ProgressRingView;
import by.pdd.study.ui.Ui;
import by.pdd.study.update.UpdateManager;

/**
 * Single-activity, offline learning app. Native Android widgets keep the APK small and
 * avoid network, account and third-party runtime dependencies.
 */
public final class MainActivity extends Activity {
    private static final int TAB_HOME = 0;
    private static final int TAB_RULES = 1;
    private static final int TAB_TESTS = 2;
    private static final int TAB_PROFILE = 3;

    private ContentRepository repository;
    private ProgressStore store;
    private UpdateManager updateManager;
    private File pendingInstall;
    private AlertDialog downloadDialog;
    private boolean updateCheckInProgress;
    private FrameLayout content;
    private LinearLayout bottomNavigation;
    private final List<LinearLayout> navigationItems = new ArrayList<>();
    private int activeTab = TAB_HOME;
    private String screen = "home";
    private int topicBackTarget = TAB_RULES;
    private boolean ticketControlMode;
    private QuizSession quiz;
    private TextView timerText;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (quiz == null || !quiz.exam || quiz.finished) return;
            quiz.remainingSeconds--;
            updateTimer();
            if (quiz.remainingSeconds <= 0) {
                showResult();
            } else {
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        repository = new ContentRepository(this);
        store = new ProgressStore(this);
        updateManager = new UpdateManager(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        content = Ui.frame(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        buildBottomNavigation(root);
        setContentView(root);
        showHome();
        checkForUpdates(false);
    }

    private void buildBottomNavigation(LinearLayout root) {
        bottomNavigation = Ui.row(this);
        bottomNavigation.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        bottomNavigation.setBackgroundColor(Color.WHITE);
        bottomNavigation.setElevation(Ui.dp(this, 12));
        String[] icons = {"home", "book", "test", "user"};
        String[] labels = {"Главная", "Правила", "Тесты", "Профиль"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 3));
            item.setBackground(Ui.ripple(this, Color.WHITE, 14));
            IconView icon = Ui.icon(this, icons[i], Ui.MUTED, 23);
            item.addView(icon);
            TextView text = Ui.text(this, labels[i], 10, Ui.MUTED, true);
            text.setGravity(Gravity.CENTER);
            Ui.add(item, text, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 3, 0, 0);
            Ui.clickable(item, view -> navigateTab(tab));
            navigationItems.add(item);
            bottomNavigation.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        root.addView(bottomNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 72)));
    }

    private void navigateTab(int tab) {
        switch (tab) {
            case TAB_HOME: showHome(); break;
            case TAB_RULES: showRules(); break;
            case TAB_TESTS: showTests(); break;
            case TAB_PROFILE: showProfile(); break;
            default: break;
        }
    }

    private void updateNavigation() {
        for (int i = 0; i < navigationItems.size(); i++) {
            LinearLayout item = navigationItems.get(i);
            int color = i == activeTab ? Ui.GREEN : Ui.MUTED;
            ((IconView) item.getChildAt(0)).setIconColor(color);
            ((TextView) item.getChildAt(1)).setTextColor(color);
        }
    }

    private void setScreen(String name, int tab, boolean navigationVisible, View view) {
        timerHandler.removeCallbacks(timerTick);
        screen = name;
        activeTab = tab;
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bottomNavigation.setVisibility(navigationVisible ? View.VISIBLE : View.GONE);
        updateNavigation();
    }

    private ScrollView scrollScreen(LinearLayout page, boolean padded) {
        if (padded) {
            page.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 28));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addMainHeader(LinearLayout page, String title, String subtitle, boolean search) {
        LinearLayout row = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, title, 28, Ui.INK, true));
        if (subtitle != null) {
            Ui.add(titles, Ui.text(this, subtitle, 13, Ui.MUTED, false),
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 4, 0, 0);
        }
        Ui.addWeight(row, titles, 1, 0, 0, 12, 0);
        if (search) {
            LinearLayout searchButton = iconButton("search", Ui.GREEN, Ui.MINT);
            searchButton.setContentDescription("Поиск по правилам и вопросам");
            Ui.clickable(searchButton, view -> showSearch());
            row.addView(searchButton, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        }
        page.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout iconButton(String icon, int color, int background) {
        LinearLayout button = Ui.column(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.ripple(this, background, 14));
        button.addView(Ui.icon(this, icon, color, 22));
        return button;
    }

    private void addBackHeader(LinearLayout page, String title, Runnable action) {
        LinearLayout row = Ui.row(this);
        LinearLayout back = iconButton("back", Ui.INK, Color.WHITE);
        back.setContentDescription("Назад");
        Ui.clickable(back, view -> action.run());
        row.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView titleView = Ui.text(this, title, 20, Ui.INK, true);
        Ui.singleLine(titleView);
        Ui.addWeight(row, titleView, 1, 14, 0, 0, 0);
        page.addView(row);
        Ui.add(page, Ui.space(this, 18), 1, 1, 0, 0, 0, 0);
    }

    private void showHome() {
        quiz = null;
        LinearLayout page = Ui.column(this);
        addMainHeader(page, "ПДД Беларусь", "Учись спокойно. Управляй уверенно.", true);
        Ui.add(page, Ui.space(this, 20), 1, 1, 0, 0, 0, 0);

        LinearLayout hero = Ui.card(this);
        GradientDrawable heroBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(16, 56, 47), Color.rgb(35, 103, 82)});
        heroBackground.setCornerRadius(Ui.dp(this, 24));
        hero.setBackground(heroBackground);
        TextView edition = Ui.pill(this, "РЕДАКЦИЯ  •  01.09.2025", Color.rgb(225, 245, 234), Color.rgb(48, 113, 93));
        hero.addView(edition, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.add(hero, Ui.text(this, "Дорога начинается\nсо знаний", 30, Color.WHITE, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0);
        Ui.add(hero, Ui.text(this, "Понятные конспекты, тренировка\nи разбор каждой ошибки", 14,
                        Color.rgb(205, 228, 217), false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0);
        int progress = Math.round(store.readCount() * 100f / repository.getTopics().size());
        LinearLayout progressLabels = Ui.row(this);
        Ui.addWeight(progressLabels, Ui.text(this, "Ваш прогресс", 12, Color.WHITE, false), 1,
                0, 0, 0, 0);
        progressLabels.addView(Ui.text(this, progress + "%", 12, Color.WHITE, true));
        Ui.add(hero, progressLabels, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 20, 0, 0);
        ProgressBar bar = Ui.progress(this, Color.rgb(146, 220, 177));
        bar.setProgress(progress);
        Ui.add(hero, bar, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6), 0, 7, 0, 0);
        Button continueButton = Ui.button(this, store.readCount() == 0 ? "Начать обучение" : "Продолжить обучение", true);
        continueButton.setBackground(Ui.ripple(this, Color.WHITE, 14));
        continueButton.setTextColor(Ui.GREEN);
        Ui.clickable(continueButton, view -> showRules());
        Ui.add(hero, continueButton, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0);
        page.addView(hero);

        LinearLayout quick = Ui.row(this);
        quick.addView(quickCard("Экзамен ×10", "10 вопросов · 10 минут", "test", Ui.GREEN,
                view -> startExam()), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        View changes = quickCard("Новое в 2025", "Разбор главных изменений", "alert", Ui.AMBER,
                view -> showTopic(repository.topic("changes"), TAB_HOME));
        LinearLayout.LayoutParams changesParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        changesParams.setMargins(Ui.dp(this, 12), 0, 0, 0);
        quick.addView(changes, changesParams);
        Ui.add(page, quick, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 14, 0, 0);

        addSectionHeading(page, "На сегодня", "Один вопрос — один шаг вперёд");
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        Content.Question daily = repository.getQuestions().get(day % repository.getQuestions().size());
        LinearLayout dailyCard = Ui.card(this);
        LinearLayout dailyTop = Ui.row(this);
        Ui.addWeight(dailyTop, Ui.pill(this, "ВОПРОС ДНЯ", Ui.GREEN, Ui.MINT), 1, 0, 0, 0, 0);
        dailyTop.addView(Ui.icon(this, "next", Ui.GREEN, 22));
        dailyCard.addView(dailyTop);
        Ui.add(dailyCard, Ui.text(this, daily.question, 17, Ui.INK, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 15, 0, 0);
        Ui.add(dailyCard, Ui.text(this, "Ответ и объяснение — сразу после выбора", 12, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0);
        dailyCard.setBackground(Ui.ripple(this, Color.WHITE, 20));
        Ui.clickable(dailyCard, view -> startQuiz("Вопрос дня",
                Collections.singletonList(daily), false, TAB_HOME));
        Ui.add(page, dailyCard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 0);

        addSectionHeading(page, "Учебный план", repository.getTopics().size() + " разделов · "
                + repository.getQuestions().size() + " вопросов");
        int shown = 0;
        for (Content.Topic topic : repository.getTopics()) {
            if (topic.id.equals("changes") || shown >= 3) continue;
            page.addView(topicCard(topic));
            shown++;
        }
        Button allTopics = Ui.button(this, "Все разделы", false);
        Ui.clickable(allTopics, view -> showRules());
        Ui.add(page, allTopics, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 0);
        setScreen("home", TAB_HOME, true, scrollScreen(page, true));
    }

    private View quickCard(String title, String subtitle, String icon, int color, View.OnClickListener listener) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16));
        card.setBackground(Ui.ripple(this, Color.WHITE, 20));
        LinearLayout iconBox = iconButton(icon, color, color == Ui.AMBER ? Ui.AMBER_BG : Ui.MINT);
        card.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        Ui.add(card, Ui.text(this, title, 16, Ui.INK, true), ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 13, 0, 0);
        Ui.add(card, Ui.text(this, subtitle, 11, Ui.MUTED, false), ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 0);
        Ui.clickable(card, listener);
        return card;
    }

    private void addSectionHeading(LinearLayout page, String title, String subtitle) {
        Ui.add(page, Ui.text(this, title, 21, Ui.INK, true), ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 26, 0, 0);
        if (subtitle != null) {
            Ui.add(page, Ui.text(this, subtitle, 12, Ui.MUTED, false), ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 10);
        }
    }

    private LinearLayout topicCard(Content.Topic topic) {
        LinearLayout outer = Ui.column(this);
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.ripple(this, Color.WHITE, 20));
        LinearLayout row = Ui.row(this);
        int accent = Color.parseColor(topic.accent);
        TextView number = Ui.text(this, topic.number, 13, accent, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.roundDp(this, topic.updated ? Ui.AMBER_BG : Ui.MINT, 13));
        row.addView(number, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout middle = Ui.column(this);
        LinearLayout titleRow = Ui.row(this);
        TextView title = Ui.text(this, topic.title, 16, Ui.INK, true);
        Ui.singleLine(title);
        Ui.addWeight(titleRow, title, 1, 0, 0, 0, 0);
        if (store.isRead(topic.id)) {
            titleRow.addView(Ui.icon(this, "check", Ui.GREEN_2, 20));
        }
        middle.addView(titleRow);
        Ui.add(middle, Ui.text(this, topic.summary, 12, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0);
        int total = repository.questionsForTopic(topic.id).size();
        Ui.add(middle, Ui.text(this, topic.minutes + " мин · " + total + " вопросов", 11, accent, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 7, 0, 0);
        Ui.addWeight(row, middle, 1, 13, 0, 8, 0);
        row.addView(Ui.icon(this, "next", Ui.MUTED, 20));
        card.addView(row);
        Ui.clickable(card, view -> showTopic(topic, TAB_RULES));
        outer.addView(card);
        Ui.add(outer, Ui.space(this, 10), 1, 1, 0, 0, 0, 0);
        return outer;
    }

    private void showRules() {
        quiz = null;
        LinearLayout page = Ui.column(this);
        addMainHeader(page, "Правила", "Кратко о главном, со ссылками на пункты", true);
        EditText search = searchField("Найти правило или вопрос");
        search.setFocusable(false);
        Ui.clickable(search, view -> showSearch());
        Ui.add(page, search, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52), 0, 20, 0, 0);

        LinearLayout update = Ui.card(this);
        update.setBackground(Ui.ripple(this, Ui.AMBER_BG, 20));
        LinearLayout updateRow = Ui.row(this);
        updateRow.addView(Ui.icon(this, "alert", Ui.AMBER, 28));
        LinearLayout updateText = Ui.column(this);
        updateText.addView(Ui.text(this, "Обновление · 1 сентября 2025", 14, Ui.AMBER, true));
        Ui.add(updateText, Ui.text(this, "СПМ, шины, стёкла и жилые зоны", 12, Ui.INK, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0);
        Ui.addWeight(updateRow, updateText, 1, 12, 0, 0, 0);
        updateRow.addView(Ui.icon(this, "next", Ui.AMBER, 20));
        update.addView(updateRow);
        Ui.clickable(update, view -> showTopic(repository.topic("changes"), TAB_RULES));
        page.addView(update);

        addSectionHeading(page, "Все разделы", store.readCount() + " из "
                + repository.getTopics().size() + " прочитано");
        for (Content.Topic topic : repository.getTopics()) {
            page.addView(topicCard(topic));
        }
        setScreen("rules", TAB_RULES, true, scrollScreen(page, true));
    }

    private EditText searchField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(Ui.MUTED);
        field.setTextColor(Ui.INK);
        field.setTextSize(14);
        field.setSingleLine(true);
        field.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        field.setBackground(Ui.roundDp(this, Color.WHITE, 16));
        return field;
    }

    private void showTopic(Content.Topic topic, int backTarget) {
        if (topic == null) return;
        topicBackTarget = backTarget;
        LinearLayout page = Ui.column(this);
        addBackHeader(page, topic.shortTitle, this::goBackFromTopic);

        LinearLayout heading = Ui.row(this);
        TextView number = Ui.text(this, topic.number, 16, Color.parseColor(topic.accent), true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(Ui.roundDp(this, topic.updated ? Ui.AMBER_BG : Ui.MINT, 16));
        heading.addView(number, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
        LinearLayout headingText = Ui.column(this);
        if (topic.updated) {
            headingText.addView(Ui.pill(this, "ОБНОВЛЕНО 01.09.2025", Ui.AMBER, Ui.AMBER_BG),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Ui.add(headingText, Ui.text(this, topic.title, 26, Ui.INK, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, topic.updated ? 9 : 0, 0, 0);
        Ui.addWeight(heading, headingText, 1, 14, 0, 0, 0);
        page.addView(heading);
        Ui.add(page, Ui.text(this, topic.summary + " · " + topic.minutes + " мин", 14, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0);

        LinearLayout actions = Ui.row(this);
        Button practice = Ui.button(this, "Тренировка · " + repository.questionsForTopic(topic.id).size(), true);
        Ui.clickable(practice, view -> startQuiz(topic.shortTitle,
                repository.questionsForTopic(topic.id), false, topicBackTarget));
        Ui.addWeight(actions, practice, 1, 0, 0, 10, 0);
        LinearLayout favorite = iconButton("bookmark", store.isFavorite(topic.id) ? Color.WHITE : Ui.GREEN,
                store.isFavorite(topic.id) ? Ui.GREEN : Ui.MINT);
        ((IconView) favorite.getChildAt(0)).setFilled(store.isFavorite(topic.id));
        favorite.setContentDescription("Добавить раздел в избранное");
        Ui.clickable(favorite, view -> {
            store.toggleFavorite(topic.id);
            showTopic(topic, topicBackTarget);
        });
        actions.addView(favorite, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        Ui.add(page, actions, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 20, 0, 0);

        for (int i = 0; i < topic.sections.size(); i++) {
            Content.Section section = topic.sections.get(i);
            LinearLayout card = Ui.card(this);
            TextView sectionNumber = Ui.label(this, "Часть " + (i + 1));
            card.addView(sectionNumber);
            Ui.add(card, Ui.text(this, section.heading, 20, Ui.INK, true),
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 7, 0, 0);
            Ui.add(card, Ui.text(this, section.body, 15, Ui.INK, false),
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0);
            for (String bullet : section.bullets) {
                LinearLayout bulletRow = Ui.row(this);
                bulletRow.setGravity(Gravity.TOP);
                TextView dot = Ui.text(this, "•", 22, Ui.GREEN_2, true);
                bulletRow.addView(dot);
                TextView value = Ui.text(this, bullet, 14, Ui.MUTED, false);
                Ui.addWeight(bulletRow, value, 1, 9, 0, 0, 0);
                Ui.add(card, bulletRow, ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0);
            }
            Ui.add(page, card, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 18, 0, 0);
        }

        Button markRead = Ui.button(this, store.isRead(topic.id) ? "✓  Раздел прочитан" : "Отметить как прочитанный",
                !store.isRead(topic.id));
        Ui.clickable(markRead, view -> {
            store.setRead(topic.id, !store.isRead(topic.id));
            showTopic(topic, topicBackTarget);
        });
        Ui.add(page, markRead, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 0);
        TextView note = Ui.text(this, "Конспект не заменяет полный официальный текст. Ссылка на нормативный акт — в профиле.",
                11, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        Ui.add(page, note, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                12, 16, 12, 0);
        setScreen("topic", topicBackTarget, false, scrollScreen(page, true));
    }

    private void goBackFromTopic() {
        if (topicBackTarget == TAB_HOME) showHome(); else if (topicBackTarget == TAB_PROFILE) showProfile(); else showRules();
    }

    private void showTests() {
        quiz = null;
        LinearLayout page = Ui.column(this);
        addMainHeader(page, "Тесты", "Тренировка и контроль в стиле «Нового поворота»", false);

        LinearLayout exam = Ui.card(this);
        exam.setBackground(Ui.roundDp(this, Ui.GREEN, 24));
        exam.addView(Ui.pill(this, "ЭКЗАМЕН ×10", Color.WHITE, Color.rgb(44, 105, 87)),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.add(exam, Ui.text(this, "Проверьте готовность", 25, Color.WHITE, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 16, 0, 0);
        Ui.add(exam, Ui.text(this, "10 вопросов  ·  10 минут  ·  не более 1 ошибки", 13,
                        Color.rgb(208, 231, 219), false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0);
        Ui.add(exam, Ui.text(this, "Ответы вслепую · порядок вариантов меняется", 12,
                        Color.rgb(208, 231, 219), false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 0);
        Button start = Ui.button(this, "Начать экзамен", true);
        start.setBackground(Ui.ripple(this, Color.WHITE, 14));
        start.setTextColor(Ui.GREEN);
        Ui.clickable(start, view -> startExam());
        Ui.add(exam, start, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 0);
        page.addView(exam);

        LinearLayout tickets = actionCard("Билеты · 40", "10 вопросов в каждом · тренировка или контроль",
                "test", Ui.GREEN, Ui.MINT, view -> showTickets());
        Ui.add(page, tickets, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 12, 0, 0);

        LinearLayout control = actionCard("Контроль ×20", "20 минут · не более 2 ошибок · ответы вслепую",
                "clock", Ui.AMBER, Ui.AMBER_BG, view -> startControl20());
        Ui.add(page, control, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 0);

        int mistakes = store.wrongIds().size();
        LinearLayout mistakeCard = actionCard("Работа над ошибками",
                mistakes == 0 ? "Ошибок пока нет — так держать!" : mistakes + " вопрос(а) ждут повторения",
                "refresh", Ui.RED, Ui.RED_BG, view -> {
                    if (mistakes == 0) {
                        Toast.makeText(this, "Сначала ответьте на вопросы", Toast.LENGTH_SHORT).show();
                    } else {
                        startQuiz("Работа над ошибками", repository.questionsByIds(store.wrongIds()), false, TAB_TESTS);
                    }
                });
        Ui.add(page, mistakeCard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 0);

        LinearLayout mixedCard = actionCard("Смешанная тренировка", "10 случайных вопросов с объяснениями",
                "stats", Ui.GREEN, Ui.MINT, view -> {
                    List<Content.Question> mixed = new ArrayList<>(repository.getQuestions());
                    Collections.shuffle(mixed);
                    startQuiz("Смешанная тренировка", new ArrayList<>(mixed.subList(0, 10)), false, TAB_TESTS);
                });
        Ui.add(page, mixedCard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 0);

        addSectionHeading(page, "По темам", "Ответ и пояснение показываются сразу");
        for (Content.Topic topic : repository.getTopics()) {
            int count = repository.questionsForTopic(topic.id).size();
            if (count == 0) continue;
            LinearLayout topicTest = actionCard(topic.title, count + " вопросов · " + topic.minutes + " мин на теорию",
                    "test", Color.parseColor(topic.accent), topic.updated ? Ui.AMBER_BG : Ui.MINT,
                    view -> startQuiz(topic.shortTitle, repository.questionsForTopic(topic.id), false, TAB_TESTS));
            Ui.add(page, topicTest, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 0, 0, 10);
        }
        setScreen("tests", TAB_TESTS, true, scrollScreen(page, true));
    }

    private void showTickets() {
        quiz = null;
        LinearLayout page = Ui.column(this);
        addBackHeader(page, "Билеты", this::showTests);
        Ui.add(page, Ui.text(this, "40 билетов · по 10 вопросов", 25, Ui.INK, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0);
        Ui.add(page, Ui.text(this, "Состав билета фиксирован. Вопросы формируются из авторского банка приложения и могут повторяться в разных билетах.",
                12, Ui.MUTED, false), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 7, 0, 16);

        LinearLayout modes = Ui.row(this);
        Button trainingMode = Ui.button(this, "Тренировка", !ticketControlMode);
        Ui.clickable(trainingMode, view -> {
            ticketControlMode = false;
            showTickets();
        });
        Ui.addWeight(modes, trainingMode, 1, 0, 0, 6, 0);
        Button controlMode = Ui.button(this, "Контроль ×10", ticketControlMode);
        Ui.clickable(controlMode, view -> {
            ticketControlMode = true;
            showTickets();
        });
        Ui.addWeight(modes, controlMode, 1, 6, 0, 0, 0);
        page.addView(modes);
        TextView modeHint = Ui.text(this, ticketControlMode
                        ? "10 минут · не более 1 ошибки · результат только в конце"
                        : "Можно возвращаться к вопросам · пояснение после каждого ответа",
                12, ticketControlMode ? Ui.AMBER : Ui.GREEN_2, true);
        modeHint.setGravity(Gravity.CENTER);
        Ui.add(page, modeHint, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 12, 0, 18);

        for (int number = 1; number <= 40; number += 2) {
            LinearLayout row = Ui.row(this);
            row.addView(ticketCard(number), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
            View second = ticketCard(number + 1);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(Ui.dp(this, 10), 0, 0, 0);
            row.addView(second, params);
            Ui.add(page, row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 0, 0, 10);
        }
        TextView disclaimer = Ui.text(this, "Это генерируемые учебные билеты, а не официальные билеты ГАИ и не копия базы «Нового поворота».",
                11, Ui.MUTED, false);
        disclaimer.setGravity(Gravity.CENTER);
        Ui.add(page, disclaimer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                8, 10, 8, 0);
        setScreen("tickets", TAB_TESTS, false, scrollScreen(page, true));
    }

    private View ticketCard(int number) {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16));
        card.setBackground(Ui.ripple(this, Color.WHITE, 18));
        TextView value = Ui.text(this, String.valueOf(number), 25, Ui.GREEN, true);
        value.setGravity(Gravity.CENTER);
        card.addView(value);
        TextView label = Ui.text(this, "БИЛЕТ · 10", 10, Ui.MUTED, true);
        label.setGravity(Gravity.CENTER);
        Ui.add(card, label, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 4, 0, 0);
        Ui.clickable(card, view -> startTicket(number));
        return card;
    }

    private void startTicket(int number) {
        List<Content.Question> questions = repository.createTicket(number);
        if (ticketControlMode) {
            startControl("Билет " + number + " · контроль", questions, 10, 1, TAB_TESTS, number);
        } else {
            startQuiz("Билет " + number + " · тренировка", questions, false, TAB_TESTS);
        }
    }

    private LinearLayout actionCard(String title, String subtitle, String icon, int color, int iconBackground,
                                    View.OnClickListener listener) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 15), Ui.dp(this, 15), Ui.dp(this, 15));
        card.setBackground(Ui.ripple(this, Color.WHITE, 18));
        LinearLayout row = Ui.row(this);
        LinearLayout iconBox = iconButton(icon, color, iconBackground);
        row.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout middle = Ui.column(this);
        TextView titleView = Ui.text(this, title, 15, Ui.INK, true);
        Ui.singleLine(titleView);
        middle.addView(titleView);
        Ui.add(middle, Ui.text(this, subtitle, 11, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0);
        Ui.addWeight(row, middle, 1, 12, 0, 8, 0);
        row.addView(Ui.icon(this, "next", Ui.MUTED, 20));
        card.addView(row);
        Ui.clickable(card, listener);
        return card;
    }

    private void startExam() {
        startControl("Экзамен ×10", repository.createExam(System.currentTimeMillis(), 10),
                10, 1, TAB_TESTS, 0);
    }

    private void startControl20() {
        startControl("Контроль ×20", repository.createExam(System.currentTimeMillis(), 20),
                20, 2, TAB_TESTS, 0);
    }

    private void startControl(String title, List<Content.Question> questions, int minutes,
                              int maxErrors, int backTarget, int ticketNumber) {
        if (questions == null || questions.isEmpty()) return;
        quiz = new QuizSession(title, new ArrayList<>(questions), true, backTarget,
                minutes, maxErrors, true, ticketNumber);
        renderQuiz();
    }

    private void startQuiz(String title, List<Content.Question> questions, boolean exam, int backTarget) {
        if (questions == null || questions.isEmpty()) {
            Toast.makeText(this, "В этом разделе пока нет вопросов", Toast.LENGTH_SHORT).show();
            return;
        }
        quiz = new QuizSession(title, new ArrayList<>(questions), exam, backTarget,
                exam ? 20 : 0, exam ? 2 : -1, exam, 0);
        renderQuiz();
    }

    private void renderQuiz() {
        if (quiz == null || quiz.finished) return;
        timerHandler.removeCallbacks(timerTick);
        Integer savedSelection = quiz.selections[quiz.index];
        quiz.answered = savedSelection != null;
        quiz.selected = savedSelection == null ? -1 : savedSelection;

        LinearLayout page = Ui.column(this);
        addBackHeader(page, quiz.title, this::confirmLeaveQuiz);

        LinearLayout status = Ui.row(this);
        TextView count = Ui.text(this, (quiz.index + 1) + " / " + quiz.questions.size(), 13, Ui.MUTED, true);
        Ui.addWeight(status, count, 1, 0, 0, 0, 0);
        if (quiz.exam) {
            timerText = Ui.text(this, formatTime(quiz.remainingSeconds), 14,
                    quiz.remainingSeconds < 60 ? Ui.RED : Ui.GREEN, true);
            status.addView(Ui.icon(this, "clock", Ui.GREEN, 18));
            Ui.add(status, timerText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    6, 0, 0, 0);
        } else {
            status.addView(Ui.pill(this, "С ОБЪЯСНЕНИЯМИ", Ui.GREEN, Ui.MINT));
        }
        page.addView(status);
        ProgressBar progress = Ui.progress(this, Ui.GREEN_2);
        progress.setProgress(Math.round(answeredCount() * 100f / quiz.questions.size()));
        Ui.add(page, progress, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6), 0, 12, 0, 14);
        addQuestionStrip(page);

        Content.Question current = quiz.questions.get(quiz.index);
        Content.Topic topic = repository.topic(current.topicId);
        page.addView(Ui.pill(this, topic == null ? "ПДД" : topic.shortTitle.toUpperCase(),
                topic != null && topic.updated ? Ui.AMBER : Ui.GREEN,
                topic != null && topic.updated ? Ui.AMBER_BG : Ui.MINT), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.add(page, Ui.text(this, current.question, 23, Ui.INK, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 22);

        List<Integer> answerOrder = quiz.answerOrders.get(quiz.index);
        for (int position = 0; position < answerOrder.size(); position++) {
            final int answer = answerOrder.get(position);
            boolean selected = quiz.answered && quiz.selected == answer;
            boolean correctAnswer = quiz.answered && !quiz.exam && answer == current.correct;
            boolean wrongSelected = selected && !quiz.exam && answer != current.correct;
            int background = Color.WHITE;
            int foreground = Ui.INK;
            if (quiz.exam && selected) {
                background = Ui.MINT;
                foreground = Ui.GREEN;
            } else if (correctAnswer) {
                background = Ui.MINT;
                foreground = Ui.GREEN;
            } else if (wrongSelected) {
                background = Ui.RED_BG;
                foreground = Ui.RED;
            }
            LinearLayout answerCard = Ui.card(this);
            answerCard.setPadding(Ui.dp(this, 15), Ui.dp(this, 15), Ui.dp(this, 15), Ui.dp(this, 15));
            answerCard.setBackground(Ui.ripple(this, background, 17));
            LinearLayout row = Ui.row(this);
            TextView letter = Ui.text(this, String.valueOf((char) ('А' + position)), 13, foreground, true);
            letter.setGravity(Gravity.CENTER);
            letter.setBackground(Ui.roundDp(this, background == Color.WHITE ? Ui.BG : Color.WHITE, 11));
            row.addView(letter, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
            TextView answerText = Ui.text(this, current.answers.get(answer), 15, foreground, false);
            Ui.addWeight(row, answerText, 1, 12, 0, 4, 0);
            if (correctAnswer) row.addView(Ui.icon(this, "check", Ui.GREEN, 22));
            if (wrongSelected) row.addView(Ui.icon(this, "close", Ui.RED, 22));
            answerCard.addView(row);
            if (!quiz.answered) {
                Ui.clickable(answerCard, view -> answerCurrent(answer));
            } else {
                answerCard.setClickable(false);
            }
            Ui.add(page, answerCard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 0, 0, 10);
        }

        if (quiz.answered && quiz.exam) {
            TextView accepted = Ui.text(this, "ОТВЕТ ПРИНЯТ", 13, Ui.GREEN, true);
            accepted.setGravity(Gravity.CENTER);
            accepted.setLetterSpacing(0.1f);
            Ui.add(page, accepted, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 5, 0, 12);
        }

        if (quiz.answered && !quiz.exam) {
            boolean correct = quiz.selected == current.correct;
            LinearLayout explanation = Ui.card(this);
            explanation.setBackground(Ui.roundDp(this, correct ? Ui.MINT : Ui.RED_BG, 18));
            explanation.addView(Ui.text(this, correct ? "Верно!" : "Есть ошибка", 17,
                    correct ? Ui.GREEN : Ui.RED, true));
            Ui.add(explanation, Ui.text(this, current.explanation, 14, Ui.INK, false),
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0);
            Ui.add(explanation, Ui.text(this, current.reference, 11, Ui.MUTED, false),
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0);
            Ui.add(page, explanation, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 6, 0, 14);
        }

        LinearLayout navigation = Ui.row(this);
        if (quiz.index > 0) {
            Button previous = Ui.button(this, "Назад", false);
            Ui.clickable(previous, view -> jumpToQuestion(quiz.index - 1));
            Ui.addWeight(navigation, previous, 0.45f, 0, 0, 8, 0);
        }
        if (quiz.answered) {
            Button next = Ui.button(this, quiz.index == quiz.questions.size() - 1 ? "Завершить" : "Далее", true);
            Ui.clickable(next, view -> nextQuestion());
            Ui.addWeight(navigation, next, 1, 0, 0, 0, 0);
        } else {
            TextView hint = Ui.text(this, "Выберите один вариант ответа", 12, Ui.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            Ui.addWeight(navigation, hint, 1, 0, 0, 0, 0);
        }
        Ui.add(page, navigation, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 0);
        setScreen("quiz", quiz.backTarget, false, scrollScreen(page, true));
        if (quiz.exam) timerHandler.postDelayed(timerTick, 1000);
    }

    private void addQuestionStrip(LinearLayout page) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = Ui.row(this);
        strip.setPadding(0, 0, 0, Ui.dp(this, 4));
        for (int i = 0; i < quiz.questions.size(); i++) {
            final int questionIndex = i;
            Integer selection = quiz.selections[i];
            boolean current = i == quiz.index;
            int background = Color.WHITE;
            int foreground = Ui.MUTED;
            if (selection != null) {
                if (quiz.exam) {
                    background = Color.rgb(220, 230, 224);
                    foreground = Ui.GREEN;
                } else {
                    boolean correct = selection == quiz.questions.get(i).correct;
                    background = correct ? Ui.GREEN_2 : Ui.RED;
                    foreground = Color.WHITE;
                }
            }
            if (current) {
                background = Ui.GREEN;
                foreground = Color.WHITE;
            }
            TextView number = Ui.text(this, String.valueOf(i + 1), 13, foreground, true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.ripple(this, background, 11));
            number.setContentDescription("Вопрос " + (i + 1));
            Ui.clickable(number, view -> jumpToQuestion(questionIndex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            params.setMargins(0, 0, Ui.dp(this, 6), 0);
            strip.addView(number, params);
        }
        scroll.addView(strip);
        Ui.add(page, scroll, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48), 0, 0, 0, 16);
    }

    private int answeredCount() {
        int result = 0;
        for (Integer selection : quiz.selections) if (selection != null) result++;
        return result;
    }

    private void jumpToQuestion(int index) {
        if (quiz == null || index < 0 || index >= quiz.questions.size()) return;
        quiz.index = index;
        renderQuiz();
    }

    private void answerCurrent(int answer) {
        if (quiz == null || quiz.answered) return;
        Content.Question current = quiz.questions.get(quiz.index);
        quiz.selections[quiz.index] = answer;
        quiz.selected = answer;
        quiz.answered = true;
        boolean correct = answer == current.correct;
        if (correct) quiz.score++; else quiz.incorrect.add(current);
        store.recordAnswer(current, correct);
        if (quiz.exam && quiz.incorrect.size() > quiz.maxErrors) {
            showResult();
        } else {
            renderQuiz();
        }
    }

    private void nextQuestion() {
        if (quiz == null) return;
        if (quiz.index >= quiz.questions.size() - 1) {
            if (answeredCount() < quiz.questions.size()) {
                new AlertDialog.Builder(this)
                        .setTitle("Завершить тест?")
                        .setMessage("Без ответа: " + (quiz.questions.size() - answeredCount())
                                + ". Они будут засчитаны как ошибки.")
                        .setNegativeButton("Вернуться", null)
                        .setPositiveButton("Завершить", (dialog, which) -> showResult())
                        .show();
            } else {
                showResult();
            }
            return;
        }
        jumpToQuestion(quiz.index + 1);
    }

    private void showResult() {
        if (quiz == null || quiz.finished) return;
        quiz.finished = true;
        timerHandler.removeCallbacks(timerTick);
        if (quiz.exam) store.recordExam(quiz.score, quiz.questions.size());
        int percent = Math.round(quiz.score * 100f / quiz.questions.size());
        int errors = quiz.questions.size() - quiz.score;
        boolean passed = quiz.exam ? errors <= quiz.maxErrors : percent >= 90;
        LinearLayout page = Ui.column(this);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        addBackHeader(page, "Результат", this::showTests);
        Ui.add(page, Ui.space(this, 12), 1, 1, 0, 0, 0, 0);

        FrameLayout ringFrame = Ui.frame(this);
        ProgressRingView ring = new ProgressRingView(this);
        ring.setFraction(percent / 100f);
        ringFrame.addView(ring, new FrameLayout.LayoutParams(Ui.dp(this, 148), Ui.dp(this, 148), Gravity.CENTER));
        TextView percentText = Ui.text(this, percent + "%", 34, passed ? Ui.GREEN : Ui.RED, true);
        percentText.setGravity(Gravity.CENTER);
        ringFrame.addView(percentText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(ringFrame, new LinearLayout.LayoutParams(Ui.dp(this, 156), Ui.dp(this, 156)));

        TextView title = Ui.text(this, passed ? (quiz.exam ? "Зачёт получен!" : "Отличный результат!") : "Ещё немного практики",
                27, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        Ui.add(page, title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 24, 0, 0);
        String requirement = quiz.exam ? " · допустимо ошибок: " + quiz.maxErrors : "";
        TextView subtitle = Ui.text(this, quiz.score + " из " + quiz.questions.size()
                + " правильных ответов" + requirement, 14, Ui.MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        Ui.add(page, subtitle, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 0);

        LinearLayout summary = Ui.card(this);
        LinearLayout summaryRow = Ui.row(this);
        addResultStat(summaryRow, "Верно", String.valueOf(quiz.score), Ui.GREEN);
        addResultStat(summaryRow, "Ошибки", String.valueOf(errors), Ui.RED);
        addResultStat(summaryRow, "Вопросов", String.valueOf(quiz.questions.size()), Ui.INK);
        summary.addView(summaryRow);
        Ui.add(page, summary, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 24, 0, 0);

        if (!quiz.incorrect.isEmpty()) {
            Button mistakes = Ui.button(this, "Разобрать ошибки · " + quiz.incorrect.size(), false);
            List<Content.Question> incorrectCopy = new ArrayList<>(quiz.incorrect);
            Ui.clickable(mistakes, view -> startQuiz("Разбор ошибок", incorrectCopy, false, TAB_TESTS));
            Ui.add(page, mistakes, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 18, 0, 0);
        }
        Button retry = Ui.button(this, quiz.exam ? "Новая попытка" : "Пройти ещё раз", true);
        Ui.clickable(retry, view -> restartQuiz());
        Ui.add(page, retry, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, quiz.incorrect.isEmpty() ? 18 : 10, 0, 0);
        Button done = Ui.button(this, "К тестам", false);
        Ui.clickable(done, view -> showTests());
        Ui.add(page, done, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 0);
        setScreen("result", TAB_TESTS, false, scrollScreen(page, true));
    }

    private void restartQuiz() {
        if (quiz == null) return;
        if (!quiz.exam) {
            startQuiz(quiz.title, quiz.questions, false, quiz.backTarget);
        } else if (quiz.ticketNumber > 0) {
            startControl(quiz.title, quiz.questions, quiz.timeLimitMinutes, quiz.maxErrors,
                    quiz.backTarget, quiz.ticketNumber);
        } else if ("Экзамен ×10".equals(quiz.title)) {
            startExam();
        } else if ("Контроль ×20".equals(quiz.title)) {
            startControl20();
        } else {
            startControl(quiz.title, quiz.questions, quiz.timeLimitMinutes, quiz.maxErrors,
                    quiz.backTarget, 0);
        }
    }

    private void addResultStat(LinearLayout row, String label, String value, int color) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER);
        TextView valueView = Ui.text(this, value, 25, color, true);
        valueView.setGravity(Gravity.CENTER);
        item.addView(valueView);
        TextView labelView = Ui.text(this, label, 11, Ui.MUTED, false);
        labelView.setGravity(Gravity.CENTER);
        Ui.add(item, labelView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 4, 0, 0);
        row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private String formatTime(int seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    private void updateTimer() {
        if (timerText != null && quiz != null) {
            timerText.setText(formatTime(quiz.remainingSeconds));
            timerText.setTextColor(quiz.remainingSeconds < 60 ? Ui.RED : Ui.GREEN);
        }
    }

    private void confirmLeaveQuiz() {
        if (quiz == null || quiz.finished) {
            showTests();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Прервать тест?")
                .setMessage("Текущая попытка не будет сохранена, но ответы останутся в статистике.")
                .setNegativeButton("Продолжить", null)
                .setPositiveButton("Прервать", (dialog, which) -> showTests())
                .show();
    }

    private void showSearch() {
        LinearLayout page = Ui.column(this);
        addBackHeader(page, "Поиск", this::showRules);
        EditText search = searchField("Например: СПМ, зимние шины, перекрёсток");
        search.setCompoundDrawables(null, null, null, null);
        Ui.add(page, search, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54), 0, 0, 0, 18);
        LinearLayout results = Ui.column(this);
        page.addView(results);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                populateSearchResults(results, editable.toString());
            }
        };
        search.addTextChangedListener(watcher);
        populateSearchResults(results, "");
        setScreen("search", TAB_RULES, false, scrollScreen(page, true));
        search.requestFocus();
    }

    private void populateSearchResults(LinearLayout results, String query) {
        results.removeAllViews();
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            TextView hint = Ui.text(this, "Ищите по конспектам, вопросам и пояснениям. Раскладка с «е» также находит слова с «ё».",
                    14, Ui.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            results.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        List<Content.Topic> topics = repository.searchTopics(normalized);
        List<Content.Question> questions = repository.searchQuestions(normalized);
        if (topics.isEmpty() && questions.isEmpty()) {
            TextView empty = Ui.text(this, "Ничего не найдено. Попробуйте другое слово.", 15, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            Ui.add(results, empty, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    0, 30, 0, 0);
            return;
        }
        if (!topics.isEmpty()) {
            results.addView(Ui.label(this, "Разделы · " + topics.size()));
            Ui.add(results, Ui.space(this, 10), 1, 1, 0, 0, 0, 0);
            for (Content.Topic topic : topics) results.addView(topicCard(topic));
        }
        if (!questions.isEmpty()) {
            results.addView(Ui.label(this, "Вопросы · " + questions.size()));
            Ui.add(results, Ui.space(this, 10), 1, 1, 0, 0, 0, 0);
            int limit = Math.min(questions.size(), 12);
            for (int i = 0; i < limit; i++) {
                Content.Question question = questions.get(i);
                LinearLayout card = Ui.card(this);
                card.setBackground(Ui.ripple(this, Color.WHITE, 18));
                card.addView(Ui.text(this, question.question, 14, Ui.INK, true));
                Ui.add(card, Ui.text(this, "Открыть вопрос с объяснением", 11, Ui.GREEN_2, true),
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0);
                Ui.clickable(card, view -> startQuiz("Найденный вопрос",
                        Collections.singletonList(question), false, TAB_RULES));
                Ui.add(results, card, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        0, 0, 0, 10);
            }
        }
    }

    private void checkForUpdates(boolean manual) {
        if (BuildConfig.DEBUG) {
            if (manual) Toast.makeText(this, "Проверка доступна в release-сборке", Toast.LENGTH_SHORT).show();
            return;
        }
        if (updateCheckInProgress) return;
        updateCheckInProgress = true;
        if (manual) Toast.makeText(this, "Проверяем новую версию…", Toast.LENGTH_SHORT).show();
        updateManager.check(new UpdateManager.CheckListener() {
            @Override public void onUpdate(UpdateManager.UpdateInfo update) {
                updateCheckInProgress = false;
                showUpdateDialog(update);
            }

            @Override public void onNoUpdate() {
                updateCheckInProgress = false;
                if (manual) Toast.makeText(MainActivity.this,
                        "У вас последняя версия · " + BuildConfig.VERSION_NAME, Toast.LENGTH_SHORT).show();
            }

            @Override public void onError(String message) {
                updateCheckInProgress = false;
                if (manual) Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showUpdateDialog(UpdateManager.UpdateInfo update) {
        new AlertDialog.Builder(this)
                .setTitle("Доступна версия " + update.versionName)
                .setMessage("У вас: " + BuildConfig.VERSION_NAME + "\n\n" + update.notes
                        + "\n\nAPK будет загружен и проверен внутри приложения. Браузер не понадобится.")
                .setNegativeButton("Позже", null)
                .setPositiveButton("Обновить", (dialog, which) -> downloadUpdate(update))
                .show();
    }

    private void downloadUpdate(UpdateManager.UpdateInfo update) {
        LinearLayout layout = Ui.column(this);
        layout.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 4));
        TextView status = Ui.text(this, "Подготовка загрузки…", 14, Ui.INK, false);
        layout.addView(status);
        ProgressBar progress = Ui.progress(this, Ui.GREEN_2);
        progress.setMax(100);
        Ui.add(layout, progress, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8), 0, 18, 0, 12);
        TextView security = Ui.text(this, "После загрузки проверим SHA-256 и сертификат подписи", 11, Ui.MUTED, false);
        layout.addView(security);
        downloadDialog = new AlertDialog.Builder(this)
                .setTitle("Обновление " + update.versionName)
                .setView(layout)
                .setCancelable(false)
                .create();
        downloadDialog.show();
        updateManager.download(update, new UpdateManager.DownloadListener() {
            @Override public void onProgress(int percent) {
                progress.setProgress(percent);
                status.setText(percent < 100 ? "Загружено: " + percent + "%" : "APK проверен. Запускаем установку…");
            }

            @Override public void onVerified(File apk) {
                if (downloadDialog != null) downloadDialog.dismiss();
                pendingInstall = apk;
                requestVerifiedInstall();
            }

            @Override public void onError(String message) {
                if (downloadDialog != null) downloadDialog.dismiss();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Не удалось обновиться")
                        .setMessage(message)
                        .setPositiveButton("Понятно", null)
                        .show();
            }
        });
    }

    private void requestVerifiedInstall() {
        if (pendingInstall == null) return;
        if (updateManager.canInstall()) {
            File apk = pendingInstall;
            pendingInstall = null;
            updateManager.install(apk);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Разрешите установку обновлений")
                .setMessage("Android требует одно системное разрешение «Установка из неизвестных источников». "
                        + "Откроются настройки системы, не браузер. После разрешения вернитесь в приложение.")
                .setNegativeButton("Отмена", (dialog, which) -> pendingInstall = null)
                .setPositiveButton("Открыть настройки", (dialog, which) -> updateManager.openInstallSettings())
                .show();
    }

    private void showProfile() {
        quiz = null;
        LinearLayout page = Ui.column(this);
        addMainHeader(page, "Ваш прогресс", "Данные хранятся только на устройстве", false);

        LinearLayout overview = Ui.card(this);
        LinearLayout row = Ui.row(this);
        FrameLayout ringFrame = Ui.frame(this);
        ProgressRingView ring = new ProgressRingView(this);
        ring.setFraction(store.readCount() / (float) repository.getTopics().size());
        ringFrame.addView(ring, new FrameLayout.LayoutParams(Ui.dp(this, 100), Ui.dp(this, 100), Gravity.CENTER));
        TextView ringText = Ui.text(this, store.readCount() + "/" + repository.getTopics().size(), 23, Ui.GREEN, true);
        ringText.setGravity(Gravity.CENTER);
        ringFrame.addView(ringText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(ringFrame, new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 110)));
        LinearLayout overviewText = Ui.column(this);
        overviewText.addView(Ui.text(this, "Учебный план", 20, Ui.INK, true));
        Ui.add(overviewText, Ui.text(this, store.readCount() + " разделов прочитано", 13, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 7, 0, 0);
        Ui.add(overviewText, Ui.text(this, store.masteredCount() + " вопросов освоено", 13, Ui.GREEN_2, true),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 0);
        Ui.addWeight(row, overviewText, 1, 12, 0, 0, 0);
        overview.addView(row);
        page.addView(overview);

        LinearLayout stats = Ui.row(this);
        int accuracy = store.totalAnswers() == 0 ? 0 : Math.round(store.correctAnswers() * 100f / store.totalAnswers());
        stats.addView(statCard("Точность", accuracy + "%", "Все ответы"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout examsCard = statCard("Экзамены", String.valueOf(store.examCount()),
                store.bestExam() < 0 ? "Пока нет попыток" : "Лучший: " + store.bestExam() + "%");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(Ui.dp(this, 12), 0, 0, 0);
        stats.addView(examsCard, params);
        Ui.add(page, stats, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 12, 0, 0);

        addSectionHeading(page, "Избранное", store.favorites().isEmpty() ? "Сохраняйте разделы закладкой" : null);
        Set<String> favorites = store.favorites();
        if (favorites.isEmpty()) {
            LinearLayout empty = actionCard("Пока нет закладок", "Откройте раздел и нажмите на закладку",
                    "bookmark", Ui.GREEN, Ui.MINT, view -> showRules());
            page.addView(empty);
        } else {
            for (Content.Topic topic : repository.getTopics()) {
                if (favorites.contains(topic.id)) page.addView(topicCard(topic));
            }
        }

        addSectionHeading(page, "О материалах", repository.getMeta().edition);
        LinearLayout legal = Ui.card(this);
        legal.addView(Ui.text(this, "Правовая основа", 17, Ui.INK, true));
        Ui.add(legal, Ui.text(this, repository.getMeta().legalBasis, 13, Ui.INK, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0);
        Ui.add(legal, Ui.text(this, repository.getMeta().notice, 12, Ui.MUTED, false),
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0);
        Button official = Ui.button(this, "Официальный текст на Pravo.by  ↗", false);
        Ui.clickable(official, view -> openUrl(repository.getMeta().officialRulesUrl));
        Ui.add(legal, official, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 14, 0, 0);
        Button amendment = Ui.button(this, "Указ № 295 от 04.08.2025  ↗", false);
        Ui.clickable(amendment, view -> openUrl(repository.getMeta().amendmentUrl));
        Ui.add(legal, amendment, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 8, 0, 0);
        page.addView(legal);

        LinearLayout updater = actionCard("Обновление приложения", "Версия " + BuildConfig.VERSION_NAME
                        + " · проверить и установить без браузера",
                "refresh", Ui.GREEN, Ui.MINT, view -> checkForUpdates(true));
        Ui.add(page, updater, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 14, 0, 0);

        LinearLayout privacy = actionCard("Приватность и офлайн", "Без аккаунта, рекламы и аналитики",
                "info", Ui.GREEN, Ui.MINT, view -> Toast.makeText(this,
                        "Прогресс хранится на устройстве. Интернет используется только для проверки и загрузки обновлений.", Toast.LENGTH_LONG).show());
        Ui.add(page, privacy, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 14, 0, 0);

        Button reset = Ui.button(this, "Сбросить прогресс", false);
        reset.setTextColor(Ui.RED);
        reset.setBackground(Ui.ripple(this, Ui.RED_BG, 15));
        Ui.clickable(reset, view -> new AlertDialog.Builder(this)
                .setTitle("Сбросить весь прогресс?")
                .setMessage("Прочитанные разделы, закладки, ответы и результаты экзаменов будут удалены.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    store.reset();
                    showProfile();
                }).show());
        Ui.add(page, reset, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 22, 0, 0);
        TextView version = Ui.text(this, "ПДД Беларусь · версия " + BuildConfig.VERSION_NAME
                + " · учебная редакция 01.09.2025", 11, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER);
        Ui.add(page, version, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 18, 0, 0);
        setScreen("profile", TAB_PROFILE, true, scrollScreen(page, true));
    }

    private LinearLayout statCard(String title, String value, String subtitle) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, title, 12, Ui.MUTED, true));
        Ui.add(card, Ui.text(this, value, 28, Ui.INK, true), ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0);
        Ui.add(card, Ui.text(this, subtitle, 11, Ui.MUTED, false), ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0);
        return card;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if ("quiz".equals(screen)) {
            confirmLeaveQuiz();
        } else if ("topic".equals(screen)) {
            goBackFromTopic();
        } else if ("tickets".equals(screen)) {
            showTests();
        } else if ("search".equals(screen)) {
            showRules();
        } else if ("result".equals(screen)) {
            showTests();
        } else if (activeTab != TAB_HOME) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstall != null && updateManager != null && updateManager.canInstall()) {
            File apk = pendingInstall;
            pendingInstall = null;
            updateManager.install(apk);
        }
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerTick);
        if (updateManager != null) updateManager.close();
        super.onDestroy();
    }

    private static final class QuizSession {
        final String title;
        final List<Content.Question> questions;
        final boolean exam;
        final int backTarget;
        final int timeLimitMinutes;
        final int maxErrors;
        final boolean floatingAnswers;
        final int ticketNumber;
        final List<Content.Question> incorrect = new ArrayList<>();
        final Integer[] selections;
        final List<List<Integer>> answerOrders = new ArrayList<>();
        int index;
        int score;
        int selected = -1;
        boolean answered;
        boolean finished;
        int remainingSeconds;

        QuizSession(String title, List<Content.Question> questions, boolean exam, int backTarget,
                    int timeLimitMinutes, int maxErrors, boolean floatingAnswers, int ticketNumber) {
            this.title = title;
            this.questions = questions;
            this.exam = exam;
            this.backTarget = backTarget;
            this.timeLimitMinutes = timeLimitMinutes;
            this.maxErrors = maxErrors;
            this.floatingAnswers = floatingAnswers;
            this.ticketNumber = ticketNumber;
            this.remainingSeconds = timeLimitMinutes * 60;
            this.selections = new Integer[questions.size()];
            long answerSeed = System.currentTimeMillis();
            for (Content.Question question : questions) {
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < question.answers.size(); i++) order.add(i);
                if (floatingAnswers) {
                    Collections.shuffle(order, new java.util.Random(question.id.hashCode() * 31L + answerSeed));
                }
                answerOrders.add(order);
            }
        }
    }
}
