(ns top.kzre.krro.d3.core.geometry.sbvh
  "SBVH 构建——多图元叶 + 空间分裂。

   阶段 2：跨越分割面的图元复制到两侧——AABB 裁剪。

   节点数组动态增长——SBVH 的 ref 膨胀导致节点数不确定。

   查询统一走 bvh 命名空间。"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (top.kzre.krro.d3.core.geometry.bvh BVH SBVHBuildCtx SBVHMeta SbvhSelect
                                        SbvhSelect$PartitionResult)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 叶节点写入
;; ═══════════════════════════════════════════════

(defn- write-leaf-refs!
  "把 refs[start, end) 写入节点。
   叶 AABB = 所有 ref 的裁剪 AABB 的并集。
   objRefs 追加 prim ids。
   返回 node-idx。

   ref-aabbs 是 ctx 内部 refs 数组（从 0 起），与外部 offset 无关；
   组件访问统一走 bvh 的 AABB getter 宏，不硬编码 stride。"
  ^long [^SBVHBuildCtx ctx ^long node-idx ^long start ^long end]
  (let [^floats data     (.dataArray ctx)
        ^ints   child    (.childArray ctx)
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
        (let [ii (int i)]
          (recur (unchecked-inc i)
                 (min mnx (double (bvh/aabb-min-x ref-aabbs ii)))
                 (min mny (double (bvh/aabb-min-y ref-aabbs ii)))
                 (min mnz (double (bvh/aabb-min-z ref-aabbs ii)))
                 (max mxx (double (bvh/aabb-max-x ref-aabbs ii)))
                 (max mxy (double (bvh/aabb-max-y ref-aabbs ii)))
                 (max mxz (double (bvh/aabb-max-z ref-aabbs ii)))))
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
;; 内部节点写入——递归后重新获取数组
;; ═══════════════════════════════════════════════

(defn- write-internal-after-rec!
  "递归完成后写内部节点——重新获取 data / child——
   因为递归可能触发扩容。"
  ^long [^SBVHBuildCtx ctx ^long idx ^long l ^long r]
  (let [^floats data  (.dataArray ctx)
        ^ints   child (.childArray ctx)]
    (bvh/merge-aabb! data (int idx) (int l) (int r))
    (bvh/set-children-left!  child (int idx) (int l))
    (bvh/set-children-right! child (int idx) (int r))
    idx))

;; ═══════════════════════════════════════════════
;; 递归构建
;; ═══════════════════════════════════════════════

(defn- build-rec
  "refs 全部位于 ctx 内部数组——offset 已在 initRefsFromAabbArray 阶段
   消化为 refs 数组从 0 起的数据区，递归里不再需要 offset。"
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
            (write-internal-after-rec! ctx idx l r))
          ;; 正常——递归左右分区
          (let [l (build-rec ctx (.-leftStart res)  (.-leftEnd res)  leaf-size)
                r (build-rec ctx (.-rightStart res) (.-rightEnd res) leaf-size)]
            (write-internal-after-rec! ctx idx l r)))))))

;; ═══════════════════════════════════════════════
;; 入口
;; ═══════════════════════════════════════════════

(defn build-sbvh
  "从 AABB 数组构建 SBVH——阶段 2（空间分裂）。

   aabbs: float[]——AabbArray 布局——长度 >= offset + n × 6
   n:     图元数（虽然可由数组长度算出，但显式传入避免歧义）

   kwargs:
     :offset     数组数据区起始元素下标——默认 0
     :leaf-size  叶节点最大图元数——默认 8
   返回 BVH 实例——SBVHMeta 存于 ext。"
  ^BVH [^floats aabbs n
        & {:keys [offset leaf-size]
           :or {offset 0
                leaf-size 8}}]
  (let [n         (long n)
        leaf-size (long leaf-size)
        offset    (long offset)]
    (if (zero? n)
      (BVH. (float-array 0) (int-array 0) (SBVHMeta. 0 (int-array 0) 0))
      (let [initial-nodes (* 4 n)
            initial-refs  (* 4 n)
            ctx           (SBVHBuildCtx. initial-nodes initial-refs)]
        ;; 读源 aabbs：把数据区元素偏移传下去（prim id 仍从 0 起）
        (.initRefsFromAabbArray ctx aabbs (int offset) (int n))
        (build-rec ctx 0 n leaf-size)
        (BVH. (.dataArray ctx) (.childArray ctx)
              (SBVHMeta. n (.objRefs ctx) (.objRefCount ctx)))))))

(set! *unchecked-math* nil)