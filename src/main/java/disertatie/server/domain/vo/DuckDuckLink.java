package disertatie.server.domain.vo;

public class DuckDuckLink {
    String text;
    String url;
    Double fuzzyRelevance;

    public DuckDuckLink(String text, String url, Double fuzzyRelevance) {
        this.text = text;
        this.url = url;
        this.fuzzyRelevance = fuzzyRelevance;
    }

    public DuckDuckLink() {
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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
