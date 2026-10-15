package vn.farm.starter.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import vn.farm.core.EventEnvelope;
import vn.farm.core.FarmEvents;

public class FarmEventListenerRegistrar implements SmartInitializingSingleton, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(FarmEventListenerRegistrar.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ApplicationContext ctx;
    private final ConnectionFactory cf;
    private final AmqpAdmin admin;
    private final ObjectMapper mapper;
    private final EventDeduplicator dedup;
    private final String service;
    private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();

    public FarmEventListenerRegistrar(ApplicationContext ctx, ConnectionFactory cf, AmqpAdmin admin,
                                      ObjectMapper mapper, EventDeduplicator dedup, String service) {
        this.ctx = ctx; this.cf = cf; this.admin = admin;
        this.mapper = mapper; this.dedup = dedup; this.service = service;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.startsWith("scopedTarget.")) continue;
            Class<?> type = ctx.getType(name);
            if (type == null) continue;
            Map<Method, FarmEventListener> found = MethodIntrospector.selectMethods(
                    ClassUtils.getUserClass(type),
                    (MethodIntrospector.MetadataLookup<FarmEventListener>) m ->
                            AnnotatedElementUtils.findMergedAnnotation(m, FarmEventListener.class));
            if (found.isEmpty()) continue;
            Object bean = ctx.getBean(name);
            found.forEach((m, ann) -> register(bean, AopUtils.selectInvocableMethod(m, bean.getClass()), ann));
        }
    }

    private void register(Object bean, Method m, FarmEventListener ann) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length < 1 || p.length > 2 || (p.length == 2 && p[1] != EventEnvelope.class)) {
            throw new IllegalStateException(m + ": chữ ký phải là (Payload) hoặc (Payload, EventEnvelope<?>)");
        }
        Class<?> payloadType = p[0];
        boolean withEnvelope = p.length == 2;
        String queue = ann.queue().isBlank() ? service + "." + ann.event() : ann.queue();

        TopicExchange exchange = new TopicExchange(FarmEvents.EXCHANGE, true, false);
        Queue main = QueueBuilder.durable(queue)
                .deadLetterExchange("")                    // default exchange
                .deadLetterRoutingKey(queue + ".dlq")
                .build();
        Queue dlq = QueueBuilder.durable(queue + ".dlq").build();
        admin.declareExchange(exchange);
        admin.declareQueue(dlq);
        admin.declareQueue(main);
        admin.declareBinding(BindingBuilder.bind(main).to(exchange).with(ann.event()));

        SimpleMessageListenerContainer c = new SimpleMessageListenerContainer(cf);
        c.setBeanName(queue);
        c.setQueueNames(queue);
        c.setPrefetchCount(10);
        c.setDefaultRequeueRejected(false);   // lỗi -> sang DLQ, không quay lại queue
        c.setMessageListener((MessageListener) msg -> onMessage(msg, bean, m, queue, payloadType, withEnvelope));
        c.afterPropertiesSet();
        c.start();
        containers.add(c);
        log.info("Đã đăng ký @FarmEventListener: event={} queue={} (DLQ={}.dlq)", ann.event(), queue, queue);
    }

    private void onMessage(Message msg, Object bean, Method m, String consumer,
                           Class<?> payloadType, boolean withEnvelope) {
        EventEnvelope<Object> env = parse(msg, payloadType);
        MDC.put("eventId", env.eventId().toString());
        if (env.traceId() != null) MDC.put("traceId", env.traceId());
        try {
            RuntimeException last = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    boolean done = dedup.runOnce(env.eventId(), consumer, () -> invoke(bean, m, env, withEnvelope));
                    if (!done) log.info("Bỏ qua event trùng {}", env.eventId());
                    return;
                } catch (RuntimeException e) {
                    last = e;
                    log.warn("Xử lý {} lỗi (lần {}/{}): {}", env.eventType(), attempt, MAX_ATTEMPTS, e.toString());
                    if (attempt < MAX_ATTEMPTS) pause(500L * attempt);
                }
            }
            throw new AmqpRejectAndDontRequeueException("Hết số lần thử, chuyển sang DLQ", last);
        } finally {
            MDC.remove("eventId");
            MDC.remove("traceId");
        }
    }

    private EventEnvelope<Object> parse(Message msg, Class<?> payloadType) {
        try {
            JsonNode r = mapper.readTree(msg.getBody());
            return new EventEnvelope<>(
                    UUID.fromString(r.required("eventId").asText()),
                    r.required("eventType").asText(),
                    r.path("version").asInt(1),
                    OffsetDateTime.parse(r.required("occurredAt").asText()),
                    r.path("producer").asText(null),
                    r.path("traceId").asText(null),
                    mapper.treeToValue(r.required("payload"), payloadType));
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Envelope hoặc payload không hợp lệ", e);
        }
    }

    private void invoke(Object bean, Method m, EventEnvelope<Object> env, boolean withEnvelope) {
        try {
            ReflectionUtils.makeAccessible(m);
            if (withEnvelope) m.invoke(bean, env.payload(), env); else m.invoke(bean, env.payload());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void destroy() { containers.forEach(SimpleMessageListenerContainer::stop); }
}