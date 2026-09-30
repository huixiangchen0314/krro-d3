(ns top.kzre.krro.d3.core.geometry.bmesh.loop
  "BMesh 环——属性布局 + 拓扑连接。

   环 = 角点 = 一个面上、一条边上的一侧。
   逐角点数据（UV / 顶点色 / 自定义法线）挂在这里——
   同一顶点在不同面上可取不同值。

   ── 属性（BMLoopUvs）────────────────────────────
     - uv         UV 坐标 u v
                  stride = 2

   ── 连接（BMLoopOwnership）──────────────────────
     - vert       环的起始顶点索引
     - edge       环所在的边索引
     - face       环所属的面索引
                  约定：edge 连接 vert 和 next.vert
                  stride = 3

   ── 连接（BMLoopRing）───────────────────────────
     - next       面内下一条环
     - prev       面内上一条环
                  满足 next.prev == loop
                  stride = 2

   ── 连接（BMLoopRadialRing）─────────────────────
     - radial-next  径向环下一条——同一条边上的相邻环
     - radial-prev  径向环上一条
                    允许非流形——径向链长度不定
                    stride = 2

   环身份 = 数组下标——非负整数。

   对应 BMesh 字段：
     loopUvs         ← BMLoopUvs（uv）
     loopOwnership   ← BMLoopOwnership（vert / edge / face）
     loopRing        ← BMLoopRing（next / prev）
     loopRadialRing  ← BMLoopRadialRing（radial-next / radial-prev）"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMLoopUvs
(declare uv-u uv-v)
(declare set-uv-u! set-uv-v!)
(declare obj-uv-u obj-uv-v)
(declare obj-set-uv-u! obj-set-uv-v!)

;; BMLoopOwnership
(declare vert-idx set-vert-idx!)
(declare edge-idx set-edge-idx!)
(declare face-idx set-face-idx!)
(declare obj-vert-idx obj-set-vert-idx!)
(declare obj-edge-idx obj-set-edge-idx!)
(declare obj-face-idx obj-set-face-idx!)

;; BMLoopRing
(declare next-idx set-next-idx!)
(declare prev-idx set-prev-idx!)
(declare obj-next-idx obj-set-next-idx!)
(declare obj-prev-idx obj-set-prev-idx!)

;; BMLoopRadialRing
(declare radial-next set-radial-next!)
(declare radial-prev set-radial-prev!)
(declare obj-radial-next obj-set-radial-next!)
(declare obj-radial-prev obj-set-radial-prev!)

;; ═══════════════════════════════════════════════
;; 属性
;; ═══════════════════════════════════════════════

(deflayout BMLoopUvs
           {:data [:float [:uv [:u :v]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——归属
;; ═══════════════════════════════════════════════

(deflayout BMLoopOwnership
           {:conn [:int
                   [:vert [:idx]
                    :edge [:idx]
                    :face [:idx]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——面内链
;; ═══════════════════════════════════════════════

(deflayout BMLoopRing
           {:conn [:int
                   [:next [:idx]
                    :prev [:idx]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——径向链
;; ═══════════════════════════════════════════════

(deflayout BMLoopRadialRing
           {:conn [:int
                   [:radial-next [:idx]
                    :radial-prev [:idx]]]}
           {:unchecked-math? true})