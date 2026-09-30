(ns top.kzre.krro.d3.core.geometry.bmesh.nav.step
  "导航——基础单步 + 派生单步。

   基础单步——直接读——O(1)：
     loop-next / loop-prev / loop-radial-next / loop-radial-prev
     loop-vert / loop-edge / loop-face
     edge-v0 / edge-v1 / edge-loop
     vert-out-edge
     face-loop / face-len

   派生单步——组合基础单步——O(1)：
     loop-other-vert
     edge-other-vert

   全部只读——返回索引 / -1——不修改。

   宏参数约定：
     先 let 绑定 mesh / seg-id——再传宏——
     不把函数调用内联为宏参数。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.seg  :as seg]
    [top.kzre.krro.d3.core.geometry.bmesh.loop :as loop]
    [top.kzre.krro.d3.core.geometry.bmesh.edge :as edge]
    [top.kzre.krro.d3.core.geometry.bmesh.vert :as vert]
    [top.kzre.krro.d3.core.geometry.bmesh.face :as face])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMesh BMeshEditor)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 基础单步——环
;; ═══════════════════════════════════════════════

(defn loop-next ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-ring-readonly mesh seg-id)]
    (long (seg/readi seg loop/next-idx local-idx))))

(defn loop-prev ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-ring-readonly mesh seg-id)]
    (long (seg/readi seg loop/prev-idx local-idx))))

(defn loop-radial-next ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-radial-ring-readonly mesh seg-id)]
    (long (seg/readi seg loop/radial-next-idx local-idx))))

(defn loop-radial-prev ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-radial-ring-readonly mesh seg-id)]
    (long (seg/readi seg loop/radial-prev-idx local-idx))))

(defn loop-vert ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-ownership-readonly mesh seg-id)]
    (long (seg/readi seg loop/vert-idx local-idx))))

(defn loop-edge ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-ownership-readonly mesh seg-id)]
    (long (seg/readi seg loop/edge-idx local-idx))))

(defn loop-face ^long [^BMeshEditor editor ^long l]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.loopSegmentSize editor))
        seg-id      (long (quot l seg-size))
        local-idx   (long (rem l seg-size))
        seg         (seg/loop-ownership-readonly mesh seg-id)]
    (long (seg/readi seg loop/face-idx local-idx))))

;; ═══════════════════════════════════════════════
;; 基础单步——边
;; ═══════════════════════════════════════════════

(defn edge-v0 ^long [^BMeshEditor editor ^long e]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.edgeSegmentSize editor))
        seg-id      (long (quot e seg-size))
        local-idx   (long (rem e seg-size))
        seg         (seg/edge-endpoint-readonly mesh seg-id)]
    (long (seg/readi seg edge/v0-idx local-idx))))

(defn edge-v1 ^long [^BMeshEditor editor ^long e]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.edgeSegmentSize editor))
        seg-id      (long (quot e seg-size))
        local-idx   (long (rem e seg-size))
        seg         (seg/edge-endpoint-readonly mesh seg-id)]
    (long (seg/readi seg edge/v1-idx local-idx))))

(defn edge-loop ^long [^BMeshEditor editor ^long e]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.edgeSegmentSize editor))
        seg-id      (long (quot e seg-size))
        local-idx   (long (rem e seg-size))
        seg         (seg/edge-loop-readonly mesh seg-id)]
    (long (seg/readi seg edge/loop-idx local-idx))))

;; ═══════════════════════════════════════════════
;; 基础单步——顶点
;; ═══════════════════════════════════════════════

(defn vert-out-edge ^long [^BMeshEditor editor ^long v]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.vertSegmentSize editor))
        seg-id      (long (quot v seg-size))
        local-idx   (long (rem v seg-size))
        seg         (seg/vert-out-edge-readonly mesh seg-id)]
    (long (seg/readi seg vert/out-edge-idx local-idx))))

;; ═══════════════════════════════════════════════
;; 基础单步——面
;; ═══════════════════════════════════════════════

(defn face-loop ^long [^BMeshEditor editor ^long f]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.faceSegmentSize editor))
        seg-id      (long (quot f seg-size))
        local-idx   (long (rem f seg-size))
        seg         (seg/face-topology-readonly mesh seg-id)]
    (long (seg/readi seg face/loop-idx local-idx))))

(defn face-len ^long [^BMeshEditor editor ^long f]
  (let [^BMesh mesh (.getBMesh editor)
        seg-size    (long (.faceSegmentSize editor))
        seg-id      (long (quot f seg-size))
        local-idx   (long (rem f seg-size))
        seg         (seg/face-topology-readonly mesh seg-id)]
    (long (seg/readi seg face/loop-len local-idx))))

;; ═══════════════════════════════════════════════
;; 派生单步——环的另一端
;; ═══════════════════════════════════════════════

(defn loop-other-vert ^long [^BMeshEditor editor ^long l]
  ;; l 的起始顶点是 l.vert
  ;; l 的另一端是 l.next.vert
  ;; 因为 edge 连接 l.vert 和 l.next.vert
  (loop-vert editor (loop-next editor l)))

;; ═══════════════════════════════════════════════
;; 派生单步——边的另一端
;; ═══════════════════════════════════════════════

(defn edge-other-vert ^long [^BMeshEditor editor ^long e ^long v]
  (let [v0 (long (edge-v0 editor e))]
    (if (== v0 v)
      (edge-v1 editor e)
      (edge-v0 editor e))))

(set! *unchecked-math* nil)