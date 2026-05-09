package com.koval.trainingplannerbackend.club;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koval.club")
public class ClubProperties {

    private final Session session = new Session();
    private final Gazette gazette = new Gazette();

    public Session getSession() { return session; }
    public Gazette getGazette() { return gazette; }

    public static class Session {
        private int defaultPastWeeks = 2;
        private int defaultFutureWeeks = 12;

        public int getDefaultPastWeeks() { return defaultPastWeeks; }
        public void setDefaultPastWeeks(int v) { this.defaultPastWeeks = v; }

        public int getDefaultFutureWeeks() { return defaultFutureWeeks; }
        public void setDefaultFutureWeeks(int v) { this.defaultFutureWeeks = v; }
    }

    public static class Gazette {
        private int maxTitleLength = 100;
        private int maxContentLength = 2000;

        public int getMaxTitleLength() { return maxTitleLength; }
        public void setMaxTitleLength(int v) { this.maxTitleLength = v; }

        public int getMaxContentLength() { return maxContentLength; }
        public void setMaxContentLength(int v) { this.maxContentLength = v; }
    }
}
