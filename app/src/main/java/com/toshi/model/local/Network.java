package com.toshi.model.local;

public class Network {
    private String id;
    private String name;
    private String url;

    public @Networks.Type String getId() {
        return id;
    }

    public Network setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Network setName(String name) {
        this.name = name;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Network setUrl(String url) {
        this.url = url;
        return this;
    }
}