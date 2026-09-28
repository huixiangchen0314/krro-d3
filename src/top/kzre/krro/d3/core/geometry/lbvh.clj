(ns top.kzre.krro.d3.core.geometry.lbvh
  "线性编码 BVH，构建速度 O(n log n)。"
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh]
    [top.kzre.krro.d3.core.geometry.mesh-trunk :as mt])
  (:import
    (java.util.concurrent.atomic AtomicInteger)
    (top.kzre.krro.d3.core.geometry AABB IMeshTrunk)
    (top.kzre.krro.d3.core.geometry.bvh BVH)))

(set! *unchecked-math* :warn-on-boxed)

;; ─── 节点数组分配 ─────────────────────────────

(defn- node-count ^long [^long n]
  (+ n (max 0 (dec n))))

(defn- allocate-aabb-array ^floats [^long n]
  (float-array (* (node-count n) 6)))   ; 6 float / 节点

(defn- allocate-child-array ^ints [^long n]
  (int-array (* (node-count n) 2)))     ; 2 int / 节点

;; ─── 叶 / 内部节点写入 ────────────────────────

(defn- set-leaf! [^ints child node-idx chunk-idx]
  (bvh/set-children-left!  child node-idx -1)
  (bvh/set-children-right! child node-idx chunk-idx))

(defn- set-internal! [^ints child node-idx left-idx right-idx]
  (bvh/set-children-left!  child node-idx left-idx)
  (bvh/set-children-right! child node-idx right-idx))

;; ─── 构建核心 —— 前序分配 ─────────────────────

(defn- build-rec
  [^floats data ^ints child trunks start end
   ^AtomicInteger next-idx ^AABB tmp-aabb]
  (let [idx (.getAndIncrement next-idx)
        n   (- end start)]
    (if (= n 1)
      (let [^IMeshTrunk trunk (nth trunks start)]
        (mt/mesh-trunk-aabb trunk tmp-aabb)
        (bvh/write-aabb! data idx tmp-aabb)
        (set-leaf! child idx start)
        idx)
      (let [codes    (mapv #(.getMorton ^IMeshTrunk %) (subvec trunks start end))
            min-code (reduce min codes)
            max-code (reduce max codes)
            diff-bit (Long/highestOneBit (bit-xor min-code max-code))
            split    (loop [i start]
                       (if (>= i end)
                         end
                         (if (not= 0 (bit-and (.getMorton ^IMeshTrunk (nth trunks i)) diff-bit))
                           i
                           (recur (inc i)))))
            split    (if (or (= split start) (= split end))
                       (+ start (quot n 2))
                       split)
            left-idx  (build-rec data child trunks start split next-idx tmp-aabb)
            right-idx (build-rec data child trunks split end next-idx tmp-aabb)]
        (bvh/merge-aabb! data idx left-idx right-idx)
        (set-internal! child idx left-idx right-idx)
        idx))))

;; ─── 公共构建函数 ─────────────────────────────

(defn build-lbvh
  "根据已按 Morton 码排序的 MeshTrunk 序列构建线性 BVH。
   返回 BVH 实例——primitive-count 存于 ext。"
  ^BVH [mesh-trunks]
  (let [n       (count mesh-trunks)
        trunk-v (vec mesh-trunks)]
    (if (zero? n)
      (bvh/make-bvh (float-array 0) (int-array 0) 0)
      (let [data     (allocate-aabb-array n)
            child    (allocate-child-array n)
            next-idx (AtomicInteger. 0)
            tmp-aabb (AABB.)]
        (build-rec data child trunk-v 0 n next-idx tmp-aabb)
        (bvh/make-bvh data child n)))))

(set! *unchecked-math* nil)