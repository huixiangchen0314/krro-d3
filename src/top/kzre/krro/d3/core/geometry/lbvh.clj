(ns top.kzre.krro.d3.core.geometry.lbvh
  "线性编码 BVH——从 Morton 序构建。

   构建速度 O(n log n)。
   输入已按 Morton 码升序排序——构建器不排序。

   通用构建器——不携带领域概念：
   - aabbs   float[]——AabbArray 布局
   - mortons long[]——Morton 码升序
   - n       long——图元数

   offset 语义：aabbs 数据区起始元素下标（aabbs[offset .. offset+n*6)），
   mortons 与 prim id 均为 0-based，与 offset 无关。"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH LBVHBuildCtx)))

(set! *unchecked-math* :warn-on-boxed)

;; ─── 节点数组分配 ─────────────────────────────

(defn- node-count ^long [^long n]
  (+ n (max 0 (dec n))))

(defn- allocate-aabb-array ^floats [^long n]
  (float-array (* (node-count n) 6)))

(defn- allocate-child-array ^ints [^long n]
  (int-array (* (node-count n) 2)))

;; ─── 构建核心——4 参数（多了 offset）──────────
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
  "递归构建。start / end 为 [0, n) 内的 0-based 索引，既是 mortons 索引
   也是 prim id；offset 仅在读取源 aabbs 时使用。"
  ^long [^LBVHBuildCtx ctx ^long start ^long end ^long offset]
  (let [^floats data    (.data    ctx)
        ^ints   child   (.child   ctx)
        ^floats aabbs   (.aabbs   ctx)
        ^longs  mortons (.mortons ctx)
        idx (.allocIdx ctx)
        n   (- end start)]
    (if (== n 1)
      (do (bvh/write-aabb-from! data idx aabbs start offset)   ;; ← 读源 aabbs 带 offset
          (bvh/set-children-left!  child (int idx) -1)
          (bvh/set-children-right! child (int idx) (int start)) ;; ← prim id 0-based
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
            left-idx  (build-rec ctx start split offset)
            right-idx (build-rec ctx split end offset)]
        (bvh/merge-aabb! data (int idx) (int left-idx) (int right-idx))
        (bvh/set-children-left!  child (int idx) (int left-idx))
        (bvh/set-children-right! child (int idx) (int right-idx))
        idx))))

;; ─── 公共构建函数 ─────────────────────────────

(defn build-lbvh
  "从已按 Morton 升序排序的 AABB 数组构建 LBVH。

   aabbs:   float[]——AabbArray 布局——长度 >= offset + n × 6
   mortons: long[]——每个图元的 Morton 码——升序——长度 >= n（无 offset）
   n:       long——图元数

   kwargs:
     :offset  aabbs 数据区起始元素下标——默认 0。
              只影响读取源 aabbs；mortons 与 prim id 始终 0-based。

   返回 BVH 实例——primitive-count 存于 ext。"
  ^BVH [^floats aabbs ^longs mortons n
        & {:keys [offset]
           :or {offset 0}}]
  (let [n      (long n)
        offset (long offset)]
    (if (zero? n)
      (bvh/make-bvh (float-array 0) (int-array 0) 0)
      (let [data  (allocate-aabb-array n)
            child (allocate-child-array n)
            ctx   (LBVHBuildCtx. data child aabbs mortons)]
        ;; 递归区间始终是 [0, n)，offset 只传给读源数组的地方
        (build-rec ctx 0 n offset)
        (bvh/make-bvh data child n)))))

(set! *unchecked-math* nil)