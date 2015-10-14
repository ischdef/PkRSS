package com.pkmmte.pkrss;

import android.net.Uri;

/**
 * Class for storing all parsed channel information
 */
public class Channel {
    private String title;
    private String description;
    private String language;
    private Uri    link;
    private Uri    image;
    private String encoding;

    public Channel() {
        this.title       = null;
        this.description = null;
        this.language    = null;
        this.link        = null;
        this.image       = null;
        this.encoding    = null;
    }

    public Channel(String title, String description, String language, Uri link, Uri image, String encoding) {
        this.title       = title;
        this.description = description;
        this.language    = language;
        this.link        = link;
        this.image       = image;
        this.encoding    = encoding;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Uri getLink() {
        return link;
    }

    public void setLink(Uri link) {
        this.link = link;
    }

    public Uri getImage() {
        return image;
    }

    public void setImage(Uri image) {
        this.image = image;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}