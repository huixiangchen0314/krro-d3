(ns top.kzre.krro.d3.core.geometry.bmesh.ops.add
  "BMesh 添加操作——流形假设。

   假设：
     1. 流形数据
     2. 顶点已存在
     3. 面用到的边已存在——由调用方显式提供

   不做边的查找 / 创建——边数组由调用方传入。

   磁盘环：
     add-edge-manifold! 建边时——把边插入两端点的磁盘环——
     单元素时 next == prev == 自身——多元素时插到 entry 之前。
     绕顶点遍历边不依赖 loop / 面——边界顶点也完整。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access        :as access]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.primitive :as prim]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.link      :as link])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)
    (top.kzre.krro.d3.core.geometry.bmesh
      EdgeEndpoints EdgeDiskRing LoopOwnership FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 磁盘环——私有
;; ═══════════════════════════════════════════════

(defn- disk-insert-manifold!
  "把边 e 插入到顶点 v 周围的磁盘环。

   v.out-edge == -1：e 成为该顶点唯一出边——自环
   否则：插到 v.out-edge 之前——保持 entry 作为入口"
  [^BMeshEditor editor ^long e ^long v]
  (let [ep    (access/get-edge-endpoints editor e)
        at-v0 (== (long (.v0 ep)) v)
        entry (long (access/get-vert-out-edge editor v))]
    (if (== entry -1)
      ;; e 自环——v.out-edge = e
      (do
        (link/set-vert-out-edge! editor v e)
        (if at-v0
          (do
            (access/set-edge-v0-ring-next! editor e e)
            (access/set-edge-v0-ring-prev! editor e e))
          (do
            (access/set-edge-v1-ring-next! editor e e)
            (access/set-edge-v1-ring-prev! editor e e))))
      ;; 插入到 entry 之前
      (let [entry-ep    (access/get-edge-endpoints editor entry)
            entry-at-v0 (== (long (.v0 entry-ep)) v)
            entry-prev  (if entry-at-v0
                          (long (access/get-edge-v0-ring-prev editor entry))
                          (long (access/get-edge-v1-ring-prev editor entry)))
            ;; e.next = entry, e.prev = entry-prev
            _ (if at-v0
                (do
                  (access/set-edge-v0-ring-next! editor e entry)
                  (access/set-edge-v0-ring-prev! editor e entry-prev))
                (do
                  (access/set-edge-v1-ring-next! editor e entry)
                  (access/set-edge-v1-ring-prev! editor e entry-prev)))
            ;; entry.prev = e
            _ (if entry-at-v0
                (access/set-edge-v0-ring-prev! editor entry e)
                (access/set-edge-v1-ring-prev! editor entry e))
            ;; entry-prev.next = e
            ep-ep    (access/get-edge-endpoints editor entry-prev)
            ep-at-v0 (== (long (.v0 ep-ep)) v)]
        (if ep-at-v0
          (access/set-edge-v0-ring-next! editor entry-prev e)
          (access/set-edge-v1-ring-next! editor entry-prev e))))))

;; ═══════════════════════════════════════════════
;; 边
;; ═══════════════════════════════════════════════

(defn add-edge-manifold!
  "添加一条连接 v0 与 v1 的边。

   调用方保证 v0 / v1 之间不存在边。
   建边后把 e 插入两端点的磁盘环——更新 v.out-edge（若原来为 -1）。

   返回新边索引。"
  ^long [^BMeshEditor editor ^long v0 ^long v1]
  (let [e (long (prim/add-isolated-edge! editor))]
    (link/set-edge-endpoints! editor e (EdgeEndpoints. (int v0) (int v1)))
    (link/set-edge-disk-ring! editor e EdgeDiskRing/NONE)
    (disk-insert-manifold! editor e v0)
    (disk-insert-manifold! editor e v1)
    e))

;; ═══════════════════════════════════════════════
;; 径向链插入——私有
;; ═══════════════════════════════════════════════

(defn- insert-loop-into-edge-manifold!
  "把 loop l 插入到边 e 的径向链。

   流形假设——e 的径向链长度 ≤ 2。
   第一条——e.loop = l —— radial 保持 -1。
   第二条——l0 与 l1 双向循环 —— 4 字段全设。"
  [^BMeshEditor editor ^long e ^long l]
  (let [entry (long (access/get-edge-loop editor e))]
    (if (== entry -1)
      (link/set-edge-loop! editor e l)
      (do
        (access/set-loop-radial-next! editor entry l)
        (access/set-loop-radial-prev! editor entry l)
        (access/set-loop-radial-next! editor l entry)
        (access/set-loop-radial-prev! editor l entry)))))

;; ═══════════════════════════════════════════════
;; 面
;; ═══════════════════════════════════════════════

(defn add-face-manifold!
  "添加面。

   verts 和 edges 是平行的两个数组——共同描述面的边界环：

     verts.length == edges.length == n
     edges[i] 是连接 verts[i] 与 verts[(i+1) % n] 的边

   即：
     edges[0]   连接 verts[0]   和 verts[1]
     edges[1]   连接 verts[1]   和 verts[2]
     ...
     edges[n-2] 连接 verts[n-2] 和 verts[n-1]
     edges[n-1] 连接 verts[n-1] 和 verts[0]      ← 闭合回起点

   例（三角面）：
     verts = [v0 v1 v2]
     edges = [e01 e12 e20]

   例（四边面）：
     verts = [v0 v1 v2 v3]
     edges = [e01 e12 e23 e30]

   调用方保证：
     1. 流形数据
     2. 所有 verts 已存在
     3. 所有 edges 已存在
     4. edges[i] 的两端点确实是 verts[i] 和 verts[(i+1) % n]
     5. 该面尚未存在

   返回新面索引。"
  ^long [^BMeshEditor editor ^ints verts ^ints edges ^long n]
  (when (< n 3)
    (throw (IllegalArgumentException.
             "face requires at least 3 vertices, got: " n)))

  (let [f     (long (prim/add-isolated-face! editor))
        loops (int-array n)]

    ;; 1. 分配 loop
    (dotimes [i n]
      (aset loops i (int (prim/add-isolated-loop! editor))))

    ;; 2. loop 归属——每个 loop 知道 (vert, edge, face)
    (dotimes [i n]
      (let [l (aget loops i)
            v (aget verts i)
            e (aget edges i)]
        (link/set-loop-ownership!
          editor l (LoopOwnership. (int v) (int e) (int f)))))

    ;; 3. 面内环——loops[i].next = loops[i+1]，闭合
    (dotimes [i n]
      (let [l        (aget loops i)
            next-idx (if (== i (dec n)) 0 (inc i))
            nl       (aget loops next-idx)]
        (link/link-loop-next! editor l nl)))

    ;; 4. 径向链——loops[i] 插入到 edges[i] 的径向链
    (dotimes [i n]
      (insert-loop-into-edge-manifold! editor (aget edges i) (aget loops i)))

    ;; 5. 面拓扑——入口 loop + 环长
    (link/set-face-topology!
      editor f (FaceTopology. (int (aget loops 0)) (int n)))

    f))

(set! *unchecked-math* nil)