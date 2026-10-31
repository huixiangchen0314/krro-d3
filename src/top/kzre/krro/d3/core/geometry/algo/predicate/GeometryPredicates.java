package top.kzre.krro.d3.core.geometry.algo.predicate;

import top.kzre.krro.util.math.KMath;

/**
 * 2D / 3D 几何谓词——三角化、耳裁剪、凸性判断的纯函数。
 *
 * <p><b>不依赖 kmath</b>：这里只放“几何语义”的谓词——叉积、点在三角形内、
 * 凸性、距离平方。kmath 的向量 / 矩阵 / 标量通用运算不重复。
 *
 * <p><b>纯函数</b>：无状态、无分配、无 I/O。所有方法都是 {@code static}，
 * 让 JIT 自由内联——O(1) 谓词接口化会阻止内联，得不偿失。
 *
 * <p><b>线程契约</b>：只读参数，无共享状态。任意线程可调。
 */
public final class GeometryPredicates {

    private GeometryPredicates() {}

    // ═══════════════════════════════════════════════
    // 2D 叉积
    // ═══════════════════════════════════════════════

    /**
     * 2D 叉积——{@code (b - a) × (p - a)}，返回 z 分量。
     *
     * <p>正值表示 {@code p} 在 {@code a→b} 的左侧（CCW 绕序下），
     * 负值表示右侧，零表示共线。
     */
    public static float cross(float ax, float ay,
                              float bx, float by,
                              float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    // ═══════════════════════════════════════════════
    // 点在三角形内
    // ═══════════════════════════════════════════════

    /**
     * 点 {@code (px, py)} 是否在三角形 {@code (a, b, c)} 内或边界上。
     *
     * <p><b>边界语义</b>：用 {@code >=} / {@code <=} 而不是 {@code >} / {@code <}——
     * 边界上的顶点（共线）也算“包含”。耳裁剪中这会略微保守
     * （共线点阻止耳裁剪），但对编辑器网格安全。
     *
     * @param ccw 三角形绕序——{@code true} 表示 CCW，{@code false} 表示 CW
     */
    public static boolean pointInTriangle(float px, float py,
                                          float ax, float ay,
                                          float bx, float by,
                                          float cx, float cy,
                                          boolean ccw) {
        float d1 = cross(ax, ay, bx, by, px, py);
        float d2 = cross(bx, by, cx, cy, px, py);
        float d3 = cross(cx, cy, ax, ay, px, py);
        return ccw ? (d1 >= 0f && d2 >= 0f && d3 >= 0f)
                : (d1 <= 0f && d2 <= 0f && d3 <= 0f);
    }

    // ═══════════════════════════════════════════════
    // 凸性
    // ═══════════════════════════════════════════════

    /**
     * 顶点 {@code curr} 在 {@code prev → curr → next} 处是否凸。
     *
     * <p>{@code proj} 是投影后的 2D 坐标，{@code float2[n]}——
     * {@code proj[i*2]} 是 x，{@code proj[i*2+1]} 是 y。
     *
     * <p>CCW 多边形中凸顶点的叉积为正，CW 中为负。
     */
    public static boolean isConvex(float[] proj, int prev, int curr, int next,
                                   boolean ccw) {
        float c = cross(
                proj[prev * 2], proj[prev * 2 + 1],
                proj[curr * 2], proj[curr * 2 + 1],
                proj[next * 2], proj[next * 2 + 1]);
        return ccw ? c > 0f : c < 0f;
    }

    // ═══════════════════════════════════════════════
    // 距离平方
    // ═══════════════════════════════════════════════

    /**
     * 3D 距离平方——{@code d[i..i+2]} 和 {@code d[j..j+2]} 的平方距离。
     *
     * <p>用平方距离避免 {@code sqrt}——比较两条对角线长度时只需要
     * 平方距离，{@code sqrt} 是单调的，不影响大小关系。
     */
    public static float squaredDist3(float[] d, int i, int j) {
        float dx = d[i]     - d[j];
        float dy = d[i + 1] - d[j + 1];
        float dz = d[i + 2] - d[j + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 2D 距离平方——{@code d[i..i+1]} 和 {@code d[j..j+1]} 的平方距离。
     */
    public static float squaredDist2(float[] d, int i, int j) {
        float dx = d[i]     - d[j];
        float dy = d[i + 1] - d[j + 1];
        return dx * dx + dy * dy;
    }
}