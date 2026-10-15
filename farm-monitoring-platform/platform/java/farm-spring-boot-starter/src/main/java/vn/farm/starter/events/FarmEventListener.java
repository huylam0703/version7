package vn.farm.starter.events;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FarmEventListener {
    /** Routing key = tên event, ví dụ "readings.synced". */
    String event();
    /** Mặc định: <SERVICE_NAME>.<event>. */
    String queue() default "";
}