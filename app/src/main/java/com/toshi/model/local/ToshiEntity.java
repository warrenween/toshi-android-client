package com.toshi.model.local;

public interface ToshiEntity {
    String getTokenId();
    String getAvatar();
    String getDisplayName();
    Double getReputationScore();
    Double getAverageRating();
    String getAbout();
    int getReviewCount();
}
