(ns ui.render-comp.discourse-suggestions
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [cljs-http.client :as http]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data.dg :refer [determine-node-type all-dg-nodes get-all-discourse-node-from-akamatsu-graph-for]]
            ["@blueprintjs/core" :as bp :refer [Checkbox Position Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.utils :refer [q
                              call-llm-api
                              title->uid
                              p
                              button-with-tooltip
                              get-block-parent-with-order
                              delete-block
                              gen-new-uid
                              uid-to-block
                              get-title-with-uid
                              update-block-string
                              get-safety-settings
                              send-message-component
                              model-mappings
                              watch-children
                              update-block-string-for-block-with-child
                              watch-string
                              create-struct
                              settings-struct
                              get-child-of-child-with-str
                              get-parent-parent
                              extract-from-code-block
                              update-block-string-and-move
                              is-a-page?
                              get-child-with-str
                              block-has-child-with-str?
                              move-block
                              create-new-block]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.cytoscape :refer [llm-suggestions-2 node-colors get-node-data suggested-nodes suggested-edges random-uid get-cyto-format-data-for-node cytoscape-component]]
            [clojure.string :as str]
            [reagent.dom :as rd]))


(defn get-discourse-template [node-name]
  (:children (ffirst (q '[:find (pull ?c [{:block/children ...} :block/string :block/order])
                          :in $ ?node-name
                          :where [?e :node/title ?node-name]
                          [?e :block/children ?c]
                          [?c :block/string "Template"]]
                       node-name))))

(comment
  (get-discourse-template "discourse-graph/nodes/Source"))


(defn template-data-for-node [suggestion-str]
  (let [pre "discourse-graph/nodes/"]
   (cond
     (str/starts-with? suggestion-str "[[EVD]] -") (get-discourse-template (str pre "Evidence"))
     (str/starts-with? suggestion-str "[[QUE]] -") (get-discourse-template (str pre "Question"))
     (str/starts-with? suggestion-str "[[CON]] -") (get-discourse-template (str pre "Conclusion"))
     (str/starts-with? suggestion-str "[[RES]] -") (get-discourse-template (str pre "Result"))
     (str/starts-with? suggestion-str "[[HYP]] -") (get-discourse-template (str pre "Hypothesis"))
     (str/starts-with? suggestion-str "[[@")       (get-discourse-template (str pre "Source"))
     (str/starts-with? suggestion-str "[[ISS]] -") (get-discourse-template (str pre "Issue"))
     (str/starts-with? suggestion-str "[[CLM]] -") (get-discourse-template (str pre "Claim")))))

(defn extract-parent-breadcrumbs [block-uid]
  (q '[:find ?u
       :in $ ?uid
       :where
       [?e :block/uid ?uid]
       [?e :block/parents ?p]
       [?p :block/string ?s]
       [?p :block/uid ?u]]
    block-uid))

