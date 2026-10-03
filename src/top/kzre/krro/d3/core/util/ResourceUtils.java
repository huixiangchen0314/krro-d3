package top.kzre.krro.d3.core.util;

/**
 * 资源工具。
 */
public final class ResourceUtils {

    private ResourceUtils() {}

    /**
     * 关闭所有资源，延迟抛出异常。
     *
     * <p>逐个 try-close：
     * <ul>
     *   <li>第一个异常作为主异常返回</li>
     *   <li>后续异常 addSuppressed 到主异常</li>
     *   <li>非 {@link RuntimeException} 的异常被包装为 RuntimeException</li>
     * </ul>
     *
     * @return 第一个异常（已被包装为 RuntimeException），无异常返回 null
     */
    public static RuntimeException closeDelayError(Iterable<? extends AutoCloseable> closeables) {
        if (closeables == null) return null;

        RuntimeException first = null;
        for (AutoCloseable c : closeables) {
            if (c == null) continue;
            try {
                c.close();
            } catch (Throwable t) {
                if (first == null) {
                    first = asRuntime(t);
                } else {
                    first.addSuppressed(t);
                }
            }
        }
        return first;
    }

    /**
     * 关闭所有资源，延迟抛出异常。
     *
     * @return 第一个异常（已被包装为 RuntimeException），无异常返回 null
     */
    public static RuntimeException closeDelayError(AutoCloseable... closeables) {
        if (closeables == null) return null;

        RuntimeException first = null;
        for (AutoCloseable c : closeables) {
            if (c == null) continue;
            try {
                c.close();
            } catch (Throwable t) {
                if (first == null) {
                    first = asRuntime(t);
                } else {
                    first.addSuppressed(t);
                }
            }
        }
        return first;
    }

    // ─── 内部 ───

    /**
     * 把 Throwable 转换为 RuntimeException。
     *
     * <p>已经是 RuntimeException 的原样返回。
     * {@link Error} 直接抛出（不吞掉严重错误）。
     * 其他包装为 RuntimeException。
     */
    private static RuntimeException asRuntime(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        return new RuntimeException(t);
    }
}