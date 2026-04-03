package io.agentscope.examples.monolithchat.service;

import io.agentscope.core.hook.Hook;
import io.agentscope.examples.monolithchat.conf.AgentStudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class StudioHookFactory {

    private static final Logger log = LoggerFactory.getLogger(StudioHookFactory.class);

    private final AgentStudioProperties properties;

    public StudioHookFactory(AgentStudioProperties properties) {
        this.properties = properties;
    }

    public List<Hook> buildHooks(String sessionId, String userId) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        Hook studioHook = createStudioHook(sessionId, userId);
        if (studioHook == null) {
            return List.of();
        }
        return List.of(studioHook);
    }

    private Hook createStudioHook(String sessionId, String userId) {
        try {
            Object config = createStudioConfig();
            Class<?> hookClass = Class.forName("io.agentscope.core.studio.StudioMessageHook");
            Hook hook = createHookWithBuilder(hookClass, config, sessionId, userId);
            if (hook != null) {
                return hook;
            }
            hook = createHookWithConstructor(hookClass, config, sessionId, userId);
            if (hook != null) {
                return hook;
            }
            log.warn("Agent Studio 已启用，但未能构建 StudioMessageHook，降级为无 Hook 模式");
            return null;
        } catch (Throwable e) {
            log.warn("构建 Agent Studio Hook 失败，降级为无 Hook 模式: {}", e.getMessage());
            return null;
        }
    }

    private Object createStudioConfig() {
        try {
            Class<?> configClass = Class.forName("io.agentscope.core.studio.StudioConfig");
            Method builderMethod = findMethod(configClass, "builder");
            if (builderMethod != null) {
                Object builder = builderMethod.invoke(null);
                apply(builder, "enabled", properties.isEnabled());
                apply(builder, "url", properties.getUrl());
                apply(builder, "project", properties.getProject());
                apply(builder, "runNamePrefix", properties.getRunNamePrefix());
                Method build = findMethod(builder.getClass(), "build");
                if (build != null) {
                    return build.invoke(builder);
                }
            }
        } catch (Exception e) {
            log.warn("构建 StudioConfig 失败，将尝试无配置方式: {}", e.getMessage());
        }
        return null;
    }

    private Hook createHookWithBuilder(Class<?> hookClass, Object config, String sessionId, String userId) {
        try {
            Method builderMethod = findMethod(hookClass, "builder");
            if (builderMethod == null) {
                return null;
            }
            Object builder = builderMethod.invoke(null);
            apply(builder, "config", config);
            apply(builder, "studioConfig", config);
            apply(builder, "enabled", properties.isEnabled());
            apply(builder, "url", properties.getUrl());
            apply(builder, "project", properties.getProject());
            apply(builder, "runNamePrefix", properties.getRunNamePrefix());
            apply(builder, "runName", buildRunName(sessionId));
            apply(builder, "sessionId", sessionId);
            apply(builder, "userId", userId);
            Method build = findMethod(builder.getClass(), "build");
            if (build == null) {
                return null;
            }
            Object hook = build.invoke(builder);
            if (hook instanceof Hook h) {
                return h;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Hook createHookWithConstructor(Class<?> hookClass, Object config, String sessionId, String userId) {
        try {
            List<Constructor<?>> constructors = new ArrayList<>(List.of(hookClass.getDeclaredConstructors()));
            constructors.sort(Comparator.comparingInt(Constructor::getParameterCount));
            for (Constructor<?> constructor : constructors) {
                Object[] args = buildConstructorArgs(constructor.getParameterTypes(), config, sessionId, userId);
                if (args == null) {
                    continue;
                }
                constructor.setAccessible(true);
                Object hook = constructor.newInstance(args);
                if (hook instanceof Hook h) {
                    return h;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Object[] buildConstructorArgs(Class<?>[] parameterTypes, Object config, String sessionId, String userId) {
        List<String> strings = List.of(buildRunName(sessionId), sessionId, userId, properties.getProject(), properties.getUrl(), properties.getRunNamePrefix());
        int stringIndex = 0;
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (config != null && type.isInstance(config)) {
                args[i] = config;
            } else if (type == String.class) {
                if (stringIndex >= strings.size()) {
                    return null;
                }
                args[i] = strings.get(stringIndex++);
            } else if (type == boolean.class || type == Boolean.class) {
                args[i] = properties.isEnabled();
            } else if (type == long.class || type == Long.class) {
                args[i] = System.currentTimeMillis();
            } else {
                return null;
            }
        }
        return args;
    }

    private String buildRunName(String sessionId) {
        String prefix = properties.getRunNamePrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "stream-chat";
        }
        String sid = sessionId == null || sessionId.isBlank() ? "unknown" : sessionId;
        return prefix + "-" + sid + "-" + System.currentTimeMillis();
    }

    private Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private void apply(Object target, String methodName, Object value) {
        if (target == null || value == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> paramType = method.getParameterTypes()[0];
            if (isAssignable(paramType, value.getClass())) {
                try {
                    method.invoke(target, value);
                    return;
                } catch (Exception ignored) {
                    return;
                }
            }
        }
    }

    private boolean isAssignable(Class<?> targetType, Class<?> valueType) {
        if (targetType.isAssignableFrom(valueType)) {
            return true;
        }
        if (targetType == boolean.class && valueType == Boolean.class) {
            return true;
        }
        if (targetType == long.class && valueType == Long.class) {
            return true;
        }
        return false;
    }
}
