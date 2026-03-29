package com.agent4j.bilibili.repository;

import com.agent4j.bilibili.model.VideoMetrics;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VideoMetricRepository {

    private final JdbcTemplate jdbcTemplate;

    public VideoMetricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initialize();
    }

    private void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS video_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bvid TEXT NOT NULL,
                    title TEXT,
                    author TEXT,
                    view INTEGER,
                    like_count INTEGER,
                    coin INTEGER,
                    favorite INTEGER,
                    reply INTEGER,
                    share INTEGER,
                    duration INTEGER,
                    avg_view_duration REAL,
                    like_rate REAL,
                    completion_rate REAL,
                    competition_score REAL,
                    source TEXT,
                    created_at TEXT NOT NULL
                )
                """);
    }

    public void saveVideoMetrics(VideoMetrics metrics) {
        jdbcTemplate.update("""
                        INSERT INTO video_metrics (
                            bvid, title, author, view, like_count, coin, favorite, reply, share,
                            duration, avg_view_duration, like_rate, completion_rate, competition_score, source, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                metrics.getBvid(),
                metrics.getTitle(),
                metrics.getAuthor(),
                metrics.getView(),
                metrics.getLike(),
                metrics.getCoin(),
                metrics.getFavorite(),
                metrics.getReply(),
                metrics.getShare(),
                metrics.getDuration(),
                metrics.getAvgViewDuration(),
                metrics.getLikeRate(),
                metrics.getCompletionRate(),
                metrics.getCompetitionScore(),
                metrics.getSource(),
                LocalDateTime.now().withNano(0).toString());
    }

    public List<Map<String, Object>> getHistory(String bvid, int limit) {
        return jdbcTemplate.queryForList("""
                        SELECT * FROM video_metrics
                        WHERE bvid = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                bvid,
                limit);
    }

    public Map<String, Object> latestSnapshot(String bvid) {
        List<Map<String, Object>> history = getHistory(bvid, 1);
        return history.isEmpty() ? null : history.get(0);
    }
}