(comment
  (extract-parent-breadcrumbs "9gT7Psy9Y")
  (clojure.string/join " > " (map #(str "((" % "))") (take 2 (flatten (extract-parent-breadcrumbs "9gT7Psy9Y"))))))


(defn create-discourse-node-with-title [node-title suggestion-ref]
  (p "Create discourse with title" node-title)
  (let [all-breadcrumbs (extract-parent-breadcrumbs (str suggestion-ref))
        breadcrumbs     (->>
                          all-breadcrumbs
                          flatten
                          (take 2)
                          (map #(str "((" % "))"))
                          (clojure.string/join " > "))
        node-template   (conj (->> (template-data-for-node node-title)
                                (map #(update % :order inc))
                                (into []))
                              {:order 0
                               :string (str "This came from: " breadcrumbs)})]
    (when (some? node-template)
      (p "Node type for title:" node-title " is -- " node-template)
      (let [page-uid (gen-new-uid)]
        (create-struct
          {:title node-title
           :u     page-uid
           :c     node-template}
          page-uid
          page-uid
          true)))))


(defn actions [child m-uid selections cy-el]
  (let [checked (r/atom false)
        added?  (r/atom false)]
    (fn [_ _ _]
      [:div
       {:style {:display "flex"}}
       [:div
        {:class-name (str "msg-" (:uid child))
         :style {:flex "1"}
         :ref (fn [el]
                (when (some? el)
                  (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                    (clj->js {:uid      (:uid child)
                              :open?    false
                              :zoom-path false
                              :el        el}))))}]
       [:div
        {:class-name (str "messages-chin-" m-uid)
         :style {:display          "flex"
                 :flex-direction   "row"
                 :background-color "aliceblue"
                 :min-height       "27px"
                 :justify-content  "end"
                 :font-size        "10px"
                 :align-items      "center"}}
        [button-with-tooltip
         "Click to add this suggested discourse node to your graph. Once you click it the plugin will mark this block as
          created, you will see the block highlighted in yellow color and marked as done. Then the plugin will create this page and pre-fill
         it based on the existing template. The new page would also mention that it was created by and ai and a reference to
         this chat."
         [:> Button {:class-name (str "tick-button" m-uid)
                     :style      {:width "30px"}
                     :icon       "tick"
                     :minimal    true
                     :fill       false
                     :small      true
                     :on-click   (fn [e]
                                   (let [block-uid    (:uid child)
                                         block-string (:string (ffirst (uid-to-block block-uid)))]
                                     (p "Create discourse node" child)
                                     (do
                                       (create-discourse-node-with-title block-string block-uid)
                                       (when (template-data-for-node block-string)
                                         (p "Suggestion node created, updating block string")
                                         (update-block-string block-uid (str "^^ {{[[DONE]]}} " block-string "^^"))))))}]
         (.-RIGHT Position)]

        [button-with-tooltip
         "Remove this suggestion from the list."
         [:> Button {:class-name (str "cross-button" m-uid)
                     :style      {:width "30px"}
                     :icon       "cross"
                     :minimal    true
                     :fill       false
                     :small      true
                     :on-click   (fn [e]
                                   (p "Discard selected option")
                                   (delete-block (:uid child)))}]
         (.-TOP Position)]
        (when (not @added?)
          [button-with-tooltip
           ;; TODO: Only show these options when in visualisation mode.
           "Add this node along with its similar nodes to the visualisation."
           [:> Button {:class-name (str "plus-button" m-uid)
                       :style      {:width "30px"}
                       :icon       "plus"
                       :minimal    true
                       :fill       false
                       :small      true
                       :on-click   (fn [e]
                                     (let [source-str    (:string child)
                                           source-uid    (:uid child)
                                           suggestion-node (vals
                                                             (suggested-nodes
                                                               [{:string source-str
                                                                 :uid    source-uid}]))
                                           extracted     (extract-from-code-block (clojure.string/trim (:string (first (:children child)))) true)
                                           split-trimmed (mapv str/trim (str/split-lines extracted))
                                           non-empty     (into [] (filter (complement str/blank?) split-trimmed))
                                           suggestion-edges (mapv
                                                              (fn [target]
                                                                (let [target-data (ffirst (get-title-with-uid target))
                                                                      target-uid (:uid target-data)]
                                                                  {:data
                                                                   {:id     (str source-uid "-" target-uid)
                                                                    :source source-uid
                                                                    :target  target-uid
                                                                    :relation "Similar to"
                                                                    :color    "lightgrey"}}))
                                                              non-empty)
                                           similar-nodes    (do
                                                              (get-cyto-format-data-for-node {:nodes non-empty}))
                                           nodes (concat similar-nodes suggestion-node suggestion-edges)]
                                       (do
                                         (.add @cy-el (clj->js nodes))
                                         (->(.layout @cy-el (clj->js{:name "cose-bilkent"
                                                                     :animate true
                                                                     :animationDuration 1000
                                                                     :idealEdgeLength 100
                                                                     :edgeElasticity 0.95
                                                                     :gravity 1.0
                                                                     :nodeDimensionsIncludeLabels true
                                                                     :gravityRange 0.8
                                                                     :padding 10}))
                                           (.run))
                                         (reset! added? true))))}]
           (.-TOP Position)])
        (when @added?

          [button-with-tooltip
           "Remove this node along with its similar nodes from the visualisation."
           [:> Button {:class-name (str "scroll-up-button" m-uid)
                       :style      {:width "30px"}
                       :icon       "minus"
                       :minimal    true
                       :fill       false
                       :small      true
                       :on-click   (fn [e]
                                     (let [similar-nodes    (let [extracted     (extract-from-code-block
                                                                                  (clojure.string/trim (:string (first (:children child))))
                                                                                  true)
                                                                   split-trimmed (mapv str/trim (str/split-lines extracted))
                                                                   non-empty     (into [] (filter (complement str/blank?) split-trimmed))]
                                                               (reduce
                                                                 (fn [acc t]
                                                                  (let [u (:uid (ffirst (get-title-with-uid t)))]
                                                                    (if (some? u) (conj acc (str "#" u)) acc)))
                                                                 []
                                                                 non-empty))
                                           nodes (conj similar-nodes (str "#"(:uid child)))]
                                       (do
                                         (doseq [id nodes]
                                           (.remove (.elements @cy-el id)))
                                         (->(.layout @cy-el (clj->js{:name "cose-bilkent"
                                                                     :animate true
                                                                     :animationDuration 1000
                                                                     :idealEdgeLength 100
                                                                     :edgeElasticity 0.95
                                                                     :gravity 1.0
                                                                     :nodeDimensionsIncludeLabels true
                                                                     :gravityRange 0.8
                                                                     :padding 10}))
                                           (.run))
                                         (reset! added? false))))}]
           (.-TOP Position)])

        [button-with-tooltip
         "Select this suggestion to later perform actions using the buttons in the bottom bar. You can select multiple
         suggestions to do a bulk operation for e.g select a few and then create them at once or for all the selected
         suggestion find similar existing nodes in the graph."
         [:> Checkbox
          {:style     {:margin-bottom "0px"
                       :padding-left  "30px"}
           :checked   @checked
           :on-change (fn []
                        (do
                          (if (contains? @selections child)
                            (swap! selections disj child)
                            (do
                              (swap! selections conj child)
                              (reset! added? true)))
                          (swap! checked not @checked)))}]
         (.-RIGHT Position)]]])))

(defn chat-history [m-uid m-children selections cy-el]
  [:div.middle-comp
   {:class-name (str "chat-history-container-" m-uid)
    :style
    {:display       "flex"
     :box-shadow    "#2e4ba4d1 0px 0px 4px 0px"
     :padding       "10px"
     :background    "aliceblue"
     :margin-bottom "15px"
     :flex-direction "column"}}
   [:div
    {:class-name (str "chat-history-" m-uid)
     :style {:overflow-y "auto"
             :min-height "100px"
             :max-height "700px"
             :background "aliceblue"}}
    (doall
      (for [child (sort-by :order @m-children)]
        ^{:key (:uid child)}
        [actions child m-uid selections cy-el]))]])


(defn get-similar-suggestion-nodes [selected]
  (vals
    (suggested-nodes
      (mapv
        (fn [node]
          (println "node" node)
          {:string (:string node)
           :uid    (:uid    node)})
        selected))))


(defn read-similar-suggestion-code-block-data [node]
  (let [extracted     (extract-from-code-block
                        (clojure.string/trim (:string (first (:children node))))
                        true)
        split-trimmed (mapv str/trim (str/split-lines extracted))
        non-empty      (filter (complement str/blank?) split-trimmed)]
    non-empty))


(defn get-similar-suggestion-edges [selected]
  (mapcat
    (fn [node]
      (let [source-str    (:string node)
            source-uid    (:uid node)
            non-empty      (read-similar-suggestion-code-block-data node)]
        (map
          (fn [target]
            (let [target-data (ffirst (get-title-with-uid target))
                  target-uid (:uid target-data)]

              {:data
               {:id     (str source-uid "-" target-uid)
                :source source-uid
                :target  target-uid
                :relation "Similar to"
                :color    "lightgrey"}}))
          non-empty)))
    selected))

(defn get-similar-nodes [selected]
  (r/atom (into []
            (flatten
              (mapv
                (fn [x]
                  (read-similar-suggestion-code-block-data x))
                selected)))))

(defn parse-data-string [node]
  (cljs.reader/read-string
    (extract-from-code-block
      (clojure.string/trim (:string (first (:children node))))
      true)))


(defn get-suggested-discourse-graph-nodes [k  v]
  (let [res (atom {})]
    (do
     (doseq [n v]
       (let [title (:title n)
             uid (:uid n)
             node-data {:data {:id uid
                               :label title
                               :color (node-colors (determine-node-type title))
                               :bwidth "3px"
                               :bstyle "solid"
                               :bcolor "black"}}]
         (swap! res assoc title node-data)))
     (swap! res assoc k {:data {:id    (title->uid k)
                                :label k
                                :color "white"
                                :bwidth "2px"
                                :bstyle "dashed"
                                :bcolor "black"}})
     (println "*********8" @res)
     (vals @res))))


(defn get-suggested-discourse-graph-cyto-data [selected]
  (let [all-nodes (atom [])
        all-edges (atom [])]
    (println "1. --" (count selected))
    (doseq [node selected]
      (when (some? (:children node))
        (let [parsed-node-data (parse-data-string node)
              cyto-nodes (get-suggested-discourse-graph-nodes (:string node)  parsed-node-data)
              cyto-edges (suggested-edges {(:string node) parsed-node-data})]
          (swap! all-nodes concat cyto-nodes)
          (swap! all-edges concat cyto-edges))))
   {:nodes @all-nodes
    :edges @all-edges}))


(defn discourse-node-suggestions-ui [block-uid]
 #_(println "block uid for chat" block-uid)
 (let [suggestions-data (get-child-with-str block-uid "Suggestions")
       type             (get-child-with-str block-uid "Type")
       similar-nodes-as-individuals (r/atom false)
       similar-nodes-as-group       (r/atom true)
       uid              (:uid suggestions-data)
       suggestions      (r/atom (:children suggestions-data))
       selections       (r/atom #{})
       visualise?       (r/atom true)
       as-indi-loading? (r/atom false)
       as-group-loading?(r/atom false)
       cy-el            (atom nil)
       running? (r/atom false)
       dg? (r/atom false)
       [parent-uid _] (get-block-parent-with-order block-uid)
       already-exist? (r/atom (block-has-child-with-str? parent-uid "{{visualise-suggestions}}"))]
   (watch-children
     uid
     (fn [_ aft]
       (reset! suggestions (:children aft))))
   (fn [_]
     [:div
        {:class-name (str "dg-suggestions-container-" block-uid)
         :style {:display "flex"
                 :flex-direction "column"
                 :border-radius "8px"
                 :overflow "hidden"}}
      [:> Card {:elevation 3
                :style {:flex "1"
                        :margin "0"
                        :padding "5px"
                        :display "flex"
                        :flex-direction "column"
                        :border "2px solid rgba(0, 0, 0, 0.2)"
                        :border-radius "8px"}}

       [chat-history uid suggestions selections cy-el]
       [:div.bottom-comp
        [:div.chat-input-container
         {:style {:display "flex"
                  :flex-direction "row"}}]
        [:div
         {:class-name (str "messages-chin-")
          :style {:display "flex"
                  :flex-direction "row"
                  :justify-content "space-between"
                  :padding "5px"
                  :align-items "center"}}
         [:div.checkboxes
          {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "center"}}
          #_[:span "For selected nodes find similar nodes: "]
          [:div.chk
           {:style {:align-self "center"
                    :margin-left "5px"}}
           [button-with-tooltip
            "For each of the selected suggestions, extract similar discourse nodes from the graph"
            [:> Button
             {:minimal true
              :fill false
              :loading @as-indi-loading?
              ;:style {:background-color "whitesmoke"}
              :on-click (fn [x]
                          (do
                           (reset! as-indi-loading? true)
                           (let [selected (into [] @selections)
                                 str-data (clj->js (mapv :string selected))
                                 uid-data (mapv  :uid    selected)
                                 url      "https://roam-llm-chat-falling-haze-86.fly.dev/get-openai-embeddings"
                                 headers {"Content-Type" "application/json"}
                                 res-ch (http/post url {:with-credentials? false
                                                        :headers           headers
                                                        :json-params       (clj->js {:input str-data
                                                                                     :top-k 3})})]
                             (take! res-ch (fn [res]
                                             (reset! selections #{})
                                             (doseq [i (range (count uid-data))]
                                               (let  [u         (nth uid-data i)
                                                      r         (nth (:body res) i)
                                                      matches   (str "``` \n "
                                                                  (clojure.string/join
                                                                    " \n "
                                                                    (map #(-> % :metadata :title) r))
                                                                  "\n ```")
                                                      node-data (merge (ffirst (q '[:find (pull ?u [{:block/children ...} :block/string :block/uid])
                                                                                    :in $ ?nuid
                                                                                    :where [?u :block/uid ?nuid]]
                                                                                 u))
                                                                  {:children [{:string matches}]})]
                                                 (do
                                                   (create-new-block u "last" matches #())
                                                   (swap! selections conj node-data))))
                                            (reset! as-indi-loading? false))))))}
             "As individuals"]]]
          [:div.chk
           {:style {:align-self "center"
                    :margin-left "5px"}}
           [button-with-tooltip
            "Consider all the selected nodes as a single group and then find similar discourse nodes in the graph."
            [:> Button
             {:minimal true
              :fill false
              :loading @as-group-loading?
              ;:style {:background-color "whitesmoke"}
              :on-click (fn [x]
                          (do
                           (reset! as-group-loading? true)
                           (let [selected (into [] @selections)
                                 str-data (clj->js [(clojure.string/join " \n " (mapv :string selected))])
                                 uid-data (mapv  :uid    selected)
                                 url     "https://roam-llm-chat-falling-haze-86.fly.dev/get-openai-embeddings"
                                 headers  {"Content-Type" "application/json"}
                                 res-ch (http/post url {:with-credentials? false
                                                        :headers           headers
                                                        :json-params       (clj->js {:input str-data
                                                                                     :top-k 3})})]
                             (take! res-ch (fn [res]
                                             (let  [res     (-> res :body first)
                                                    _       (println "GOT RESPONSE" res)
                                                    matches (str "``` \n "
                                                               (clojure.string/join
                                                                " \n "
                                                                (map #(-> % :metadata :title) res))
                                                               "\n ```")]
                                               (create-new-block uid "last" matches #()))
                                             (reset! as-group-loading? false))))))}
             "As group"]]]]
         [:div.chk
          {:style {:align-self "center"
                   :margin-left "5px"}}
          [button-with-tooltip
           "For all the selected nodes which also have their similar nodes. Take each such node's similar node, find their discourse context
           and then visualise them. So you have: Suggested node --> Similar node 1 --> Discourse context for similar node 1. "
           [:> Button
            {:class-name "visualise-button"
             :active (not @running?)
             :disabled @running?
             :minimal true
             :fill false
             :small true
             :on-click (fn []
                         (let [selected (into [] @selections)])
                         (let [cyto-uid       (gen-new-uid)
                               struct         {:s "{{visualise-suggestions}}"
                                               :u cyto-uid}
                               selected       (into [] @selections)                  #_[{:children
                                                                                         [{:order 0,
                                                                                           :string
                                                                                           "``` \n [[ISS]] - Measure actin asymmetry at endocytic sites as a function of myosin-I parameters \n [[ISS]] - experimental data of the amount of actin or Arp2/3 complex at sites of endocytosis under elevated membrane tension \n [[ISS]] - experimental comparsion between single-molecule binding rates of actin + myosin\n ```",
                                                                                           :uid "rhnI6i8hJ"}],
                                                                                         :order 2,
                                                                                         :string
                                                                                         "[[ISS]] - Conduct experiments to measure the impact of varying myosin unbinding rates on endocytosis under different membrane tension conditions using real-time fluorescence microscopy",
                                                                                         :uid "34m9BUqql"}
                                                                                        {:children
                                                                                         [{:order 0,
                                                                                           :string
                                                                                           "``` \n [[ISS]] - Measure actin asymmetry at endocytic sites as a function of myosin-I parameters \n [[CON]] - With high catch bonding and low unbinding rates, myosin-I contributes to increased stalling of endocytic actin filaments, greater nucleation rates, and less pit internalization \n [[RES]] - Under increasing values of membrane tension, endocytic myosin-I assisted endocytosis under a wider range of values of unbinding rate and catch bonding  - [[@cytosim/simulate myosin-I with calibrated stiffness value and report unbinding force vs. unbinding rate]]\n ```",
                                                                                           :uid "ASnOB1PGh"}],
                                                                                         :order 3,
                                                                                         :string
                                                                                         "[[ISS]] - Perform a literature review on the role of myosin catch bonding in cellular processes, focusing on its impact on endocytosis under varying tension conditions",
                                                                                         :uid "r5EQJeiTb"}]
                               _ (println "before suggestions")
                               {:keys [nodes edges]} (if @dg? (get-suggested-discourse-graph-cyto-data selected) {})
                               suggestion-nodes      (if (some? nodes) nodes (get-similar-suggestion-nodes selected))
                               suggestion-edges      (if (some? edges) edges (get-similar-suggestion-edges selected))
                               similar-nodes         (if (some? nodes) (atom [])  (get-similar-nodes selected))
                               extra-data   (concat suggestion-nodes suggestion-edges)]
                           (do
                             ;(println "Visualise button clicked")
                             ;(println "Selected" (count @similar-nodes))
                             (if (some? @already-exist?)
                               (let [el (first (.getElementsByClassName js/document (str "cytoscape-main-" @already-exist?)))]
                                 (println "rendering cytoscape")
                                 (rd/render [cytoscape-component @already-exist? cy-el similar-nodes extra-data] el))
                               (create-struct
                                 struct
                                 (first (get-block-parent-with-order block-uid))
                                 nil
                                 false
                                 #(js/setTimeout
                                    (fn [_]
                                      (let [el (first (.getElementsByClassName js/document (str "cytoscape-main-" cyto-uid)))]
                                        (do
                                          (println "rendering cytoscape")
                                          (rd/render [cytoscape-component cyto-uid cy-el similar-nodes extra-data] el)
                                          (when @already-exist?
                                            true))))
                                    700)))
                             (reset! running? true))))}

            (if @already-exist?
              "Connect"
              "Visualise")]]]
         [:div.chk
          {:style {:align-self "center"
                   :margin-left "5px"}}
          [button-with-tooltip
           "Consider all the selected nodes as a single group and then find similar discourse nodes in the graph."
           [:> Button
            {:minimal true
             :small true
             :on-click  (fn [e]
                          (let [initial-data (take 695 (all-dg-nodes)) ;; 40k tokens
                                new-titles    (mapv :string @selections)
                                title->block-uid (reduce
                                                   (fn [acc x]
                                                     (assoc acc (keyword (:string x)) (:uid x)))
                                                   {}
                                                   @selections)
                                prompt       (str
                                               "<system_context>
                                               You are an AI assistant trained to analyze discourse relationships in scientific research data, specifically in the field of cellular biology focusing on the actin cytoskeleton and endocytosis.
                                               </system_context>

                                               <lab_context>
                                               Our lab uses Roam Research for knowledge organization with the following structure:
                                               <node_types>
                                               - Question (QUE)
                                               - Claim (CLM)
                                               - Evidence (EVD)
                                               </node_types>
                                               <edge_types>
                                               - Informs
                                               - Supports
                                               - Opposes
                                               </edge_types>
                                               <discourse_relationships>
                                               - (Evidence, Informs, Question)
                                               - (Question, InformedBy, Evidence)
                                               - (Evidence, Supports, Claim)
                                               - (Claim, SupportedBy, Evidence)
                                               - (Evidence, Opposes, Claim)
                                               - (Claim, OpposedBy, Evidence)
                                               </discourse_relationships>
                                               </lab_context>

                                               <task>
                                               Analyze the provided <initial_data> to find discourse relationships relevant to each of the given <new_titles>. Use your expertise to identify meaningful connections for each title.
                                               </task>
                                               <input_format>
                                               <initial_data_format>
                                               A list of maps, each containing:
                                               - uid: Unique identifier of the node
                                               - title: Title of the node
                                               </initial_data_format>
                                               <initial-data>"
                                               initial-data
                                               "</initial-data>
                                               <new_titles>"
                                               new-titles
                                               "</new_titles>
                                               </input_format>
                                               <output_format>
                                               Return a map where each key is one of the new titles, and the corresponding value is a list of maps containing:
                                               - uid: Unique identifier of the related node
                                               - title: Title of the related node
                                               - label: Type of discourse relationship (Informs, Supports, or Opposes)
                                               </output_format>

                                               <instructions>
                                               1. Examine each title in <new_titles> and understand its implications within the context of actin cytoskeleton and endocytosis research.
                                               2. Review the <initial_data> and identify nodes that could form valid discourse relationships with the <new_title>.
                                               3. For each relevant node, determine the appropriate relationship type based on the content and research context.
                                               4. Only include relationships that strictly adhere to the defined <discourse_relationships>.
                                               5. If no valid relationships are found, return an empty list rather than forcing irrelevant connections.
                                               </instructions>

                                                <quality_guideline>
                                                Prioritize accuracy and relevance over quantity. It's better to return fewer, highly relevant relationships than many tenuous connections. Ensure all relationships logically fit within the context of actin cytoskeleton and endocytosis research.
                                                </quality_guideline>")
                                tools[{:name "analyze_discourse_relationships"
                                       :description "Analyze discourse relationships for multiple scientific titles. For each input title, identify relevant nodes from the initial data and determine their relationship types."
                                       :input_schema
                                       {:type "object"
                                        :properties
                                        {:results
                                         {:type "object"
                                          :additionalProperties
                                          {:type "array"
                                           :items
                                           {:type "object"
                                            :properties
                                            {:uid {:type "string"
                                                   :description "Unique identifier of the related node"}
                                             :title {:type "string"
                                                     :description "Title of the related node"}
                                             :label {:type "string"
                                                     :enum ["Supports" "Informs" "Opposes" "SupportedBy" "InformedBy" "OpposedBy"]
                                                     :description "Type of discourse relationship with the input title"}}
                                            :required ["uid" "title" "label"]}}}}
                                        :required ["results"]}}]]
                            #_(println "prompt" prompt)
                            (call-llm-api
                              {:messages [{:role "user"
                                           :content prompt}]
                               :settings {:model #_"claude-3-5-sonnet-20240620"
                                          "claude-3-haiku-20240307"
                                          :temperature 1.0
                                          :max-tokens 1000
                                          :tools tools
                                          :tool_choice {:type "tool"
                                                        :name "analyze_discourse_relationships"}}
                               :callback (fn [response]
                                           (let [res (-> response :body)
                                                 content (-> res :input :results)]

                                             (println "----- Got response from llm -----")
                                             (println "title->blockuid" title->block-uid)
                                             (println "---------- END ---------------")
                                             (doseq [[k v] content]
                                               (println "k" k)
                                               (let [u               (k title->block-uid)
                                                     _               (println "u" u)
                                                     matches         (str "```\n"
                                                                       (clojure.string/trim
                                                                         (with-out-str
                                                                           (cljs.pprint/pprint v)))
                                                                       "\n```")]
                                                 (do
                                                   (println "u" u "--matches--" matches)
                                                   (create-new-block u "last" matches #()))))))})))}

            "Suggest discourse graph"]]]
         [:div.chk
          {:style {:align-self "center"
                   :margin-left "5px"}}
          [button-with-tooltip
           "Visualise only the discourse graph?"
           [:> Checkbox
            {:style {:margin-bottom "0px"}
             :checked @dg?
             :on-change (fn [x]
                          (reset! dg? (not @dg?)))}
            [:span.bp3-button-text
             {:style {:font-size "14px"
                      :font-family "initial"
                      :font-weight "initial"}} "dg?"]]]]

         [:div.buttons
          {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "center"}}
          [button-with-tooltip
           "For each selected suggestion create new discourse node, this is like bulk creation. "
           [:> Button {:class-name (str "create-node-button")
                       :minimal true
                       :fill false
                       ;:style {:background-color "whitesmoke"}
                       :on-click (fn [_]
                                   (doseq [child @selections]
                                     (let [block-uid    (:uid child)
                                           block-string (:string (ffirst (uid-to-block block-uid)))]
                                       (do
                                         (create-discourse-node-with-title block-string block-uid)
                                         (when (template-data-for-node block-string)
                                           (p "Suggestion node created, updating block string")
                                           (update-block-string block-uid (str "^^ {{[[DONE]]}}" block-string "^^")))))))}
            "Create selected"]]

          [button-with-tooltip
           "For each selected suggestion create new discourse node, this is like bulk creation. "
           [:> Button {:class-name (str "discard-node-button")
                       :minimal true
                       #_#_:style {:background-color "whitesmoke"
                                   :margin "10px"}
                       :fill false
                       :on-click (fn [_]
                                   (doseq [node @selections]
                                     (delete-block (:uid node))))}
            "Discard selected"]]]]]]])))



(defn llm-dg-suggestions-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [discourse-node-suggestions-ui block-uid] parent-el)))
