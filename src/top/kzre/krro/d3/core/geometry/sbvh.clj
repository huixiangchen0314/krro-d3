(ns top.kzre.krro.d3.core.geometry.sbvh
  "SBVH 构建——多图元叶 + Binned SAH。

   与 Binned BVH 共用 BVH 布局 + 查询——
   差异仅在叶节点语义：
   - 叶节点包含多个图元（数量 ≤ leaf-size）
   - objRefs 存储叶节点引用的图元 id

   叶语义（统一）：
     left == -1   →  单图元叶——right = 图元 id
     left < -1    →  多图元叶——right = objRefs 起始——-left = count
     left >= 0    →  内部节点

   查询统一走 bvh 命名空间：
     (bvh/cross-query  sbvh ray)
     (bvh/sphere-query sbvh sphere)

   阶段 1：无空间分裂——每个图元只出现一次。"
  (:require
    [top.kzre.krro.d3.core.geometry.aabb :as aabb]
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH SBVHBuildCtx SBVHMeta BinnedSelect)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 节点数组分配
;; ═══════════════════════════════════════════════

(defn- node-count ^long [^long n]
  (if (<= n 1) 1 (- (* 2 n) 1)))

(defn- allocate-aabb-array ^floats [^long n]
  (float-array (* (node-count n) 6)))

(defn- allocate-child-array ^ints [^long n]
  (int-array (* (node-count n) 2)))

;; ═══════════════════════════════════════════════
;; 递归构建
;; ═══════════════════════════════════════════════

(defn- build-rec
  ^long [^SBVHBuildCtx ctx ^long start ^long end ^long leaf-size]
  (let [^floats data  (.data  ctx)
        ^ints   child (.child ctx)
        ^floats aabbs (.aabbs ctx)
        ^longs  prims (.prims ctx)
        idx (.allocIdx ctx)
        n   (- end start)]
    (if (<= n leaf-size)
      ;; 叶节点——多图元
      (let [ref-start (.allocRefsFromPrims ctx (int start) (int end))
            range     (aabb/range-aabb-range aabbs prims start end)]
        (bvh/write-aabb! data (int idx) range)
        (bvh/set-children-left!  child (int idx) (- (int n)))   ; ← 负 count
        (bvh/set-children-right! child (int idx) ref-start)
        idx)
      ;; 内部节点
      (let [ax       (BinnedSelect/longestAxis aabbs prims (int start) (int end))
            raw      (BinnedSelect/binnedPartition
                       prims (int start) (int end) aabbs ax)
            split    (long (if (or (neg? raw) (== raw start) (== raw end))
                             (unchecked-add start (quot n 2))
                             raw))
            left-idx  (build-rec ctx start split leaf-size)
            right-idx (build-rec ctx split end leaf-size)]
        (bvh/merge-aabb! data (int idx) (int left-idx) (int right-idx))
        (bvh/set-children-left!  child (int idx) (int left-idx))
        (bvh/set-children-right! child (int idx) (int right-idx))
        idx))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-sbvh
  "从 AABB 数组构建 SBVH。

   阶段 1：无空间分裂——每个图元只出现一次——
   objRefs 长度 = primitiveCount。

   aabbs: float[]——AabbArray 布局——长度 = n × 6
   n:     long——图元数

   kwargs:
     :leaf-size  叶节点最大图元数——默认 8

   返回 BVH 实例——SBVHMeta 存于 ext。
   查询走 bvh/cross-query / bvh/sphere-query。"
  ^BVH [^floats aabbs ^long n & {:keys [leaf-size] :or {leaf-size 8}}]
  (if (zero? n)
    (BVH. (float-array 0) (int-array 0) (SBVHMeta. 0 (int-array 0) 0))
    (let [data  (allocate-aabb-array n)
          child (allocate-child-array n)
          prims (long-array n)]
      (dotimes [i n] (aset prims i (long i)))
      (let [ctx (SBVHBuildCtx. data child aabbs prims)]
        (build-rec ctx 0 n leaf-size)
        (BVH. data child
              (SBVHMeta. n (.objRefs ctx) (.refCount ctx)))))))

(set! *unchecked-math* nil)