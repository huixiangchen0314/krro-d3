(ns top.kzre.krro.d3.core.geometry.bmesh.access
  "段访问层——把全局索引映射到 (段, 段内索引)。

   职责：
     1. 全局索引 → 段号 + 段内索引
     2. 段定位——直接用 seg 宏
     3. 组合——值类型 + seg 宏 + deflayout 宏

   本命名空间不含宏——只定义 set / get 函数——
   内部用 seg 宏 + 段大小 + quot / rem 计算。

   值类型：
     Position / Normal / UV / EdgeEndpoints /
     LoopOwnership / LoopRing / LoopRadialRing / FaceTopology"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.seg   :as seg]
    [top.kzre.krro.d3.core.geometry.bmesh.vert  :as vert]
    [top.kzre.krro.d3.core.geometry.bmesh.edge  :as edge]
    [top.kzre.krro.d3.core.geometry.bmesh.loop  :as loop]
    [top.kzre.krro.d3.core.geometry.bmesh.face  :as face])
  (:import
    (top.kzre.krro.d3.core.geometry Position Normal UV)
    (top.kzre.krro.d3.core.geometry.bmesh
      BMesh BMeshEditor
      EdgeEndpoints LoopOwnership LoopRing LoopRadialRing FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 顶点——位置
;; ═══════════════════════════════════════════════

(defn set-vert-position!
  [^BMeshEditor editor ^long v ^Position p]
  (let [seg-size  (long (.vertSegmentSize editor))
        seg-id    (long (quot v seg-size))
        local-idx (long (rem v seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/vert-position-for-write mesh seg-id)]
    (seg/writef seg vert/set-position-x! local-idx (.x p))
    (seg/writef seg vert/set-position-y! local-idx (.y p))
    (seg/writef seg vert/set-position-z! local-idx (.z p))))

(defn get-vert-position
  ^Position [^BMeshEditor editor ^long v]
  (let [seg-size  (long (.vertSegmentSize editor))
        seg-id    (long (quot v seg-size))
        local-idx (long (rem v seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/vert-position-readonly mesh seg-id)]
    (Position. (seg/readf seg vert/position-x local-idx)
               (seg/readf seg vert/position-y local-idx)
               (seg/readf seg vert/position-z local-idx))))

;; ═══════════════════════════════════════════════
;; 顶点——出边
;; ═══════════════════════════════════════════════

(defn set-vert-out-edge!
  [^BMeshEditor editor ^long v ^long e]
  (let [seg-size  (long (.vertSegmentSize editor))
        seg-id    (long (quot v seg-size))
        local-idx (long (rem v seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/vert-out-edge-for-write mesh seg-id)]
    (seg/writei seg vert/set-out-edge-idx! local-idx e)))

(defn get-vert-out-edge ^long [^BMeshEditor editor ^long v]
  (let [seg-size  (long (.vertSegmentSize editor))
        seg-id    (long (quot v seg-size))
        local-idx (long (rem v seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/vert-out-edge-readonly mesh seg-id)]
    (long (seg/readi seg vert/out-edge-idx local-idx))))

;; ═══════════════════════════════════════════════
;; 边——端点
;; ═══════════════════════════════════════════════

(defn set-edge-endpoints!
  [^BMeshEditor editor ^long e ^EdgeEndpoints p]
  (let [seg-size  (long (.edgeSegmentSize editor))
        seg-id    (long (quot e seg-size))
        local-idx (long (rem e seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/edge-endpoint-for-write mesh seg-id)]
    (seg/writei seg edge/set-v0-idx! local-idx (.v0 p))
    (seg/writei seg edge/set-v1-idx! local-idx (.v1 p))))

(defn get-edge-endpoints
  ^EdgeEndpoints [^BMeshEditor editor ^long e]
  (let [seg-size  (long (.edgeSegmentSize editor))
        seg-id    (long (quot e seg-size))
        local-idx (long (rem e seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/edge-endpoint-readonly mesh seg-id)]
    (EdgeEndpoints. (int (seg/readi seg edge/v0-idx local-idx))
                    (int (seg/readi seg edge/v1-idx local-idx)))))

;; ═══════════════════════════════════════════════
;; 边——径向环入口
;; ═══════════════════════════════════════════════

(defn set-edge-loop!
  [^BMeshEditor editor ^long e ^long l]
  (let [seg-size  (long (.edgeSegmentSize editor))
        seg-id    (long (quot e seg-size))
        local-idx (long (rem e seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/edge-loop-for-write mesh seg-id)]
    (seg/writei seg edge/set-loop-idx! local-idx l)))

(defn get-edge-loop ^long [^BMeshEditor editor ^long e]
  (let [seg-size  (long (.edgeSegmentSize editor))
        seg-id    (long (quot e seg-size))
        local-idx (long (rem e seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/edge-loop-readonly mesh seg-id)]
    (long (seg/readi seg edge/loop-idx local-idx))))

;; ═══════════════════════════════════════════════
;; 环——UV
;; ═══════════════════════════════════════════════

(defn set-loop-uv!
  [^BMeshEditor editor ^long l ^UV uv]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-uv-for-write mesh seg-id)]
    (seg/writef seg loop/set-uv-u! local-idx (.u uv))
    (seg/writef seg loop/set-uv-v! local-idx (.v uv))))

(defn get-loop-uv
  ^UV [^BMeshEditor editor ^long l]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-uv-readonly mesh seg-id)]
    (UV. (seg/readf seg loop/uv-u local-idx)
         (seg/readf seg loop/uv-v local-idx))))

;; ═══════════════════════════════════════════════
;; 环——归属
;; ═══════════════════════════════════════════════

(defn set-loop-ownership!
  [^BMeshEditor editor ^long l ^LoopOwnership o]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-ownership-for-write mesh seg-id)]
    (seg/writei seg loop/set-vert-idx! local-idx (.vert o))
    (seg/writei seg loop/set-edge-idx! local-idx (.edge o))
    (seg/writei seg loop/set-face-idx! local-idx (.face o))))

(defn get-loop-ownership
  ^LoopOwnership [^BMeshEditor editor ^long l]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-ownership-readonly mesh seg-id)]
    (LoopOwnership. (int (seg/readi seg loop/vert-idx local-idx))
                    (int (seg/readi seg loop/edge-idx local-idx))
                    (int (seg/readi seg loop/face-idx local-idx)))))

