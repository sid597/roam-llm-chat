(ns ui.components.cytoscape
 (:require [reagent.core :as r]
           [reagent.dom :as rd]
           [ui.extract-data.dg :refer [all-dg-nodes get-all-discourse-node-from-akamatsu-graph-for]]
   ["cytoscape" :as cytoscape]))

(defonce cy! (r/atom nil))

(def cyto-nodes
  (mapv
    (fn [n]
      (println "nnn" n)
      {:data {:id (:uid n)
              :label (:title n)}})
    all-dg-nodes))


#_(def cyto-edges
    (mapv
      (fn [n]
        (let [src (:uid (first n))
              rel  (second n)
              tar (:uid (last n))]
          {:data
           {:id (str src "->" tar)
            :source src
            :target tar
            :label rel}}))
      []
      #_discourse-queries))

cyto-nodes

(def edge-colors
  {"InformedBy" "#1f78b4"
   "Informs" "#33a02c"
   "OpposedBy" "#e31a1c"
   "SupportedBy" "#ff7f00"
   "Reproduces" "#6a3d9a"
   "Supports" "#b15928"
   "Opposes" "#a6cee3"})

(def node-colors
  {"QUE" "#4e79a7"
   "CLM" "#f28e2b"
   "EVD" "#e15759"
   "HYP" "#76b7b2"
   "ISS" "#59a14f"
   "CON" "#edc948"
   "RES" "#b07aa1"})

(defn convert-to-cytoscape-nodes [unique-nodes]
  (mapv (fn [[x {:keys [uid title type]}]]
          (println "----" (node-colors type) type)
          {:data {:id uid :label title :color (node-colors type)}})
    unique-nodes))

(defn convert-to-cytoscape-edges [data]
  (mapv (fn [[source label target]]
          {:data {:id (str (:uid source) "-" (:uid target))
                  :source (:uid source)
                  :target (:uid target)
                  :label label
                  :color (edge-colors label)}
           :group "edges"})
    data))

(def cyto-edges
  (convert-to-cytoscape-edges
    (get-all-discourse-node-from-akamatsu-graph-for
      "[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules")))

(defn get-cyto-format-data-for-node [node]
  (let [res (get-all-discourse-node-from-akamatsu-graph-for node)
        edges (convert-to-cytoscape-edges (:edges res))
        nodes (convert-to-cytoscape-nodes (:nodes res))]
    (into [] (concat nodes edges))))

(get-cyto-format-data-for-node
  "[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules")

(defn init-cytoscape [element]
  (reset! cy! (cytoscape (clj->js {:container element
                                   :elements (get-cyto-format-data-for-node
                                               "[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules")
                                             #_(concat cyto-nodes cyto-edges) #_[{:data  {:id "a" :label "THIS IS IT"}}
                                                                                 {:data  {:id "b"}}
                                                                                 {:data  {:id "c"}}
                                                                                 {:data  {:id "d"}}
                                                                                 {:data  {:id "e"}}
                                                                                 {:data  {:id "ac" :source "a" :target "c" :label "Test"}
                                                                                  :group "edges"}
                                                                                 {:data  {:id "de" :source "d" :target "e" :label "Test"}
                                                                                  :group "edges"}
                                                                                 {:data  {:id "ab" :source "a" :target "b" :label "Test"}
                                                                                  :group "edges"}]
                                   :style  [{:selector "node"
                                             :style{:background-color "data(color)"
                                                    :shape "rectangle"
                                                    :height "100px"
                                                    :width "150px"
                                                    :label "data(label)"
                                                    :text-valign "center"
                                                    :text-halign "center"
                                                    :text-wrap "wrap"
                                                    :text-max-width "150px"
                                                    :text-justification "center"
                                                    :line-height 1.2
                                                    :font-size 10}}
                                            {:selector "edge"
                                             :style {:width 3
                                                     :line-color "data(color)"
                                                     :target-arrow-color "data(color)"
                                                     :target-arrow-shape "triangle"
                                                     :curve-style "bezier"
                                                     :label "data(label)"
                                                     :text-margin-y -10
                                                     :text-rotation "autorotate"
                                                     :font-size 12
                                                     :text-valign "top"
                                                     :color "data(color)" #_"#ff0000"}}]
                                   :layout {:name "circle"
                                            :padding 90
                                            ;:nodeDimensionsIncludeLabels true
                                            :spacingFactor 0.7
                                            :fit false}
                                   #_{:name "concentric"
                                      :fit true
                                      :circle true
                                      :avoidOverlap true
                                      :padding 10
                                      :directed "true"}}))))

(defn add-node [new-node]
  (.add @cy! (clj->js new-node)))

#_(add-node {:data  {:id "ggwp"}})


(defn cytoscape-component [block-uid]
  (r/create-class
    {:component-did-mount (fn [this]
                            (init-cytoscape (rd/dom-node this)))
     :reagent-render (fn []
                       [:div.TEST {:style {:width "1200px"
                                           :border "2px red"
                                           :height "800px"}}])}))

(defn cytoscape-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [cytoscape-component block-uid] parent-el)))
