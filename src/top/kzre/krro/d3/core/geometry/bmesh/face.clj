(ns top.kzre.krro.d3.core.geometry.bmesh.face
  "BMesh 面——属性布局 + 拓扑连接。

   面由一圈 loop 组成——首尾相接的边界。

   ── 属性（BMFaceAttrs）──────────────────────────
     - normal     面法线 x y z——编辑期缓存——按需重算
     - submesh    面所属的子网格 id
                  烘焙时按 submesh-id 分组——生成 SubMesh
                  材质由子网格引用——面不直接持有材质

   ── 连接（BMFaceConn）──────────────────────────
     - loop       环的入口——任一条 loop 索引
     - len        环长（顶点数）——缓存——避免每次遍历

   面身份 = 数组下标——非负整数。"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMFaceAttrs
(declare normal-x normal-y normal-z)
(declare submesh-id)
(declare set-normal-x! set-normal-y! set-normal-z!)
(declare set-submesh-id!)
(declare obj-normal-x obj-normal-y obj-normal-z)
(declare obj-submesh-id)
(declare obj-set-normal-x! obj-set-normal-y! obj-set-normal-z!)
(declare obj-set-submesh-id!)

;; BMFaceConn
(declare loop-idx len-count)
(declare set-loop-idx! set-len-count!)
(declare obj-loop-idx obj-len-count)
(declare obj-set-loop-idx! obj-set-len-count!)

;; ═══════════════════════════════════════════════
;; 属性
;; ═══════════════════════════════════════════════

(deflayout BMFaceAttrs
           {:data  [:float [:normal   [:x :y :z]]]
            :attrs [:int   [:submesh  [:id]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接
;; ═══════════════════════════════════════════════

(deflayout BMFaceConn
           {:conn [:int
                   [:loop [:idx]
                    :len  [:count]]]}
           {:unchecked-math? true})