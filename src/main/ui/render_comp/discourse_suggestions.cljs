(ns ui.render-comp.discourse-suggestions
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [cljs-http.client :as http]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data.dg :refer [determine-node-type all-dg-nodes get-all-discourse-node-from-akamatsu-graph-for]]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.utils :refer [q
                              p
                              get-block-parent-with-order
                              delete-block
                              gen-new-uid
                              uid-to-block
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
            [ui.components.cytoscape :refer [llm-suggestions-2 get-node-data suggested-nodes random-uid get-cyto-format-data-for-node cytoscape-component]]
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


(defn actions [child m-uid selections]
  (let [checked (r/atom false)]
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
        [:> Button {:class-name (str "scroll-down-button" m-uid)
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

        [:> Button {:class-name (str "scroll-up-button" m-uid)
                    :style      {:width "30px"}
                    :icon       "cross"
                    :minimal    true
                    :fill       false
                    :small      true
                    :on-click   (fn [e]
                                  (p "Discard selected option")
                                  (delete-block (:uid child)))}]

        [:> Checkbox
         {:style     {:margin-bottom "0px"
                      :padding-left  "30px"}
          :checked   @checked
          :on-change (fn []
                       (do
                         (if (contains? @selections child)
                           (swap! selections disj child)
                           (swap! selections conj child))
                         (swap! checked not @checked)))}]]])))

(defn chat-history [m-uid m-children selections]
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
        [actions child m-uid selections]))]])


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
       lout             (atom nil)
       running? (r/atom false)]
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

       [chat-history uid suggestions selections]
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
                                              (let  [u (nth uid-data i)
                                                     r (nth (:body res) i)
                                                     matches (str "``` \n "
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
            "As individuals"]]
          [:div.chk
           {:style {:align-self "center"
                    :margin-left "5px"}}
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
            "As group"]]]
         [:div.chk
          {:style {:align-self "center"
                   :margin-left "5px"}}
          [:> Button
           {:class-name "visualise-button"
            :style {:width "30px"}
            :icon "play" ;; Ideal would be to have it change between play and connect, if not present then play
                         ;; otherwise connect
            :active (not @running?)
            :disabled @running?
            :minimal true
            :fill false
            :small true
            :on-click (fn []
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
                              suggestion-nodes (vals
                                                 (suggested-nodes
                                                   (mapv
                                                     (fn [node]
                                                       (println "node" node)
                                                       {:string (:string node)
                                                        :uid    (:uid    node)})
                                                     selected)))
                              suggestion-edges (mapcat
                                                 (fn [node]
                                                   (let [source-str    (:string node)
                                                         source-uid    (:uid node)
                                                         extracted     (extract-from-code-block (clojure.string/trim (:string (first (:children node)))))
                                                         split-trimmed (mapv str/trim (str/split-lines extracted))
                                                         non-empty      (filter (complement str/blank?) split-trimmed)]
                                                     (map
                                                       (fn [target]
                                                         (let [target-data (ffirst
                                                                             (q '[:find (pull ?e [:node/title :block/uid])
                                                                                  :in $ ?n
                                                                                  :where [?e :node/title ?n]]
                                                                               target))
                                                               target-uid (:uid target-data)]

                                                           {:data
                                                            {:id     (str source-uid "-" target-uid)
                                                             :source source-uid
                                                             :target  target-uid
                                                             :relation "Similar to"
                                                             :color    "lightgrey"}}))
                                                       non-empty)))
                                                 selected)
                              similar-nodes  (r/atom (into []
                                                       (flatten
                                                         (mapv
                                                           (fn [x]
                                                             (let [extracted     (extract-from-code-block (clojure.string/trim (:string (first (:children x)))))
                                                                   split-trimmed (mapv str/trim (str/split-lines extracted))
                                                                   non-empty      (filter (complement str/blank?) split-trimmed)]
                                                               non-empty))
                                                           selected))))
                              extra-data   (concat suggestion-nodes suggestion-edges)
                              [parent-uid _] (get-block-parent-with-order block-uid)
                              already-exist? (block-has-child-with-str? parent-uid "{{visualise-suggestions}}")]

                          (do

                            (println "Alread exirst?? " already-exist?)
                            ;(println "Visualise button clicked")
                            ;(println "Selected" (count @similar-nodes))
                            (if (some? already-exist?)
                              (let [el (first (.getElementsByClassName js/document (str "cytoscape-main-" already-exist?)))]
                                (println "rendering cytoscape")
                                (rd/render [cytoscape-component already-exist? cy-el similar-nodes extra-data] el))
                              (create-struct
                                struct
                                (first (get-block-parent-with-order block-uid))
                                nil
                                false
                                #(js/setTimeout
                                   (fn [_]
                                     (let [el (first (.getElementsByClassName js/document (str "cytoscape-main-" cyto-uid)))]
                                       (println "rendering cytoscape")
                                       (rd/render [cytoscape-component cyto-uid cy-el similar-nodes extra-data] el)))
                                   700)))
                            (reset! running? true))))}]]
         #_[:div.action-area
            [:> Button
             {:minimal true
              :small true
              :style {:flex "1 1 1"}
              :on-click (fn [_]
                          (let [new-nodes (get-cyto-format-data-for-node {:nodes ["[[EVD]] - hPSCs were found to exert strong inward-directed mechanical forces on the ECM at specific locations coinciding with ventral stress fibers at the colony edge.  - [[@narva2017strong]]"]})]
                            (println "new node")
                            (doseq [node new-nodes]
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
             "RUN"]
            #_[:> Button
               {:minimal true
                :small true
                :style {:flex "1 1 1"}
                :on-click (fn [_]
                            (let [cyto-uid (gen-new-uid)
                                  similar-nodes (r/atom (into []
                                                          (reduce
                                                            (fn [acc {:keys [title]}]
                                                             (conj acc title))
                                                            []
                                                            (into #{} (flatten (vals llm-suggestions-2))))))
                                  #_#_#_#_s-nodes (keys llm-suggestions-2)
                                          s-edges (suggested-edges llm-suggestions-2)]
                              (println "create struct")
                              (create-struct
                                {:s "{{visualise-suggestions}}"
                                 :u cyto-uid}
                                (first (get-block-parent-with-order block-uid))
                                nil
                                false
                                #(js/setTimeout
                                   (fn [_]
                                     (let [el (first (.getElementsByClassName js/document (str "cytoscape-main-" cyto-uid)))]
                                       (println "rendering cytoscape")
                                       (.log js/console el)
                                       (rd/render [cytoscape-component cyto-uid cy-el similar-nodes []] el)))
                                   700))))}


               "RUN"]]
         [:div.buttons
          {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "center"}}
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
           "Create selected"]
          [:> Button {:class-name (str "discard-node-button")
                      :minimal true
                      #_#_:style {:background-color "whitesmoke"
                                  :margin "10px"}
                      :fill false
                      :on-click (fn [_]
                                  (doseq [node @selections]
                                    (delete-block (:uid node))))}
           "Discard selected"]]]]]])))



(defn llm-dg-suggestions-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [discourse-node-suggestions-ui block-uid] parent-el)))