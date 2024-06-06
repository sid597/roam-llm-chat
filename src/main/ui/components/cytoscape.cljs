(ns ui.components.cytoscape
 (:require [reagent.core :as r]
           [reagent.dom :as rd]
           [ui.utils :refer [q]]
           ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
           [ui.extract-data.dg :refer [determine-node-type all-dg-nodes get-all-discourse-node-from-akamatsu-graph-for]]
   ["cytoscape" :as cytoscape]
   ["cytoscape-cose-bilkent" :as cose-bilkent]))

(defonce cy! (r/atom nil))

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

(defn convert-to-cytoscape-nodes [unique-nodes all-nodes]
  (reduce (fn [acc [x {:keys [uid title type]}]]
            (assoc acc title {:data
                              {:id uid
                               :label title
                               :bwidth "0px"
                               :bstyle "solid"
                               :bcolor "black"
                               :color (node-colors type)}}))
    all-nodes
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

(defn get-node-data [node-title]
  (let [res       (first
                    (q '[:find (pull ?e [:node/title :block/uid])
                         :in $ ?n
                         :where [?e :node/title ?n]]
                      node-title))
        node-data (first (map (fn [{:keys [uid title type]}]
                                {:data {:id uid
                                        :label title
                                        :color (node-colors (name (determine-node-type title)))
                                        :bwidth "3px"
                                        :bstyle "solid"
                                        :bcolor "black"}})
                           res))]
    {node-title node-data}))

(defn random-uid [length]
  (let [chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        chars-len (count chars)]
    (apply str (repeatedly length #(nth chars (rand-int chars-len))))))

(defn suggested-nodes [nodes]
  (let [res (atom {})]
    (doseq [i (range (count nodes))]
      (let [rid (str i "-" (random-uid 10))
            title (nth nodes i)
            node-data {title {:data {:id rid
                                     :label title
                                     :color "white"
                                     :bwidth "2px"
                                     :bstyle "dashed"
                                     :bcolor "black"}}}]
       (reset! res (merge node-data @res))))
    @res))

(def ex-suggested-nodes ["[[RES]] - Increasing membrane tension from 2 pN/nm to 2000 pN/nm in
           simulations showed a broader assistance by myosin in
           internalization"
                         "[[RES]] - Resistance to internalization increased as myosin unbinding rate
                   decreased at higher membrane tension in simulations"
                         "[[RES]] - At 20 pN/nm membrane tension, areas with low myosin unbinding
                   rates had decreased internalization resistance"
                         "[[ISS]] - Investigate the relationship between myosin catch bonding
                   parameters and internalization efficiency in live cell
                   experiments"
                         "[[HYP]] - Myosin assists more broadly in membrane internalization under
                   higher tension conditions"
                         "[[CLM]] - High membrane tension facilitates myosin’s role in overcoming
                   resistance to internalization"])

(comment
  (get-node-data "[[HYP]] - Myosin-I directly applies force on the actin network via its power stroke to assist in endocytosis"))



(defn get-cyto-format-data-for-node [nodes]
  (let [all-nodes (atom {})
        all-edges (atom [])]
    (doall
     (map
       (fn [node]
         (println "Get cyto format processing: " node)
         (let [res (get-all-discourse-node-from-akamatsu-graph-for node)
               edges (convert-to-cytoscape-edges (:edges res))
               nodes (convert-to-cytoscape-nodes (:nodes res) @all-nodes)]
           (do
            (reset! all-nodes (merge (get-node-data node) @all-nodes))
            (when (not (empty? nodes))
              (reset! all-nodes (merge nodes @all-nodes)))
            (swap! all-edges concat edges))))
       nodes))
    (into [] (concat
               #_(vals
                   (suggested-nodes ex-suggested-nodes))
               (vals @all-nodes)
               @all-edges))))


(comment
 (:nodes (get-all-discourse-node-from-akamatsu-graph-for "[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I "))

 (get-cyto-format-data-for-node ["[[HYP]] - Myosin-I directly applies force on the actin network via its power stroke to assist in endocytosis"])
 (get-cyto-format-data-for-node ["[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I "]))


#_(def example-elements
    (get-cyto-format-data-for-node
      ["[[RES]] - Under increasing values of membrane tension, endocytic myosin-I assisted endocytosis under a wider range of values of unbinding rate and catch bonding  - [[@cytosim/simulate myosin-I with calibrated stiffness value and report unbinding force vs. unbinding rate]]"
       "[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I"
       "[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I "
       "[[HYP]] - Myosin-I directly assists with endocytosis by using its power stroke to apply a force directly assisting internalization"
       "[[HYP]] - Myosin-I directly applies force on the actin network via its power stroke to assist in endocytosis"
       "[[HYP]] - Transient actin-binding alone is sufficient for myosin to assist endocytosis"
       ;; Test
       #_"[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules"
       #_"[[QUE]] - Does actin polymerization adjacent to CLICs help to form CLIC tubules or scission of CLICs?"
       #_"[[EVD]] - hPSCs were found to exert strong inward-directed mechanical forces on the ECM at specific locations coinciding with ventral stress fibers at the colony edge.  - [[@narva2017strong]]"]))


(def default-layout
  {:name "cose-bilkent"
   :nodeDimensionsIncludeLabels true
   :padding 10
   :gravityRange 0.8
   :idealEdgeLength 100
   :fit true})


(defn init-cytoscape [container elements cy-el]
  (reset! cy-el (cytoscape (clj->js {:container container
                                     :elements (get-cyto-format-data-for-node @elements)
                                     :style  [{:selector "node"
                                               :style{:background-color "data(color)"
                                                      :border-width "data(bwidth)"
                                                      :border-style "data(bstyle)"
                                                      :border-color "data(bcolor)"
                                                      :shape "rectangle"
                                                      :height "120px"
                                                      :width "150px"
                                                      :label "data(label)"
                                                      :text-valign "center"
                                                      :text-halign "center"
                                                      :text-wrap "wrap"
                                                      :text-max-width "150px"
                                                      :text-justification "center"
                                                      :line-height 1.2
                                                      :font-size 10
                                                      :padding "5px"}}
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
                                     :layout default-layout}))))



(defn cytoscape-component []
  (let [elements (r/atom ["[[RES]] - Under increasing values of membrane tension, endocytic myosin-I assisted endocytosis under a wider range of values of unbinding rate and catch bonding  - [[@cytosim/simulate myosin-I with calibrated stiffness value and report unbinding force vs. unbinding rate]]"
                          "[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I"
                          "[[RES]] - For baseline levels of membrane tension, there was an inverse relationship between the change in number of filaments and change in internalization for different unbinding and catch bonding properties of myosin-I "
                          "[[HYP]] - Myosin-I directly assists with endocytosis by using its power stroke to apply a force directly assisting internalization"
                          ;"[[HYP]] - Myosin-I directly applies force on the actin network via its power stroke to assist in endocytosis"
                          #_"[[HYP]] - Transient actin-binding alone is sufficient for myosin to assist endocytosis"
                          ;; Test
                          #_"[[CLM]] - Actin polymerization participates in the scission of CLIC endocytic tubules"
                          #_"[[QUE]] - Does actin polymerization adjacent to CLICs help to form CLIC tubules or scission of CLICs?"
                          #_"[[EVD]] - hPSCs were found to exert strong inward-directed mechanical forces on the ECM at specific locations coinciding with ventral stress fibers at the colony edge.  - [[@narva2017strong]]"])
        cy-el (atom nil)
        lout (atom nil)]
   (fn []
     [:div.cytoscape-main
      [:div.cytoscape-view
        {:style {:width "800px"
                 :border "2px solid lightgrey"
                 :height "800px"}
         :ref (fn [el]
                (when el
                  (init-cytoscape el elements cy-el)))}]
      [:div.action-area
       [:> Button
        {:minimal true
         :small true
         :style {:flex "1 1 1"}
         :on-click (fn [_]
                     (let [new-nodes (get-cyto-format-data-for-node ["[[EVD]] - hPSCs were found to exert strong inward-directed mechanical forces on the ECM at specific locations coinciding with ventral stress fibers at the colony edge.  - [[@narva2017strong]]"])]
                       (println "new node")
                       (doseq [node new-nodes]
                         (cljs.pprint/pprint node)
                         (.add @cy-el (clj->js node))
                         (reset! lout (.layout @cy-el (clj->js{:name "cose-bilkent"
                                                               :animate true
                                                               :animationDuration 1000
                                                               :idealEdgeLength 100
                                                               :edgeElasticity 0.95
                                                               :gravity 1.0
                                                               :nodeDimensionsIncludeLabels true
                                                               :gravityRange 0.8
                                                               :padding 10}))))))}
        "Add new nodes"]
       [:> Button
        {:minimal true
         :small true
         :style {:flex "1 1 1"}
         :on-click (fn [_]
                     (.run @lout))}

        "RUN"]]])))



(defn cytoscape-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [cytoscape-component ] parent-el)))
