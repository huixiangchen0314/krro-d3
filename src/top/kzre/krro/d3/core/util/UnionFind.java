package top.kzre.krro.d3.core.util;

/**
 * 并查集——按秩合并 + 完全路径压缩。
 *
 * <p>容量按需增长——不缩容——配合复用场景。
 *
 * <p>{@link #reset(int)} 重置为 n 个独立集合——
 * 之后 {@link #find(int)} / {@link #union(int, int)} 使用。
 *
 * <p>单次 {@code reset} + m 次操作 —— 近似 O(m · α(n))。
 *
 * <p><b>线程契约</b>：非线程安全。
 */
public final class UnionFind {

    private int[]  parent;
    private byte[] rank;

    public UnionFind() { this(16); }

    public UnionFind(int n) {
        this.parent = new int[Math.max(1, n)];
        this.rank   = new byte[Math.max(1, n)];
    }

    private void ensureCapacity(int n) {
        if (parent.length < n) {
            this.parent = new int[n];
            this.rank   = new byte[n];
        }
    }

    /**
     * 重置为 n 个独立集合——parent[i] = i——rank[i] = 0。
     * 容量自动扩展。
     */
    public void reset(int n) {
        ensureCapacity(n);
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            rank[i]   = 0;
        }
    }

    /**
     * 查找 x 的根——完全路径压缩。
     */
    public int find(int x) {
        int root = x;
        while (parent[root] != root) root = parent[root];
        // 完全压缩
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    /**
     * 合并 x 与 y 所在的集合——按秩。
     */
    public void union(int x, int y) {
        int rx = find(x);
        int ry = find(y);
        if (rx == ry) return;
        if (rank[rx] < rank[ry]) {
            parent[rx] = ry;
        } else if (rank[rx] > rank[ry]) {
            parent[ry] = rx;
        } else {
            parent[ry] = rx;
            rank[rx]++;
        }
    }

    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }

    public int capacity() { return parent.length; }
}