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

   offset 语义：aabbs 数据区起始元素下标（aabbs[offset .. offset+n*6)），
   prim id 与内部节点索引均为 0-based，与 offset 无关。

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
  "start / end 是 [0, n) 内的 0-based 索引，既是 prims 索引也是 prim id；
   offset 仅在读取源 aabbs 时使用。"
  ^long [^KDBuildCtx ctx ^long start ^long end ^long offset]
  (let [^floats aabbs (.aabbs ctx)
        ^longs  prims (.prims ctx)
        ^floats split (.split ctx)
        ^bytes  axis  (.axis  ctx)
        ^ints   child (.child ctx)
        idx     (.allocIdx ctx)
        n       (- end start)]
    (if (== n 1)
      (do (kd/write-leaf! child (int idx) (int (aget prims start)))   ;; ← prim id 0-based
          idx)
      (let [^AABB range (aabb/range-aabb-range aabbs prims (long start) (long end) offset)
            ax         (aabb/longest-axis range)
            ax-i       (int ax)
            mid        (KDSelect/medianPartitionAabb
                         prims (int start) (int end) aabbs ax-i (int offset))
            split-pos  (float (aabb/center-on-axis
                                aabbs (aget prims mid) ax offset))
            left-idx   (build-rec ctx start mid offset)
            right-idx  (build-rec ctx mid end offset)]
        (kd/write-internal! split axis child
                            (int idx) (byte ax-i) split-pos
                            (int left-idx) (int right-idx))
        idx))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-kd
  "从 AABB 数组构建 KD。

   aabbs: float[]——AabbArray 布局——长度 >= offset + n × 6
   n:     long——图元数

   kwargs:
     :offset  aabbs 数据区起始元素下标——默认 0。
              只影响读取源 aabbs；prim id 与内部节点索引始终 0-based。

   返回 KD 实例——primitive-count 存于 ext。"
  ^KD [^floats aabbs n
       & {:keys [offset]
          :or {offset 0}}]
  (let [n      (long n)
        offset (long offset)]
    (if (zero? n)
      (kd/make-kd (float-array 0) (byte-array 0) (int-array 0))
      (let [split (allocate-split n)
            axis  (allocate-axis n)
            child (allocate-child n)
            prims (long-array n)]
        (dotimes [i n] (aset prims i (long i)))
        (let [ctx (KDBuildCtx. aabbs prims split axis child)]
          ;; ← 修复：递归区间恒为 [0, n)，offset 只传给读源 aabbs 的地方
          (build-rec ctx 0 n offset))
        (kd/make-kd split axis child (long n))))))

(set! *unchecked-math* nil)