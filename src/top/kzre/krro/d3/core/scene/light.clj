(ns top.kzre.krro.d3.core.scene.light
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.d3.core.scene.core :as scene]))

;; ── 通用灯光属性 ────────────────────────────
(s/def ::light-type #{:point :directional :spot :area})
(s/def ::color (s/coll-of float? :kind vector? :count 3))
(s/def ::intensity float?)
(s/def ::range (s/nilable float?))
(s/def ::shadow? boolean?)
(s/def ::shadow-bias (s/nilable float?))
(s/def ::shadow-resolution (s/nilable int?))

;; ── 特有属性 ──────────────────────────────────
;; 聚光灯
(s/def ::spot-angle (s/nilable float?))
(s/def ::spot-blend (s/nilable float?))
;; 方向光 / 聚光灯方向（模型空间）
(s/def ::direction (s/nilable (s/coll-of float? :kind vector? :count 3)))
;; 区域光形状
(s/def ::area-shape #{:rect :disc})
(s/def ::area-width float?)
(s/def ::area-height float?)
(s/def ::area-radius float?)

;; ── 灯光类型分发 multi-method ──────────────
(defmulti light-type-dispatch ::light-type)

;; ── 点光源 ──────────────────────────────────
(s/def ::point-light
  (s/keys :req-un [::light-type ::color ::intensity]
          :opt-un [::range ::shadow? ::shadow-bias ::shadow-resolution]))
(defmethod light-type-dispatch :point [_] ::point-light)

;; ── 方向光 ──────────────────────────────────
(s/def ::directional-light
  (s/keys :req-un [::light-type ::color ::intensity ::direction]
          :opt-un [::shadow? ::shadow-bias ::shadow-resolution]))
(defmethod light-type-dispatch :directional [_] ::directional-light)

;; ── 聚光灯 ──────────────────────────────────
(s/def ::spot-light
  (s/keys :req-un [::light-type ::color ::intensity ::direction ::spot-angle]
          :opt-un [::range ::spot-blend ::shadow? ::shadow-bias ::shadow-resolution]))
(defmethod light-type-dispatch :spot [_] ::spot-light)

;; ── 区域光 ──────────────────────────────────
(s/def ::area-light
  (s/keys :req-un [::light-type ::color ::intensity ::area-shape]
          :opt-un [::area-width ::area-height ::area-radius ::shadow? ::shadow-bias ::shadow-resolution]))
(defmethod light-type-dispatch :area [_] ::area-light)

;; ── 灯光组件（二级 multispec） ───────────────
(s/def ::light-component
  (s/merge
    (s/keys :req-un [::type])   ;; 确保包含场景组件分派键 :type
    (s/multi-spec light-type-dispatch ::light-type)))

;; ── 注册到场景组件系统 ─────────────────────
(defmethod scene/component-type :light [_]
  ::light-component)