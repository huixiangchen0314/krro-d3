(ns top.kzre.krro.d3.core.geometry.bmesh.face
  "BMesh 面——属性布局 + 拓扑连接。

   面由一圈 loop 组成——首尾相接的边界。

   ── 属性（BMFaceNormals）────────────────────────
     - normal     面法线 x y z——编辑期缓存——按需重算
                  stride = 3

   ── 属性（BMFaceSubmeshIds）─────────────────────
     - submesh    面所属的子网格 id
                  烘焙时按 submesh-id 分组——生成 SubMesh
                  材质由子网格引用——面不直接持有材质
                  stride = 1

   ── 连接（BMFaceTopology）───────────────────────
     - loop       环——两个分量
       - idx        环入口——任一条 loop 索引
       - len        环长（顶点数）——缓存——避免每次遍历
                    三角形 = 3——四边形 = 4——N 边形 = N
                  stride = 2

   面身份 = 数组下标——非负整数。

   对应 BMesh 字段：
     faceNormals     ← BMFaceNormals（normal）
     faceSubmeshIds  ← BMFaceSubmeshIds（submesh）
     faceTopology    ← BMFaceTopology（loop.idx / loop.len）"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMFaceNormals
(declare normal-x normal-y normal-z)
(declare set-normal! set-normal-x! set-normal-y! set-normal-z!)
(declare obj-normal-x obj-normal-y obj-normal-z)
(declare obj-set-normal-x! obj-set-normal-y! obj-set-normal-z!)

;; BMFaceSubmeshIds
(declare submesh-id)
(declare set-submesh-id!)
(declare obj-submesh-id)
(declare obj-set-submesh-id!)

;; BMFaceTopology
(declare loop-idx loop-len)
(declare set-loop-idx! set-loop-len!)
(declare obj-loop-idx obj-loop-len)
(declare obj-set-loop-idx! obj-set-loop-len!)

;; ═══════════════════════════════════════════════
;; 属性——面法线
;; ═══════════════════════════════════════════════

(deflayout BMFaceNormals
           {:data [:float [:normal [:x :y :z]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 属性——子网格归属
;; ═══════════════════════════════════════════════

(deflayout BMFaceSubmeshIds
           {:attrs [:int [:submesh [:id]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——拓扑
;; ═══════════════════════════════════════════════

(deflayout BMFaceTopology
           {:conn [:int
                   [:loop [:idx :len]]]}
           {:unchecked-math? true})