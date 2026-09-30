(ns top.kzre.krro.d3.core.geometry.bmesh.access
  "段访问层——把全局索引映射到 (数组, 段内索引)。

   职责：
     1. 全局索引 → 段号 + 段内索引
     2. 定位段——通过 BMesh 的 COW 结构
     3. 读——快照 / 写——getForWrite
     4. 组合——值类型 + deflayout 宏

   值类型：
     Position / Normal / UV / EdgeEndpoints /
     LoopOwnership / LoopRing / LoopRadialRing / FaceTopology

   命名约定：
     - xxx-array-readonly    读路径——快照
     - xxx-array-for-write!  写路径——触发 COW
     - set-xxx! / get-xxx    组合——值类型 + deflayout 宏"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.vert  :as vert]
    [top.kzre.krro.d3.core.geometry.bmesh.edge  :as edge]
    [top.kzre.krro.d3.core.geometry.bmesh.loop  :as loop]
    [top.kzre.krro.d3.core.geometry.bmesh.face  :as face])
  (:import
    (java.util List)
    (top.kzre.krro.d3.core.geometry Position Normal UV)
    (top.kzre.krro.d3.core.geometry.bmesh
      AllocatorResource BMesh SegmentizedIndexAllocator
      EdgeEndpoints LoopOwnership LoopRing LoopRadialRing FaceTopology)
    (top.kzre.krro.d3.core.util.cow CopyOnWriteFloats CopyOnWriteInts
                                    CopyOnWriteObject ListResource)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 段大小 / 段号 / 段内索引
;; ═══════════════════════════════════════════════

(defn- allocator ^SegmentizedIndexAllocator
  [^CopyOnWriteObject cow]
  (.getAllocator ^AllocatorResource (.getSnapshot cow)))

(defn vert-seg-size ^long [^BMesh bm]
  (.getSegmentSize (allocator (.vertAllocator bm))))

(defn edge-seg-size ^long [^BMesh bm]
  (.getSegmentSize (allocator (.edgeAllocator bm))))

(defn loop-seg-size ^long [^BMesh bm]
  (.getSegmentSize (allocator (.loopAllocator bm))))

(defn face-seg-size ^long [^BMesh bm]
  (.getSegmentSize (allocator (.faceAllocator bm))))

(defn- seg-of ^long [^long idx ^long seg-size]
  (quot idx seg-size))

(defn- local-of ^long [^long idx ^long seg-size]
  (rem idx seg-size))

;; ═══════════════════════════════════════════════
;; 段数组——通用辅助
;; ═══════════════════════════════════════════════

(defn- float-seg-readonly ^floats
  [^CopyOnWriteObject cow ^long seg-id]
  (let [^ListResource lr (.getSnapshot cow)
        ^List segs (.getList lr)
        ^CopyOnWriteFloats seg (.get segs (int seg-id))]
    (.getFloatsSnapshot seg)))

(defn- float-seg-for-write! ^floats
  [^CopyOnWriteObject cow ^long seg-id]
  (let [^ListResource lr (.getForWrite cow)
        ^List segs (.getList lr)
        ^CopyOnWriteFloats seg (.get segs (int seg-id))]
    (.getFloatsForWrite seg)))

(defn- int-seg-readonly ^ints
  [^CopyOnWriteObject cow ^long seg-id]
  (let [^ListResource lr (.getSnapshot cow)
        ^List segs (.getList lr)
        ^CopyOnWriteInts seg (.get segs (int seg-id))]
    (.getIntsSnapshot seg)))

(defn- int-seg-for-write! ^ints
  [^CopyOnWriteObject cow ^long seg-id]
  (let [^ListResource lr (.getForWrite cow)
        ^List segs (.getList lr)
        ^CopyOnWriteInts seg (.get segs (int seg-id))]
    (.getIntsForWrite seg)))

;; ═══════════════════════════════════════════════
;; 顶点——位置（Position）
;; ═══════════════════════════════════════════════

(defn vert-position-array-readonly ^floats [^BMesh bm ^long seg-id]
  (float-seg-readonly (.vertPositions bm) seg-id))

(defn vert-position-array-for-write! ^floats [^BMesh bm ^long seg-id]
  (float-seg-for-write! (.vertPositions bm) seg-id))

(defn set-vert-position!
  "写顶点位置。"
  [^BMesh bm ^long v ^Position p]
  (let [seg-size  (vert-seg-size bm)
        seg-id    (seg-of v seg-size)
        local-idx (local-of v seg-size)
        data      (vert-position-array-for-write! bm seg-id)]
    (vert/set-position! data local-idx (.x p) (.y p) (.z p))))

(defn get-vert-position
  "读顶点位置——返回 Position。"
  ^Position [^BMesh bm ^long v]
  (let [seg-size  (vert-seg-size bm)
        seg-id    (seg-of v seg-size)
        local-idx (local-of v seg-size)
        data      (vert-position-array-readonly bm seg-id)]
    (Position. (vert/position-x data local-idx)
               (vert/position-y data local-idx)
               (vert/position-z data local-idx))))

;; ═══════════════════════════════════════════════
;; 顶点——出边（int）
;; ═══════════════════════════════════════════════

(defn vert-out-edge-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.vertOutEdges bm) seg-id))

