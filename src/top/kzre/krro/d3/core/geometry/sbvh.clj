(ns top.kzre.krro.d3.core.geometry.sbvh
  "SBVH 构建——多图元叶 + 空间分裂。

   阶段 2：跨越分割面的图元复制到两侧——AABB 裁剪。

   查询统一走 bvh 命名空间。"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH SBVHBuildCtx SBVHMeta SbvhSelect SbvhSelect$PartitionResult)))

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
;; 叶节点写入
;; ═══════════════════════════════════════════════

(defn- write-leaf-refs!
  "把 refs[start, end) 写入节点。
   叶 AABB = 所有 ref 的裁剪 AABB 的并集。
   objRefs 追加 prim ids。
   返回 node-idx。"
  ^long [^SBVHBuildCtx ctx ^long node-idx ^long start ^long end]
  (let [^floats data     (.data ctx)
        ^ints   child    (.child ctx)
        ^floats ref-aabbs (.refAabbs ctx)
        n (- end start)
        obj-start (.appendObjRefsFromRefs ctx (int start) (int end))]
    ;; 合并叶 AABB
    (loop [i   start
           mnx Double/POSITIVE_INFINITY
           mny Double/POSITIVE_INFINITY
           mnz Double/POSITIVE_INFINITY
           mxx Double/NEGATIVE_INFINITY
           mxy Double/NEGATIVE_INFINITY
           mxz Double/NEGATIVE_INFINITY]
      (if (< i end)
        (let [base (* i 6)]
          (recur (unchecked-inc i)
                 (min mnx (double (aget ref-aabbs base)))
                 (min mny (double (aget ref-aabbs (+ base 1))))
                 (min mnz (double (aget ref-aabbs (+ base 2))))
                 (max mxx (double (aget ref-aabbs (+ base 3))))
                 (max mxy (double (aget ref-aabbs (+ base 4))))
                 (max mxz (double (aget ref-aabbs (+ base 5))))))
        (do
          (bvh/set-aabb-min-x! data (int node-idx) (float mnx))
          (bvh/set-aabb-min-y! data (int node-idx) (float mny))
          (bvh/set-aabb-min-z! data (int node-idx) (float mnz))
          (bvh/set-aabb-max-x! data (int node-idx) (float mxx))
          (bvh/set-aabb-max-y! data (int node-idx) (float mxy))
          (bvh/set-aabb-max-z! data (int node-idx) (float mxz)))))
    ;; 叶标记——负 count + obj-start
    (bvh/set-children-left!  child (int node-idx) (- (int n)))
    (bvh/set-children-right! child (int node-idx) (int obj-start))
    node-idx))

;; ═══════════════════════════════════════════════
;; 递归构建
;; ═══════════════════════════════════════════════

(defn- build-rec
  ^long [^SBVHBuildCtx ctx ^long start ^long end ^long leaf-size]
  (let [idx (.allocIdx ctx)
        n   (- end start)]
    (if (<= n leaf-size)
      (write-leaf-refs! ctx idx start end)
      (let [^SbvhSelect$PartitionResult res
            (SbvhSelect/partition ctx (int start) (int end))]
        (if (nil? res)
          ;; 退化——按引用顺序中位数切
          (let [mid (+ start (quot n 2))
                l   (build-rec ctx start mid leaf-size)
                r   (build-rec ctx mid end leaf-size)]
            (bvh/merge-aabb! (.data ctx) (int idx) (int l) (int r))
            (bvh/set-children-left!  (.child ctx) (int idx) (int l))
            (bvh/set-children-right! (.child ctx) (int idx) (int r))
            idx)
          ;; 正常——递归左右分区
          (let [l (build-rec ctx (.-leftStart res)  (.-leftEnd res)  leaf-size)
                r (build-rec ctx (.-rightStart res) (.-rightEnd res) leaf-size)]
            (bvh/merge-aabb! (.data ctx) (int idx) (int l) (int r))
            (bvh/set-children-left!  (.child ctx) (int idx) (int l))
            (bvh/set-children-right! (.child ctx) (int idx) (int r))
            idx))))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-sbvh
  "从 AABB 数组构建 SBVH——阶段 2（空间分裂）。

   aabbs: float[]——AabbArray 布局——长度 = n × 6
   n:     图元数

   kwargs:
     :leaf-size  叶节点最大图元数——默认 8

   返回 BVH 实例——SBVHMeta 存于 ext。"
  ^BVH [^floats aabbs n & {:keys [leaf-size] :or {leaf-size 8}}]
  (let [n         (long n)
        leaf-size (long leaf-size)]
    (if (zero? n)
      (BVH. (float-array 0) (int-array 0) (SBVHMeta. 0 (int-array 0) 0))
      (let [data    (allocate-aabb-array n)
            child   (allocate-child-array n)
            initial (* 4 n)
            ctx     (SBVHBuildCtx. data child initial)]
        ;; 初始化 refs——从 AabbArray 布局
        (.initRefsFromAabbArray ctx aabbs 0 (int n))

        ;; 递归构建
        (build-rec ctx 0 n leaf-size)

        ;; 输出 BVH + SBVHMeta
        (BVH. data child
              (SBVHMeta. n (.objRefs ctx) (.objRefCount ctx)))))))

(set! *unchecked-math* nil)