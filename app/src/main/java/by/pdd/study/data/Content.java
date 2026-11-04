package by.pdd.study.data;

import java.util.ArrayList;
import java.util.List;

/** Immutable in-memory representation of the bundled learning material. */
public final class Content {
    private Content() { }

    public static final class Meta {
        public String title;
        public String edition;
        public String legalBasis;
        public String officialRulesUrl;
        public String amendmentUrl;
        public String notice;
    }

    public static final class Section {
        public String heading;
        public String body;
        public final List<String> bullets = new ArrayList<>();
    }

    public static final class Topic {
        public String id;
        public String number;
        public String title;
        public String shortTitle;
        public String summary;
        public String accent;
        public int minutes;
        public boolean updated;
        public final List<Section> sections = new ArrayList<>();
    }

    public static final class Question {
        public String id;
        public String topicId;
        public String question;
        public final List<String> answers = new ArrayList<>();
        public int correct;
        public String explanation;
        public String reference;
    }
}
