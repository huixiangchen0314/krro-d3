(ns top.kzre.krro.d3.core.geometry.bmesh.loop
  "BMesh 环——属性布局 + 拓扑连接。

   环 = 角点 = 一个面上、一条边上的一侧。
   逐角点数据（UV / 顶点色 / 自定义法线）挂在这里——
   同一顶点在不同面上可取不同值。

   ── 属性（BMLoopAttrs）──────────────────────────
     - uv        UV 坐标 u v

   ── 连接（BMLoopConn）──────────────────────────
     - vert        环的起始顶点索引
     - edge        环所在的边索引
     - face        环所属的面索引
     - next        面内下一条 loop
     - prev        面内上一条 loop
     - radial      径向环——同一条边上的相邻 loop
       - next      径向环下一条
       - prev      径向环上一条

   约定：edge 连接 vert 和 next 的 vert。
   环身份 = 数组下标——非负整数。"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMLoopAttrs
(declare uv-u uv-v)
(declare set-uv-u! set-uv-v!)
(declare obj-uv-u obj-uv-v)
(declare obj-set-uv-u! obj-set-uv-v!)

;; BMLoopConn
(declare vert-idx set-vert-idx!)
(declare edge-idx set-edge-idx!)
(declare face-idx set-face-idx!)
(declare next-idx set-next-idx!)
(declare prev-idx set-prev-idx!)
(declare radial-next set-radial-next!)
(declare radial-prev set-radial-prev!)
(declare obj-vert-idx obj-set-vert-idx!)
(declare obj-edge-idx obj-set-edge-idx!)
(declare obj-face-idx obj-set-face-idx!)
(declare obj-next-idx obj-set-next-idx!)
(declare obj-prev-idx obj-set-prev-idx!)
(declare obj-radial-next obj-set-radial-next!)
(declare obj-radial-prev obj-set-radial-prev!)

;; ═══════════════════════════════════════════════
;; 属性
;; ═══════════════════════════════════════════════

(deflayout BMLoopAttrs
           {:data [:float [:uv [:u :v]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接
;; ═══════════════════════════════════════════════

(deflayout BMLoopConn
           {:conn [:int
                   [:vert   [:idx]
                    :edge   [:idx]
                    :face   [:idx]
                    :next   [:idx]
                    :prev   [:idx]
                    :radial [:next :prev]]]}
           {:unchecked-math? true})