;; ═══════════════════════════════════════════════
;; 环——面内链
;; ═══════════════════════════════════════════════

(defn set-loop-ring!
  [^BMeshEditor editor ^long l ^LoopRing r]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-ring-for-write mesh seg-id)]
    (seg/writei seg loop/set-next-idx! local-idx (.next r))
    (seg/writei seg loop/set-prev-idx! local-idx (.prev r))))

(defn get-loop-ring
  ^LoopRing [^BMeshEditor editor ^long l]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-ring-readonly mesh seg-id)]
    (LoopRing. (int (seg/readi seg loop/next-idx local-idx))
               (int (seg/readi seg loop/prev-idx local-idx)))))

;; ═══════════════════════════════════════════════
;; 环——径向链
;; ═══════════════════════════════════════════════

(defn set-loop-radial-ring!
  [^BMeshEditor editor ^long l ^LoopRadialRing r]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-radial-ring-for-write mesh seg-id)]
    (seg/writei seg loop/set-radial-next! local-idx (.radialNext r))
    (seg/writei seg loop/set-radial-prev! local-idx (.radialPrev r))))

(defn get-loop-radial-ring
  ^LoopRadialRing [^BMeshEditor editor ^long l]
  (let [seg-size  (long (.loopSegmentSize editor))
        seg-id    (long (quot l seg-size))
        local-idx (long (rem l seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/loop-radial-ring-readonly mesh seg-id)]
    (LoopRadialRing. (int (seg/readi seg loop/radial-next-idx local-idx))
                     (int (seg/readi seg loop/radial-prev-idx local-idx)))))

;; ═══════════════════════════════════════════════
;; 面——法线
;; ═══════════════════════════════════════════════

(defn set-face-normal!
  [^BMeshEditor editor ^long f ^Normal n]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-normal-for-write mesh seg-id)]
    (seg/writef seg face/set-normal-x! local-idx (.x n))
    (seg/writef seg face/set-normal-y! local-idx (.y n))
    (seg/writef seg face/set-normal-z! local-idx (.z n))))

(defn get-face-normal
  ^Normal [^BMeshEditor editor ^long f]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-normal-readonly mesh seg-id)]
    (Normal. (seg/readf seg face/normal-x local-idx)
             (seg/readf seg face/normal-y local-idx)
             (seg/readf seg face/normal-z local-idx))))

;; ═══════════════════════════════════════════════
;; 面——子网格
;; ═══════════════════════════════════════════════

(defn set-face-submesh!
  [^BMeshEditor editor ^long f ^long s]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-submesh-for-write mesh seg-id)]
    (seg/writei seg face/set-submesh-id! local-idx s)))

(defn get-face-submesh ^long [^BMeshEditor editor ^long f]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-submesh-readonly mesh seg-id)]
    (long (seg/readi seg face/submesh-id local-idx))))

;; ═══════════════════════════════════════════════
;; 面——拓扑
;; ═══════════════════════════════════════════════

(defn set-face-topology!
  [^BMeshEditor editor ^long f ^FaceTopology t]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-topology-for-write mesh seg-id)]
    (seg/writei seg face/set-loop-idx! local-idx (.loop t))
    (seg/writei seg face/set-loop-len! local-idx (.len t))))

(defn get-face-topology
  ^FaceTopology [^BMeshEditor editor ^long f]
  (let [seg-size  (long (.faceSegmentSize editor))
        seg-id    (long (quot f seg-size))
        local-idx (long (rem f seg-size))
        mesh      (.getBMesh editor)
        seg       (seg/face-topology-readonly mesh seg-id)]
    (FaceTopology. (int (seg/readi seg face/loop-idx local-idx))
                   (int (seg/readi seg face/loop-len local-idx)))))

(set! *unchecked-math* nil)