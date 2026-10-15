package vn.farm.starter.events;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class EventDeduplicator {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public EventDeduplicator(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
    }

    /** true = vừa xử lý xong; false = event đã xử lý trước đó (bỏ qua). */
    public boolean runOnce(UUID eventId, String consumer, Runnable action) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            try {
                jdbc.update("INSERT INTO dbo.ProcessedEvents (EventId, Consumer) VALUES (?, ?)",
                        eventId.toString(), consumer);
            } catch (DuplicateKeyException dup) {
                return false;
            }
            action.run();   // lỗi ở đây sẽ rollback luôn dòng đánh dấu ở trên
            return true;
        }));
    }
}