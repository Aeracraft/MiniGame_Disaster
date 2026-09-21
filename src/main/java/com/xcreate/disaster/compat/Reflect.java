package com.xcreate.disaster.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 反射工具。存在的唯一理由是让跨版本易碎点集中在这一处，
 * 避免散落到主逻辑里。
 *
 * <p>所有方法在失败时都返回 {@code null} 或 {@code false} 而不是抛异常——
 * 调用方负责决定「拿不到时怎么办」，而绝大多数情况下答案是「降级，别崩」。</p>
 */
public final class Reflect {

    private static final Logger LOG = Logger.getLogger("Disaster");

    private Reflect() {
    }

    /** 类是否存在。用于平台探测与可选依赖判定。 */
    public static boolean hasClass(String className) {
        try {
            Class.forName(className, false, Reflect.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 按顺序尝试若干候选类名，返回第一个存在的。
     * 跨版本改名时把新旧名字都列上即可。
     */
    public static Class<?> firstPresentClass(String... classNames) {
        for (String name : classNames) {
            try {
                return Class.forName(name, false, Reflect.class.getClassLoader());
            } catch (Throwable ignored) {
                // 继续试下一个
            }
        }
        return null;
    }

    public static Class<?> findClass(Class<?> owner, String className) {
        try {
            return Class.forName(className, true, owner.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 沿继承链查找字段，逐级提升可访问性。 */
    public static Field findField(Class<?> type, String... candidateNames) {
        if (type == null) {
            return null;
        }
        for (String name : candidateNames) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (Throwable t) {
                    break;
                }
            }
        }
        return null;
    }

    /** 沿继承链查找无参或指定参数的方法。 */
    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                break;
            }
        }
        return null;
    }

    public static Object getField(Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (Throwable t) {
            LOG.log(Level.FINE, "读取字段失败: " + field.getName(), t);
            return null;
        }
    }

    public static boolean setField(Field field, Object target, Object value) {
        if (field == null) {
            return false;
        }
        try {
            field.set(target, value);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "写入字段失败: " + field.getName(), t);
            return false;
        }
    }

    public static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable t) {
            LOG.log(Level.FINE, "调用方法失败: " + method.getName(), t);
            return null;
        }
    }

    /** 枚举常量按名字取值，名字不存在时返回 {@code null}（跨版本枚举增删很常见）。 */
    public static Object enumConstant(Class<?> enumType, String... candidateNames) {
        if (enumType == null || !enumType.isEnum()) {
            return null;
        }
        Object[] constants = enumType.getEnumConstants();
        for (String candidate : candidateNames) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(candidate)) {
                    return constant;
                }
            }
        }
        return null;
    }
}