(defn vert-out-edge-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.vertOutEdges bm) seg-id))

(defn set-vert-out-edge!
  [^BMesh bm ^long v ^long e]
  (let [seg-size  (vert-seg-size bm)
        seg-id    (seg-of v seg-size)
        local-idx (local-of v seg-size)
        data      (vert-out-edge-array-for-write! bm seg-id)]
    (vert/set-out-edge-idx! data local-idx e)))

(defn get-vert-out-edge ^long [^BMesh bm ^long v]
  (let [seg-size  (vert-seg-size bm)
        seg-id    (seg-of v seg-size)
        local-idx (local-of v seg-size)
        data      (vert-out-edge-array-readonly bm seg-id)]
    (vert/out-edge-idx data local-idx)))

;; ═══════════════════════════════════════════════
;; 边——端点（EdgeEndpoints）
;; ═══════════════════════════════════════════════

(defn edge-endpoints-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.edgeEndpoints bm) seg-id))

(defn edge-endpoints-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.edgeEndpoints bm) seg-id))

(defn set-edge-endpoints!
  "写边端点。"
  [^BMesh bm ^long e ^EdgeEndpoints p]
  (let [seg-size  (edge-seg-size bm)
        seg-id    (seg-of e seg-size)
        local-idx (local-of e seg-size)
        data      (edge-endpoints-array-for-write! bm seg-id)]
    (edge/set-v0-idx! data local-idx (.v0 p))
    (edge/set-v1-idx! data local-idx (.v1 p))))

(defn get-edge-endpoints
  "读边端点——返回 EdgeEndpoints。"
  ^EdgeEndpoints [^BMesh bm ^long e]
  (let [seg-size  (edge-seg-size bm)
        seg-id    (seg-of e seg-size)
        local-idx (local-of e seg-size)
        data      (edge-endpoints-array-readonly bm seg-id)]
    (EdgeEndpoints. (edge/v0-idx data local-idx)
                    (edge/v1-idx data local-idx))))

;; ═══════════════════════════════════════════════
;; 边——径向环入口（int）
;; ═══════════════════════════════════════════════

(defn edge-loops-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.edgeLoops bm) seg-id))

(defn edge-loops-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.edgeLoops bm) seg-id))

(defn set-edge-loop!
  [^BMesh bm ^long e ^long l]
  (let [seg-size  (edge-seg-size bm)
        seg-id    (seg-of e seg-size)
        local-idx (local-of e seg-size)
        data      (edge-loops-array-for-write! bm seg-id)]
    (edge/set-loop-idx! data local-idx l)))

(defn get-edge-loop ^long [^BMesh bm ^long e]
  (let [seg-size  (edge-seg-size bm)
        seg-id    (seg-of e seg-size)
        local-idx (local-of e seg-size)
        data      (edge-loops-array-readonly bm seg-id)]
    (edge/loop-idx data local-idx)))

;; ═══════════════════════════════════════════════
;; 环——UV（UV）
;; ═══════════════════════════════════════════════

(defn loop-uvs-array-readonly ^floats [^BMesh bm ^long seg-id]
  (float-seg-readonly (.loopUvs bm) seg-id))

(defn loop-uvs-array-for-write! ^floats [^BMesh bm ^long seg-id]
  (float-seg-for-write! (.loopUvs bm) seg-id))

(defn set-loop-uv!
  "写环 UV。"
  [^BMesh bm ^long l ^UV uv]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-uvs-array-for-write! bm seg-id)]
    (loop/set-uv-u! data local-idx (.u uv))
    (loop/set-uv-v! data local-idx (.v uv))))

(defn get-loop-uv
  "读环 UV——返回 UV。"
  ^UV [^BMesh bm ^long l]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-uvs-array-readonly bm seg-id)]
    (UV. (loop/uv-u data local-idx)
         (loop/uv-v data local-idx))))

;; ═══════════════════════════════════════════════
;; 环——归属（LoopOwnership）
;; ═══════════════════════════════════════════════

(defn loop-ownership-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.loopOwnership bm) seg-id))

(defn loop-ownership-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.loopOwnership bm) seg-id))

(defn set-loop-ownership!
  "写环归属。"
  [^BMesh bm ^long l ^LoopOwnership o]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-ownership-array-for-write! bm seg-id)]
    (loop/set-vert-idx! data local-idx (.vert o))
    (loop/set-edge-idx! data local-idx (.edge o))
    (loop/set-face-idx! data local-idx (.face o))))

