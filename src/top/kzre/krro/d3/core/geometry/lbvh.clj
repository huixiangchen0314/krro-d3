(ns top.kzre.krro.d3.core.geometry.lbvh
  "线性编码 BVH——从 Morton 序构建。

   构建速度 O(n log n)。
   输入已按 Morton 码升序排序——构建器不排序。

   通用构建器——不携带领域概念：
   - aabbs   float[]——AabbArray 布局
   - mortons long[]——Morton 码升序
   - n       long——图元数"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH)
    (top.kzre.krro.d3.core.geometry.lbvh LBVHBuildCtx)))

(set! *unchecked-math* :warn-on-boxed)

;; ─── 节点数组分配 ─────────────────────────────

(defn- node-count ^long [^long n]
  (+ n (max 0 (dec n))))

(defn- allocate-aabb-array ^floats [^long n]
  (float-array (* (node-count n) 6)))

(defn- allocate-child-array ^ints [^long n]
  (int-array (* (node-count n) 2)))

;; ─── 构建核心——3 参数 ────────────────────────
;; ─── 辅助函数——顶层 defn- —— 无闭包 ──────────

(defn- range-min-morton
  "在 mortons[start, end) 找最小值。"
  ^long [^longs mortons ^long start ^long end]
  (loop [i  start
         mn (long Long/MAX_VALUE)]
    (if (< i end)
      (let [m (aget mortons i)]
        (recur (unchecked-inc i) (if (< m mn) m mn)))
      mn)))

(defn- range-max-morton
  "在 mortons[start, end) 找最大值。"
  ^long [^longs mortons ^long start ^long end]
  (loop [i  start
         mx (long Long/MIN_VALUE)]
    (if (< i end)
      (let [m (aget mortons i)]
        (recur (unchecked-inc i) (if (> m mx) m mx)))
      mx)))

(defn- find-split
  "按 diff-bit 找第一个不同的位置——Morton 分割点。"
  ^long [^longs mortons ^long start ^long end ^long diff-bit]
  (loop [i start]
    (if (>= i end)
      end
      (if (== 0 (bit-and (aget mortons i) diff-bit))
        (recur (unchecked-inc i))
        i))))

(defn- build-rec
  ^long [^LBVHBuildCtx ctx ^long start ^long end]
  (let [^floats data    (.data    ctx)
        ^ints   child   (.child   ctx)
        ^floats aabbs   (.aabbs   ctx)
        ^longs  mortons (.mortons ctx)
        idx (.allocIdx ctx)
        n   (- end start)]
    (if (== n 1)
      (do (bvh/write-aabb-from! data idx aabbs start)
          (bvh/set-children-left!  child (int idx) -1)
          (bvh/set-children-right! child (int idx) (int start))
          idx)
      (let [min-code (range-min-morton mortons start end)
            max-code (range-max-morton mortons start end)
            diff-bit (Long/highestOneBit (bit-xor min-code max-code))
            split-0 (long (if (== 0 diff-bit)
                            (unchecked-add start (quot n 2))
                            (find-split mortons start end diff-bit)))
            split (long (if (or (== split-0 start) (== split-0 end))
                          (unchecked-add start (quot n 2))
                          split-0))
            left-idx  (build-rec ctx start split)
            right-idx (build-rec ctx split end)]
        (bvh/merge-aabb! data (int idx) (int left-idx) (int right-idx))
        (bvh/set-children-left!  child (int idx) (int left-idx))
        (bvh/set-children-right! child (int idx) (int right-idx))
        idx))))

;; ─── 公共构建函数 ─────────────────────────────

(defn build-lbvh
  "从已按 Morton 升序排序的 AABB 数组构建 LBVH。

   aabbs:   float[]——AabbArray 布局——长度 = n × 6
   mortons: long[]——每个图元的 Morton 码——升序
   n:       long——图元数

   返回 BVH 实例——primitive-count 存于 ext。"
  ^BVH [^floats aabbs ^longs mortons ^long n]
  (if (zero? n)
    (bvh/make-bvh (float-array 0) (int-array 0) 0)
    (let [data (allocate-aabb-array n)
          child (allocate-child-array n)
          ctx  (LBVHBuildCtx. data child aabbs mortons)]
      (build-rec ctx 0 n)
      (bvh/make-bvh data child n))))

(set! *unchecked-math* nil)