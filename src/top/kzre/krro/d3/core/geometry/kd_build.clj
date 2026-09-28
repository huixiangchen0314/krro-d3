(ns top.kzre.krro.d3.core.geometry.kd-build
  "从 AABB 数组构建 KD 树。

   通用构建器——不携带领域概念：
   - 输入：float[] aabbs（AabbArray 布局）+ 图元数
   - 输出：KD 实例

   算法：
   - 最长轴 + 中位数分割（quickselect）
   - 单图元叶
   - 前序分配节点索引——root = 0
   - 分割位置 = 右子集最小中心——左右不重叠

   调用方按需选择——对象级 KD / 块内 KD 共用此函数。"
  (:require
    [top.kzre.krro.d3.core.geometry.aabb :as aabb]
    [top.kzre.krro.d3.core.geometry.kd :as kd])
  (:import
    (top.kzre.krro.d3.core.geometry AABB)
    (top.kzre.krro.d3.core.geometry.kd KD KDSelect KDBuildCtx)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 节点数组分配
;; ═══════════════════════════════════════════════

(defn- node-count ^long [^long n]
  (if (<= n 1)
    1
    (- (* 2 n) 1)))

(defn- allocate-split ^floats [^long n]
  (float-array (node-count n)))

(defn- allocate-axis ^bytes [^long n]
  (byte-array (node-count n)))

(defn- allocate-child ^ints [^long n]
  (int-array (* (node-count n) 2)))

;; ═══════════════════════════════════════════════
;; 递归构建
;; ═══════════════════════════════════════════════

(defn- build-rec
  [^KDBuildCtx ctx ^long start ^long end]
  (let [^floats aabbs (.aabbs ctx)
        ^longs  prims (.prims ctx)
        ^floats split (.split ctx)
        ^bytes  axis  (.axis  ctx)
        ^ints   child (.child ctx)
        idx     (.allocIdx ctx)
        n       (- end start)]
    (if (== n 1)
      ;; 叶节点
      (do (kd/write-leaf! child (int idx) (int (aget prims start)))
          idx)
      ;; 内部节点
      (let [^AABB range (aabb/range-aabb-range aabbs prims start end)
            ax         (aabb/longest-axis range)
            ax-i       (int ax)
            mid        (KDSelect/medianPartitionAabb
                         prims (int start) (int end) aabbs ax-i)
            split-pos  (float (aabb/center-on-axis
                                aabbs (aget prims mid) ax))
            left-idx   (build-rec ctx start mid)
            right-idx  (build-rec ctx mid end)]
        (kd/write-internal! split axis child
                            (int idx) ax-i split-pos
                            (int left-idx) (int right-idx))
        idx))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-kd
  "从 AABB 数组构建 KD。

   aabbs: float[]——AabbArray 布局——长度 = n × 6
   n:     long——图元数

   返回 KD 实例——primitive-count 存于 ext。"
  ^KD [^floats aabbs ^long n]
  (if (zero? n)
    (kd/make-kd (float-array 0) (byte-array 0) (int-array 0))
    (let [split (allocate-split n)
          axis  (allocate-axis n)
          child (allocate-child n)
          prims (long-array n)]
      (dotimes [i n] (aset prims i (long i)))
      (let [ctx (KDBuildCtx. aabbs prims split axis child)]
        (build-rec ctx 0 n))
      (kd/make-kd split axis child (long n)))))

(set! *unchecked-math* nil)