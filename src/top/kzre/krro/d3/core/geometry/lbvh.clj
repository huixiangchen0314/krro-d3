(ns top.kzre.krro.d3.core.geometry.lbvh
  "线性编码BVH，构建速度 O(n log n)."
  (:require
    [top.kzre.krro.d3.core.geometry.bvh :as bvh])
  (:import
    (java.util.concurrent.atomic AtomicInteger)
    (top.kzre.krro.d3.core.geometry DefaultMeshTrunk IMeshTrunk)))

(set! *unchecked-math* :warn-on-boxed)


;; ─── 内部辅助（仍直接操作 float 数组）─────────────
(defn- node-count [n]
  (+ n (max 0 (dec n))))

(defn- allocate-nodes [n]
  (float-array (* (node-count n) 8)))


(defn- set-leaf!
  [^floats bvh-data node-idx chunk-idx]
  (bvh/set-children-left!  bvh-data node-idx -1.0)
  (bvh/set-children-right! bvh-data node-idx (float chunk-idx)))

(defn- set-internal!
  [^floats bvh-data node-idx left-idx right-idx]
  (bvh/set-children-left!  bvh-data node-idx (float left-idx))
  (bvh/set-children-right! bvh-data node-idx (float right-idx)))

;; ─── 构建核心 ──────────────────────────────────
(defn- build-rec
  [^floats bvh-data trunks start end ^AtomicInteger next-idx]
  (let [n (- end start)]
    (if (= n 1)
      (let [idx (.getAndIncrement next-idx)
            ^IMeshTrunk trunk (nth trunks start)]
        (bvh/write-aabb! bvh-data idx (.getAabb trunk))
        (set-leaf! bvh-data idx start)
        idx)
      (let [codes (map #(.getMorton ^DefaultMeshTrunk %) (subvec trunks start end))
            min-code (reduce min codes)
            max-code (reduce max codes)
            diff-bit (Long/highestOneBit (bit-xor min-code max-code))
            split (loop [i start]
                    (if (>= i end)
                      end
                      (if (not= 0 (bit-and (.getMorton ^DefaultMeshTrunk (nth trunks i)) diff-bit))
                        i
                        (recur (inc i)))))
            split (if (or (= split start) (= split end))
                    (+ start (quot n 2))
                    split)
            left-idx  (build-rec bvh-data trunks start split next-idx)
            right-idx (build-rec bvh-data trunks split end next-idx)
            idx (.getAndIncrement next-idx)]
        (bvh/merge-aabb! bvh-data idx left-idx right-idx)
        (set-internal! bvh-data idx left-idx right-idx)
        idx))))

;; ─── 公共构建函数（返回 BVH 实例） ──────────────
(defn build-lbvh
  "根据已按 Morton 码排序的 MeshTrunk 序列构建线性 BVH。
   返回 {:bvh BVH, :trunk-count n}"
  [mesh-trunks]
  (let [n (count mesh-trunks)
        nodes (allocate-nodes n)
        next-idx (AtomicInteger. 0)]
    (if (zero? n)
      {:bvh (bvh/make-bvh (float-array 0))
       :trunk-count 0}
      (do
        (build-rec nodes (vec mesh-trunks) 0 n next-idx)
        {:bvh (bvh/make-bvh nodes)
         :trunk-count n}))))

(set! *unchecked-math* nil)