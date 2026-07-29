(ns top.kzre.krro.d3.core.geometry.mesh
  (:require [top.kzre.krro.d3.core.geometry.vertex-buffer])
  (:import (clojure.lang Keyword)
           (top.kzre.krro.d3.core.geometry.vertex_buffer VertexBuffer)))

(defrecord SubMesh
  [^Keyword material-id
   ^int     start-index   ;; 在全局 indices 数组中的起始位置
   ^int     count])       ;; 三角形数量（每三角形3个索引，实际索引数 = count*3）

(defrecord MeshTrunk
  [^VertexBuffer buffer
   ^int start-vertex
   ^int vertex-count
   ;; 元数据
   ^long morton
   ^floats aabb      ;; 6 floats: minX,minY,minZ,maxX,maxY,maxZ
   ^int lod          ;; 0 = 全细节, 1,2...
   ^boolean loaded?]
  )

(defrecord TrunkedMesh
  [chunks       ;; 每个元素是 ChunkedMesh，按全局顶点索引顺序排列
   ^ints    indices      ;; 全局索引，不变
   edge-table
   submeshes
   ])

;; ──────────────────────────────────────────────
;; 工厂函数
;; ──────────────────────────────────────────────
(defn make-mesh
  "创建一个网格。edge-table 可为空映射，submeshes 可为空向量。"
  [vertices indices & {:keys [edge-table submeshes]}]
  (TrunkedMesh.
         vertices
         indices
         (or edge-table (hash-map))
         (or submeshes [])
         ))