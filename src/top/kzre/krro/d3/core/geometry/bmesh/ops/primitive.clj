(ns top.kzre.krro.d3.core.geometry.bmesh.ops.primitive
  "BMesh 原始操作——分配 / 初始化 / 释放。

   无流形假设——只分配索引 + 写默认值——
   不做拓扑连接——不检查邻接。

   拓扑连接——归 link.clj。
   流形添加 / 删除——归 add.clj / remove.clj。

   ── 顶点 ──
     add-isolated-vert!       分配索引——position ORIGIN——out-edge -1
     remove-isolated-vert!    释放索引

   ── 边 ──
     add-isolated-edge!       分配索引——endpoints NONE——loop -1
     remove-isolated-edge!    释放索引

   ── 环 ──
     add-isolated-loop!       分配索引——UV ZERO——ownership NONE——
                              ring NONE——radial-ring NONE
     remove-isolated-loop!    释放索引

   ── 面 ──
     add-isolated-face!       分配索引——normal UP——submesh -1——
                              topology NONE
     remove-isolated-face!    释放索引

   ── 契约 ──
     add-isolated-*! —— 返回新索引——不改变任何已有元素的拓扑。
     remove-isolated-*! —— 调用方保证元素确实孤立——
                          不做级联检查——不修改邻居。

   全部只碰 editor 的 alloc / free + access 的写。
   不触碰 nav——无遍历——无查询。

   ── 默认值 —— 值类型单例 —— 零分配 ──
     Position/ORIGIN / Normal/UP / UV/ZERO
     EdgeEndpoints/NONE / LoopOwnership/NONE
     LoopRing/NONE / LoopRadialRing/NONE / FaceTopology/NONE"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access :as access])
  (:import
    (top.kzre.krro.d3.core.geometry Position Normal UV)
    (top.kzre.krro.d3.core.geometry.bmesh
      BMeshEditor
      EdgeEndpoints LoopOwnership LoopRing LoopRadialRing FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 顶点
;; ═══════════════════════════════════════════════

(defn add-isolated-vert!
  "添加孤立顶点。

   position = ORIGIN
   out-edge = -1

   返回新顶点索引。"
  ^long [^BMeshEditor editor]
  (let [v (long (.allocVert editor))]
    (access/set-vert-position! editor v Position/ORIGIN)
    (access/set-vert-out-edge! editor v -1)
    v))

(defn remove-isolated-vert!
  "释放孤立顶点索引。

   调用方保证顶点确实孤立（out-edge == -1）。"
  [^BMeshEditor editor ^long v]
  (.freeVert editor (int v)))

;; ═══════════════════════════════════════════════
;; 边
;; ═══════════════════════════════════════════════

(defn add-isolated-edge!
  "添加孤立边。

   endpoints = NONE
   loop = -1

   返回新边索引。"
  ^long [^BMeshEditor editor]
  (let [e (long (.allocEdge editor))]
    (access/set-edge-endpoints! editor e EdgeEndpoints/NONE)
    (access/set-edge-loop! editor e -1)
    e))

(defn remove-isolated-edge!
  "释放孤立边索引。

   调用方保证边确实孤立（loop == -1）。"
  [^BMeshEditor editor ^long e]
  (.freeEdge editor (int e)))

;; ═══════════════════════════════════════════════
;; 环
;; ═══════════════════════════════════════════════

(defn add-isolated-loop!
  "添加孤立环。

   UV = ZERO
   ownership = NONE
   ring = NONE
   radial-ring = NONE

   返回新环索引。"
  ^long [^BMeshEditor editor]
  (let [l (long (.allocLoop editor))]
    (access/set-loop-uv! editor l UV/ZERO)
    (access/set-loop-ownership! editor l LoopOwnership/NONE)
    (access/set-loop-ring! editor l LoopRing/NONE)
    (access/set-loop-radial-ring! editor l LoopRadialRing/NONE)
    l))

(defn remove-isolated-loop!
  "释放孤立环索引。

   调用方保证环确实孤立（ring / radial-ring 全 -1——
   ownership 全 -1）。"
  [^BMeshEditor editor ^long l]
  (.freeLoop editor (int l)))

;; ═══════════════════════════════════════════════
;; 面
;; ═══════════════════════════════════════════════

(defn add-isolated-face!
  "添加孤立面。

   normal = UP
   submesh = -1
   topology = NONE

   返回新面索引。"
  ^long [^BMeshEditor editor]
  (let [f (long (.allocFace editor))]
    (access/set-face-normal! editor f Normal/UP)
    (access/set-face-submesh! editor f -1)
    (access/set-face-topology! editor f FaceTopology/NONE)
    f))

(defn remove-isolated-face!
  "释放孤立面索引。

   调用方保证面确实孤立（topology.loop == -1）。"
  [^BMeshEditor editor ^long f]
  (.freeFace editor (int f)))

(set! *unchecked-math* nil)