(ns top.kzre.krro.d3.core.geometry.mesh
  (:require
    [top.kzre.krro.d3.core.geometry.vertex-buffer])
  (:import
    (clojure.lang Keyword)))

(defrecord SubMesh
  [^Keyword material-id
   ^int     start-index   ;; 在全局 indices 数组中的起始位置
   ^int     count])       ;; 三角形数量（每三角形3个索引，实际索引数 = count*3）


(defrecord TrunkedMesh
  [chunks       ;; 向量，元素为 TrunkHandle
   ^ints indices      ;; 全局索引数组
   edge-table
   submeshes
   ^floats  lbvh-nodes
   ^boolean lbvh-dirty?])

;; ──────────────────────────────────────────────
;; 工厂函数
;; ──────────────────────────────────────────────
(defn make-mesh
  "创建一个网格。edge-table 可为空映射，submeshes 可为空向量。"
  [chunks indices & {:keys [edge-table submeshes lbvh-nodes lbvh-dirty?]}]
  (TrunkedMesh.
    chunks
    indices
    (or edge-table (hash-map))
    (or submeshes [])
    (or lbvh-nodes nil)        ;; 初始可以为 nil，表示未构建
    (boolean (or lbvh-dirty? true)))) ;; 默认脏，需要构建