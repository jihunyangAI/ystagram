package com.yangstagram.media;

/** 저장된 이미지 한 장. url은 원본(긴 변 최대 1440px), thumbUrl은 그리드용 작은 사진. */
public record StoredImage(String url, String thumbUrl, int width, int height) {
}
