(ns top.kzre.krro.d3.core.geometry.bmesh.vert
  "BMesh 顶点——属性布局 + 拓扑连接。

   本命名空间定义顶点的两套数组：

   ── 属性（BMVertAttrs）──────────────────────────
     几何 / 逐元素数据——可能随需求扩展。
     - position   位置 x y z
     - normal     法线 x y z

   ── 连接（BMVertConn）──────────────────────────
     拓扑索引——结构稳定，极少变化。
     - out-edge   一条出边的索引——从本顶点出发遍历邻接

   两组数据分离——属性可扩展，连接保持稳定。
   顶点身份 = 数组下标——非负整数。
   孤立顶点的 out-edge 为 -1。

   布局由 deflayout 管理——逻辑代码不硬编码下标。
   扩展属性：在 BMVertAttrs 的字段向量里加一项。"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMVertAttrs
(declare position-x position-y position-z)
(declare normal-x normal-y normal-z)
(declare set-position-x! set-position-y! set-position-z!)
(declare set-normal-x! set-normal-y! set-normal-z!)
(declare obj-position-x obj-position-y obj-position-z)
(declare obj-normal-x obj-normal-y obj-normal-z)
(declare obj-set-position-x! obj-set-position-y! obj-set-position-z!)
(declare obj-set-normal-x! obj-set-normal-y! obj-set-normal-z!)

;; BMVertConn
(declare out-edge-idx set-out-edge-idx!)
(declare obj-out-edge-idx obj-set-out-edge-idx!)

;; ═══════════════════════════════════════════════
;; 属性
;; ═══════════════════════════════════════════════

(deflayout BMVertAttrs
           {:data [:float [:position [:x :y :z]
                           :normal   [:x :y :z]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接
;; ═══════════════════════════════════════════════

(deflayout BMVertConn
           {:conn [:int [:out-edge [:idx]]]}
           {:unchecked-math? true})