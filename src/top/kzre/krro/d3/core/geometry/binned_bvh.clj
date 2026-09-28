(ns top.kzre.krro.d3.core.geometry.binned-bvh
  "Binned SAH BVH 构建。

   输入：AabbArray 布局的 float[] + 图元数
   输出：BVH 实例

   算法：
   - 最长轴 + 12 bin
   - 每 bin 累计 AABB + 数量
   - 遍历 11 个分割位置——SAH 成本最小
   - 递归构建——单图元叶
   - 前序分配节点索引——root = 0

   通用构建器——不携带领域概念。"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH BinnedBvhBuildCtx BinnedSelect)))

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
  ^long [^BinnedBvhBuildCtx ctx ^long start ^long end]
  (let [^floats data  (.data  ctx)
        ^ints   child (.child ctx)
        ^floats aabbs (.aabbs ctx)
        ^longs  prims (.prims ctx)
        idx (.allocIdx ctx)
        n   (- end start)]
    (if (== n 1)
      ;; 叶节点——单图元
      (do (bvh/write-aabb-from! data idx aabbs (aget prims start))
          (bvh/set-children-left!  child (int idx) -1)
          (bvh/set-children-right! child (int idx) (int (aget prims start)))
          idx)
      ;; 内部节点
      (let [ax       (BinnedSelect/longestAxis aabbs prims (int start) (int end))
            raw      (BinnedSelect/binnedPartition
                       prims (int start) (int end) aabbs ax)
            split    (long (if (or (neg? raw) (== raw start) (== raw end))
                             (unchecked-add start (quot n 2))
                             raw))
            left-idx  (build-rec ctx start split)
            right-idx (build-rec ctx split end)]
        (bvh/merge-aabb! data (int idx) (int left-idx) (int right-idx))
        (bvh/set-children-left!  child (int idx) (int left-idx))
        (bvh/set-children-right! child (int idx) (int right-idx))
        idx))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-binned-bvh
  "从 AABB 数组构建 Binned SAH BVH。

   aabbs: float[]——AabbArray 布局——长度 = n × 6
   n:     long——图元数

   返回 BVH 实例——primitive-count 存于 ext。"
  ^BVH [^floats aabbs ^long n]
  (if (zero? n)
    (bvh/make-bvh (float-array 0) (int-array 0) 0)
    (let [data  (allocate-aabb-array n)
          child (allocate-child-array n)
          prims (long-array n)]
      (dotimes [i n] (aset prims i (long i)))
      (let [ctx (BinnedBvhBuildCtx. data child aabbs prims)]
        (build-rec ctx 0 n))
      (bvh/make-bvh data child n))))

(set! *unchecked-math* nil)