(defn get-loop-ownership
  "读环归属——返回 LoopOwnership。"
  ^LoopOwnership [^BMesh bm ^long l]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-ownership-array-readonly bm seg-id)]
    (LoopOwnership. (loop/vert-idx data local-idx)
                    (loop/edge-idx data local-idx)
                    (loop/face-idx data local-idx))))

;; ═══════════════════════════════════════════════
;; 环——面内链（LoopRing）
;; ═══════════════════════════════════════════════

(defn loop-ring-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.loopRing bm) seg-id))

(defn loop-ring-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.loopRing bm) seg-id))

(defn set-loop-ring!
  "写环面内链。"
  [^BMesh bm ^long l ^LoopRing r]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-ring-array-for-write! bm seg-id)]
    (loop/set-next-idx! data local-idx (.next r))
    (loop/set-prev-idx! data local-idx (.prev r))))

(defn get-loop-ring
  "读环面内链——返回 LoopRing。"
  ^LoopRing [^BMesh bm ^long l]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-ring-array-readonly bm seg-id)]
    (LoopRing. (loop/next-idx data local-idx)
               (loop/prev-idx data local-idx))))

;; ═══════════════════════════════════════════════
;; 环——径向链（LoopRadialRing）
;; ═══════════════════════════════════════════════

(defn loop-radial-ring-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.loopRadialRing bm) seg-id))

(defn loop-radial-ring-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.loopRadialRing bm) seg-id))

(defn set-loop-radial-ring!
  "写环径向链。"
  [^BMesh bm ^long l ^LoopRadialRing r]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-radial-ring-array-for-write! bm seg-id)]
    (loop/set-radial-next! data local-idx (.radialNext r))
    (loop/set-radial-prev! data local-idx (.radialPrev r))))

(defn get-loop-radial-ring
  "读环径向链——返回 LoopRadialRing。"
  ^LoopRadialRing [^BMesh bm ^long l]
  (let [seg-size  (loop-seg-size bm)
        seg-id    (seg-of l seg-size)
        local-idx (local-of l seg-size)
        data      (loop-radial-ring-array-readonly bm seg-id)]
    (LoopRadialRing. (loop/radial-next-idx data local-idx)
                     (loop/radial-prev-idx data local-idx))))

;; ═══════════════════════════════════════════════
;; 面——法线（Normal）
;; ═══════════════════════════════════════════════

(defn face-normals-array-readonly ^floats [^BMesh bm ^long seg-id]
  (float-seg-readonly (.faceNormals bm) seg-id))

(defn face-normals-array-for-write! ^floats [^BMesh bm ^long seg-id]
  (float-seg-for-write! (.faceNormals bm) seg-id))

(defn set-face-normal!
  "写面法线。"
  [^BMesh bm ^long f ^Normal n]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-normals-array-for-write! bm seg-id)]
    (face/set-normal! data local-idx (.x n) (.y n) (.z n))))

(defn get-face-normal
  "读面法线——返回 Normal。"
  ^Normal [^BMesh bm ^long f]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-normals-array-readonly bm seg-id)]
    (Normal. (face/normal-x data local-idx)
             (face/normal-y data local-idx)
             (face/normal-z data local-idx))))

;; ═══════════════════════════════════════════════
;; 面——子网格（int）
;; ═══════════════════════════════════════════════

(defn face-submesh-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.faceSubmeshIds bm) seg-id))

(defn face-submesh-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.faceSubmeshIds bm) seg-id))

(defn set-face-submesh!
  [^BMesh bm ^long f ^long s]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-submesh-array-for-write! bm seg-id)]
    (face/set-submesh-id! data local-idx s)))

(defn get-face-submesh ^long [^BMesh bm ^long f]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-submesh-array-readonly bm seg-id)]
    (face/submesh-id data local-idx)))

;; ═══════════════════════════════════════════════
;; 面——拓扑（FaceTopology）
;; ═══════════════════════════════════════════════

(defn face-topology-array-readonly ^ints [^BMesh bm ^long seg-id]
  (int-seg-readonly (.faceTopology bm) seg-id))

(defn face-topology-array-for-write! ^ints [^BMesh bm ^long seg-id]
  (int-seg-for-write! (.faceTopology bm) seg-id))

(defn set-face-topology!
  "写面拓扑。"
  [^BMesh bm ^long f ^FaceTopology t]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-topology-array-for-write! bm seg-id)]
    (face/set-loop-idx! data local-idx (.loop t))
    (face/set-loop-len! data local-idx (.len t))))

(defn get-face-topology
  "读面拓扑——返回 FaceTopology。"
  ^FaceTopology [^BMesh bm ^long f]
  (let [seg-size  (face-seg-size bm)
        seg-id    (seg-of f seg-size)
        local-idx (local-of f seg-size)
        data      (face-topology-array-readonly bm seg-id)]
    (FaceTopology. (face/loop-idx data local-idx)
                   (face/loop-len data local-idx))))

(set! *unchecked-math* nil)