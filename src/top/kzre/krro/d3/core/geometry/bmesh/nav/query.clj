(ns top.kzre.krro.d3.core.geometry.bmesh.nav.query
  "导航——查询。

   基于 step / access —— 判断 / 查找 / 度量。
   全部只读——无副作用。

   ── 谓词 ──
     isolated-vert?
     isolated-edge?
     isolated-face?
     boundary-edge?           径向链 == 1
     manifold-edge?           径向链 == 2
     boundary-vert?           有边界边

   ── 度量 ──
     vert-degree              绕顶点的拓扑边数（磁盘环）
     face-degree
     edge-radial-count

   ── 查找 ──
     find-edge                绕顶点查找 v0-v1 边

   ── 绕顶点遍历 ──
   基于磁盘环——不依赖 loop / 面——无扇假设——
   边界顶点也返回全部拓扑邻边。
   O(deg) —— deg 是绕 v 的边数。

   ── 无扇假设 ──
   所有函数基于拓扑边或径向链——完整覆盖。
   流形 / 非流形——均正确。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access   :as access]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step :as step])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 度量——无扇假设
;; ═══════════════════════════════════════════════

(defn face-degree
  "面 f 的环长。"
  ^long [^BMeshEditor editor ^long f]
  (step/face-len editor f))

(defn edge-radial-count
  "边 e 的径向链长度——0 / 1 / 2 / N。"
  ^long [^BMeshEditor editor ^long e]
  (let [start (long (step/edge-loop editor e))]
    (if (== start -1)
      0
      (loop [cur start
             n   1]
        (let [nxt (long (step/loop-radial-next editor cur))]
          (if (or (== nxt -1) (== nxt start))
            n
            (recur nxt (inc n))))))))

;; ═══════════════════════════════════════════════
;; 谓词——无扇假设
;; ═══════════════════════════════════════════════

(defn isolated-vert?
  "顶点 v 无出边。"
  [^BMeshEditor editor ^long v]
  (== -1 (long (step/vert-out-edge editor v))))

(defn isolated-edge?
  "边 e 无径向环。"
  [^BMeshEditor editor ^long e]
  (== -1 (long (step/edge-loop editor e))))

(defn isolated-face?
  "面 f 无环入口。"
  [^BMeshEditor editor ^long f]
  (== -1 (long (step/face-loop editor f))))

(defn boundary-edge?
  "边 e 是边界边——径向链长度 1。"
  [^BMeshEditor editor ^long e]
  (== 1 (edge-radial-count editor e)))

(defn manifold-edge?
  "边 e 是流形边——径向链长度 2。"
  [^BMeshEditor editor ^long e]
  (== 2 (edge-radial-count editor e)))

;; ═══════════════════════════════════════════════
;; 度量——绕顶点（磁盘环）
;; ═══════════════════════════════════════════════

(defn- next-edge-around-vert
  "绕 v 从边 e 出发的下一条边——磁盘环读。

   返回 -1 —— 异常（e 不在 v 的磁盘环上）。"
  ^long [^BMeshEditor editor ^long v ^long e]
  (let [ep    (access/get-edge-endpoints editor e)
        at-v0 (== (long (.v0 ep)) v)]
    (if at-v0
      (access/get-edge-v0-ring-next editor e)
      (access/get-edge-v1-ring-next editor e))))

(defn vert-degree
  "顶点 v 的绕边数（拓扑邻边数）。

   基于磁盘环——遍历 v 周围的全部出边——含「入边」——
   无扇假设——边界顶点也完整。"
  ^long [^BMeshEditor editor ^long v]
  (let [start (long (step/vert-out-edge editor v))]
    (if (== start -1)
      0
      (loop [e start
             n 1]
        (let [nxt (long (next-edge-around-vert editor v e))]
          (if (or (== nxt -1) (== nxt start))
            n
            (recur nxt (inc n))))))))

;; ═══════════════════════════════════════════════
;; 谓词——绕顶点（磁盘环）
;; ═══════════════════════════════════════════════

(defn boundary-vert?
  "顶点 v 有边界边。

   基于磁盘环——遍历全部拓扑边——不漏判。"
  [^BMeshEditor editor ^long v]
  (let [start (long (step/vert-out-edge editor v))]
    (if (== start -1)
      false
      (loop [e start]
        (if (boundary-edge? editor e)
          true
          (let [nxt (long (next-edge-around-vert editor v e))]
            (if (or (== nxt -1) (== nxt start))
              false
              (recur nxt))))))))

;; ═══════════════════════════════════════════════
;; 查找——绕顶点（磁盘环）
;; ═══════════════════════════════════════════════

(defn find-edge
  "在 v0 周围查找连接 v0 v1 的边——无返回 -1。

   基于磁盘环——完整覆盖所有拓扑边。"
  ^long [^BMeshEditor editor ^long v0 ^long v1]
  (let [start (long (step/vert-out-edge editor v0))]
    (if (== start -1)
      -1
      (loop [e start]
        (if (== (long (step/edge-other-vert editor e v0)) v1)
          e
          (let [nxt (long (next-edge-around-vert editor v0 e))]
            (if (or (== nxt -1) (== nxt start))
              -1
              (recur nxt))))))))

(set! *unchecked-math* nil)