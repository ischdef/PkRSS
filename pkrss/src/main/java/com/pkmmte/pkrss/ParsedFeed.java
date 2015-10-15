package com.pkmmte.pkrss;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by stefan.schulze on 15.10.2015.
 */
public class ParsedFeed {
    private Channel channel;
    private List<Article> articles;

    public ParsedFeed() {
        this.channel = new Channel();
        this.articles = new ArrayList<>();
    }

    public ParsedFeed(Channel channel, List<Article> articles) {
        this.channel = channel;
        this.articles = articles;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public List<Article> getArticles() {
        return articles;
    }

    public void setArticles(List<Article> articles) {
        this.articles = articles;
    }

    public void clear() {
        if (this.articles != null) {
            this.articles.clear();
        }
        if (this.channel != null) {
            this.channel = new Channel();
        }
    }

    public void addArticle(Article article) {
        this.articles.add(article);
    }
}
