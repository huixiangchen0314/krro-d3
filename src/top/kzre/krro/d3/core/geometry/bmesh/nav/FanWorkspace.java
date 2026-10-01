package top.kzre.krro.d3.core.geometry.bmesh.nav;

import java.util.Arrays;

/**
 * 绕顶点扇分析的 workspace —— 复用 deg(v) 级临时数组。
 *
 * <p>并查集独立——用 {@link top.kzre.krro.d3.core.util.UnionFind}——
 * 与本 workspace 分开传入 —— 各自复用。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class FanWorkspace {

    public int[] rootToFan;
    public int[] sizes;
    public int[] offsets;
    public int[] result;
    public int[] cursor;

    public FanWorkspace() { this(16); }

    public FanWorkspace(int initialCapacity) {
        int cap = Math.max(16, initialCapacity);
        this.rootToFan = new int[cap];
        this.sizes     = new int[cap];
        this.offsets   = new int[cap + 1];
        this.result    = new int[cap];
        this.cursor    = new int[cap];
    }

    public void ensureCapacity(int n) {
        if (rootToFan.length < n) {
            this.rootToFan = new int[n];
            this.sizes     = new int[n];
            this.offsets   = new int[n + 1];
            this.result    = new int[n];
            this.cursor    = new int[n];
        }
    }

    public void resetRootToFan(int n) {
        Arrays.fill(rootToFan, 0, n, -1);
    }
}