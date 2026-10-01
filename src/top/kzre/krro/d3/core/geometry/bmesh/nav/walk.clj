(ns top.kzre.krro.d3.core.geometry.bmesh.nav.walk
  "导航——遍历。

   从起点出发——沿某种链——遍历所有相关元素。
   返回 int[]——含起点——回到起点或遇 -1 停。

   ── 六个遍历 ──
     loop-around-face      f → 该面所有 loop（面内链）
     loop-around-edge      e → 该边所有 loop（径向链）
     loop-around-vertex    v → 从 v 出发的所有 loop（出 loop）
     edge-around-vertex    v → 绕 v 的所有拓扑边（磁盘环）
     face-around-edge      e → 该边所有邻面
     vert-around-face      f → 该面所有顶点

   ── 双版本 ──
     xxx        —— 内部构造 IntList——返回 int[]
     xxx-into   —— 用户传入 IntList——填充后返回同一 buf

   into 版本——先 clear buf——再填充——
   调用方复用 buf——稳态零分配。

   ── 绕顶点遍历 ──
     loop-around-vertex 基于 loop 环——只返回从 v 出发的 loop。
     edge-around-vertex 基于磁盘环——返回全部拓扑邻边——含边界顶点。

   ── 流形 / 通用 ──
     loop-around-vertex 有两套：
       -manifold —— 用流形 loop 旋转——O(1) 每步
       无后缀    —— 用通用 loop 旋转——O(径向链长) 每步

     edge-around-vertex 只有一套——磁盘环——O(1) 每步。

   全部只读——不修改。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access        :as access]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step      :as step]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.rotate    :as rotate])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)
    (top.kzre.krro.d3.core.util IntList)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; loop-around-face
;; ═══════════════════════════════════════════════

(defn loop-around-face-into ^ints [^BMeshEditor editor ^long f ^IntList buf]
  (.clear buf)
  (let [start (long (step/face-loop editor f))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int cur))
        (let [nxt (long (step/loop-next editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn loop-around-face ^ints [^BMeshEditor editor ^long f]
  (loop-around-face-into editor f (IntList.)))

;; ═══════════════════════════════════════════════
;; loop-around-edge
;; ═══════════════════════════════════════════════

(defn loop-around-edge-into ^ints [^BMeshEditor editor ^long e ^IntList buf]
  (.clear buf)
  (let [start (long (step/edge-loop editor e))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int cur))
        (let [nxt (long (step/loop-radial-next editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn loop-around-edge ^ints [^BMeshEditor editor ^long e]
  (loop-around-edge-into editor e (IntList.)))

;; ═══════════════════════════════════════════════
;; loop-around-vertex —— 通用（出 loop 集合）
;; ═══════════════════════════════════════════════

(defn loop-around-vertex-into ^ints [^BMeshEditor editor ^long v ^IntList buf]
  (.clear buf)
  (let [start (long (rotate/first-loop-from-vert editor v))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int cur))
        (let [nxt (long (rotate/next-loop-around-vert editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn loop-around-vertex ^ints [^BMeshEditor editor ^long v]
  (loop-around-vertex-into editor v (IntList.)))

;; ═══════════════════════════════════════════════
;; loop-around-vertex —— 流形（出 loop 集合）
;; ═══════════════════════════════════════════════

(defn loop-around-vertex-manifold-into ^ints [^BMeshEditor editor ^long v ^IntList buf]
  (.clear buf)
  (let [start (long (rotate/first-loop-from-vert editor v))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int cur))
        (let [nxt (long (rotate/next-loop-around-vert-manifold editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn loop-around-vertex-manifold ^ints [^BMeshEditor editor ^long v]
  (loop-around-vertex-manifold-into editor v (IntList.)))

;; ═══════════════════════════════════════════════
;; edge-around-vertex —— 唯一版本（磁盘环）
;; ═══════════════════════════════════════════════

(defn edge-around-vertex-into ^ints [^BMeshEditor editor ^long v ^IntList buf]
  (.clear buf)
  (let [start (long (step/vert-out-edge editor v))]
    (when (not= start -1)
      (loop [e start]
        (.add buf (int e))
        (let [ep    (access/get-edge-endpoints editor e)
              at-v0 (== (long (.v0 ep)) v)
              nxt   (if at-v0
                      (long (access/get-edge-v0-ring-next editor e))
                      (long (access/get-edge-v1-ring-next editor e)))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn edge-around-vertex ^ints [^BMeshEditor editor ^long v]
  (edge-around-vertex-into editor v (IntList.)))

;; ═══════════════════════════════════════════════
;; face-around-edge
;; ═══════════════════════════════════════════════

(defn face-around-edge-into ^ints [^BMeshEditor editor ^long e ^IntList buf]
  (.clear buf)
  (let [start (long (step/edge-loop editor e))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int (step/loop-face editor cur)))
        (let [nxt (long (step/loop-radial-next editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn face-around-edge ^ints [^BMeshEditor editor ^long e]
  (face-around-edge-into editor e (IntList.)))

;; ═══════════════════════════════════════════════
;; vert-around-face
;; ═══════════════════════════════════════════════

(defn vert-around-face-into ^ints [^BMeshEditor editor ^long f ^IntList buf]
  (.clear buf)
  (let [start (long (step/face-loop editor f))]
    (when (not= start -1)
      (loop [cur start]
        (.add buf (int (step/loop-vert editor cur)))
        (let [nxt (long (step/loop-next editor cur))]
          (when (and (not= nxt -1) (not= nxt start))
            (recur nxt))))))
  (.toArray buf))

(defn vert-around-face ^ints [^BMeshEditor editor ^long f]
  (vert-around-face-into editor f (IntList.)))

(set! *unchecked-math* nil)