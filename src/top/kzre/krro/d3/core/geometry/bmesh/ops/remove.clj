(ns top.kzre.krro.d3.core.geometry.bmesh.ops.remove
  "BMesh 删除操作。

  分两层：
    孤立化    isolate-face-manifold! / isolate-edge-manifold! /
              isolate-vert-manifold!
    复合移除  remove-face-manifold! / remove-edge-manifold! /
              remove-vert-manifold!

  孤立化手段：
    面   摘掉它的所有 loop 的径向链，释放这些 loop，面拓扑归 NONE
    边   删掉所有共享它的面
    顶点 删掉它的所有出边

  后缀约定：
    -manifold!  径向链长度 ≤ 2 假设。若同时含单扇假设，取更严格的 -manifold!。
    -in-fan     仅单扇顶点假设。
    无后缀      无假设。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access        :as access]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step      :as step]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.walk      :as walk]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.link      :as link]
    [top.kzre.krro.d3.core.geometry.bmesh.ops.primitive :as prim])
  (:import
    (java.util HashSet)
    (top.kzre.krro.d3.core.geometry.bmesh
      BMeshEditor FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 辅助——私有
;; ═══════════════════════════════════════════════

(defn- detach-loop-radial-manifold!
  "从 loop l 所在边的径向链中完整摘除 l——含 edge-loop 入口更新。

  链约定（add/insert-loop-into-edge-manifold! 建立）：
    单元素    rn == -1 && rp == -1
    两元素    rn == rp（都指向对面 loop）
    三元素+   rn 是下一条, rp 是上一条

  流形假设——链长度 ≤ 2。

    rn==-1 rp==-1   单元素          e.loop = -1
    rn==rp          两元素          对面 rn/rp = -1, e.loop = 对面
    其他            三元素以上       通用摘除

  不释放任何索引。"
  [^BMeshEditor editor ^long l]
  (let [e  (long (step/loop-edge editor l))
        rn (long (step/loop-radial-next editor l))
        rp (long (step/loop-radial-prev editor l))]
    (cond
      ;; 单元素——无对面
      (and (== rn -1) (== rp -1))
      (link/set-edge-loop! editor e -1)

      ;; 两元素——对面就是 rn（== rp）
      (== rn rp)
      (do
        (access/set-loop-radial-next! editor rn -1)
        (access/set-loop-radial-prev! editor rn -1)
        (link/set-edge-loop! editor e rn))

      ;; 三元素以上——通用摘除
      :else
      (do
        (access/set-loop-radial-prev! editor rn rp)
        (access/set-loop-radial-next! editor rp rn)
        (when (== (long (step/edge-loop editor e)) l)
          (link/set-edge-loop! editor e rn))))))

(defn- find-other-out-edge-in-fan
  "绕 v 的当前扇内找第一条不是 exclude-e 的边。无则 -1。

  单扇假设——多扇顶点只遍历当前扇，其他扇被漏掉。

  必须在删除 exclude-e 之前调用——旋转依赖对面 loop。"
  ^long [^BMeshEditor editor ^long v ^long exclude-e]
  (let [^ints es (walk/edge-around-vertex editor v)
        n        (alength es)]
    (loop [i 0]
      (if (>= i n)
        -1
        (let [e (long (aget es i))]
          (if (not= e exclude-e)
            e
            (recur (inc i))))))))

;; ═══════════════════════════════════════════════
;; 孤立化
;; ═══════════════════════════════════════════════

(defn isolate-face-manifold!
  "使面 f 孤立——摘除其所有 loop 的径向链——释放这些 loop——
   面拓扑归 NONE。

  面本身保留。边、顶点保留——边的 radial-count 各减 1。

  流形假设——径向链长度 ≤ 2。非流形抛 IllegalStateException。

  返回 nil。"
  [^BMeshEditor editor ^long f]
  (let [start (long (step/face-loop editor f))]
    (when (not= start -1)
      (let [loops (loop [cur start
                         acc []]
                    (let [acc' (conj acc cur)
                          nxt  (long (step/loop-next editor cur))]
                      (if (== nxt start)
                        acc'
                        (recur nxt acc'))))]
        (doseq [l loops]
          (detach-loop-radial-manifold! editor l))
        (doseq [l loops]
          (prim/remove-isolated-loop! editor l))
        (link/set-face-topology! editor f FaceTopology/NONE)))))

(declare remove-edge-manifold! remove-face-manifold!)

(defn isolate-edge-manifold!
  "使边 e 孤立——删除所有共享它的面。

  边本身保留。相邻面通过 remove-face-manifold! 完整删除——
  连带释放面内的所有 loop，其中就有 e 上的 loop。
  删完后 e.radial-count 归 0，e.loop == -1。

  流形假设——径向链长度 ≤ 2（经 remove-face-manifold! 传递）。

  返回 nil。"
  [^BMeshEditor editor ^long e]
  (let [^ints loops (walk/loop-around-edge editor e)
        n           (alength loops)
        faces       (HashSet.)]
    (dotimes [i n]
      (.add faces (long (step/loop-face editor (aget loops i)))))
    (doseq [^long f (seq faces)]
      (remove-face-manifold! editor f))))

(defn isolate-vert-manifold!
  "使顶点 v 孤立——删除它的所有出边。

  顶点本身保留。出边通过 remove-edge-manifold! 完整删除——
  连带删掉边的相邻面和边本身。
  删完后 v.out-edge == -1。

  流形假设——单扇顶点 + 径向链长度 ≤ 2。

  返回 nil。"
  [^BMeshEditor editor ^long v]
  (let [^ints edges (walk/edge-around-vertex editor v)
        n           (alength edges)]
    (dotimes [i n]
      (remove-edge-manifold! editor (long (aget edges i))))))

;; ═══════════════════════════════════════════════
;; 复合移除
;; ═══════════════════════════════════════════════

(defn remove-face-manifold!
  "删除面 f——isolate-face-manifold! + 释放面索引。

  不级联：边、顶点保留。
  f 已孤立（face-loop == -1）时仅释放面索引。

  流形假设——径向链长度 ≤ 2。

  返回 nil。"
  [^BMeshEditor editor ^long f]
  (isolate-face-manifold! editor f)
  (prim/remove-isolated-face! editor f))

(defn remove-edge-manifold!
  "删除边 e——级联相邻面——更新端点 out-edge——释放边索引。

  替代 out-edge 在删面之前找（旋转依赖面）。
  端点顶点不删除——即使变孤立顶点。

  流形假设——径向链长度 ≤ 2 + 单扇顶点。

  返回 nil。"
  [^BMeshEditor editor ^long e]
  (let [ep   (access/get-edge-endpoints editor e)
        v0   (long (.v0 ep))
        v1   (long (.v1 ep))
        alt0 (long (find-other-out-edge-in-fan editor v0 e))
        alt1 (long (find-other-out-edge-in-fan editor v1 e))]
    (isolate-edge-manifold! editor e)
    (prim/remove-isolated-edge! editor e)
    (when (== (long (step/vert-out-edge editor v0)) e)
      (link/set-vert-out-edge! editor v0 alt0))
    (when (== (long (step/vert-out-edge editor v1)) e)
      (link/set-vert-out-edge! editor v1 alt1))))

(defn remove-vert-manifold!
  "删除顶点 v——级联所有出边及相邻面——释放顶点索引。

  出边列表在删任何东西之前收集完毕。

  流形假设——单扇顶点 + 径向链长度 ≤ 2。

  返回 nil。"
  [^BMeshEditor editor ^long v]
  (isolate-vert-manifold! editor v)
  (prim/remove-isolated-vert! editor v))

(set! *unchecked-math* nil)