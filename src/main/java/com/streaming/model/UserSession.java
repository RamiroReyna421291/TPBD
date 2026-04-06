package com.streaming.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@RedisHash("user:session")
public class UserSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String lastContentId;
    private Long timestamp;

    public UserSession() {}

    public UserSession(String userId, String lastContentId, Long timestamp) {
        this.userId = userId;
        this.lastContentId = lastContentId;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getLastContentId() { return lastContentId; }
    public void setLastContentId(String lastContentId) { this.lastContentId = lastContentId; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}