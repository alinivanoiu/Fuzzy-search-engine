package disertatie.server.domain.vo;

public class GoogleSearchLink {
    String title;
    String summary;
    String url;
    Double fuzzyRelevance;

    public GoogleSearchLink(String title, String summary, String url, Double fuzzyRelevance) {
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.fuzzyRelevance = fuzzyRelevance;
    }

    public GoogleSearchLink() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Double getFuzzyRelevance() {
        return fuzzyRelevance;
    }

    public void setFuzzyRelevance(Double fuzzyRelevance) {
        this.fuzzyRelevance = fuzzyRelevance;
    }
}
