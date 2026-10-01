(ns top.kzre.krro.d3.core.geometry.bmesh.ops.add
  "BMesh 添加操作——流形假设。

   假设：
     1. 流形数据
     2. 顶点已存在
     3. 面用到的边已存在——由调用方显式提供

   不做边的查找 / 创建——边数组由调用方传入。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access        :as access]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.primitive :as prim]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.link      :as link])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)
    (top.kzre.krro.d3.core.geometry.bmesh
      EdgeEndpoints LoopOwnership FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 边
;; ═══════════════════════════════════════════════

(defn add-edge-manifold!
  "添加一条连接 v0 与 v1 的边。

   调用方保证 v0 / v1 之间不存在边。
   更新 v0 / v1 的 out-edge（若之前是 -1）。

   返回新边索引。"
  ^long [^BMeshEditor editor ^long v0 ^long v1]
  (let [e (long (prim/add-isolated-edge! editor))]
    (link/set-edge-endpoints! editor e (EdgeEndpoints. (int v0) (int v1)))

    (when (== -1 (access/get-vert-out-edge editor v0))
      (link/set-vert-out-edge! editor v0 e))
    (when (== -1 (access/get-vert-out-edge editor v1))
      (link/set-vert-out-edge! editor v1 e))

    e))

;; ═══════════════════════════════════════════════
;; 径向链插入——内部
;; ═══════════════════════════════════════════════

(defn- insert-loop-into-edge-manifold!
  "把 loop l 插入到边 e 的径向链。

   流形假设——e 的径向链长度 ≤ 2。
   第一条——e.loop = l —— radial 保持 -1。
   第二条——l0 与 l1 双向循环 —— 4 字段全设。"
  [^BMeshEditor editor ^long e ^long l]
  (let [entry (long (access/get-edge-loop editor e))]
    (if (== entry -1)
      ;; 第一条——默认 radial 都是 -1
      (link/set-edge-loop! editor e l)
      ;; 第二条——双向对称
      (do
        (access/set-loop-radial-next! editor entry l)
        (access/set-loop-radial-prev! editor entry l)   ; ← 新增
        (access/set-loop-radial-next! editor l entry)   ; ← 新增
        (access/set-loop-radial-prev! editor l entry)))))  ; ← 新增

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
     edges = [e01 e12 e20]      ← e01 连接 v0-v1，e12 连接 v1-v2，e20 连接 v2-v0

   例（四边面）：
     verts = [v0 v1 v2 v3]
     edges = [e01 e12 e23 e30]

   调用方保证：
     1. 流形数据
     2. 所有 verts 已存在
     3. 所有 edges 已存在
     4. edges[i] 的两端点确实是 verts[i] 和 verts[(i+1) % n]（正反皆可）
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