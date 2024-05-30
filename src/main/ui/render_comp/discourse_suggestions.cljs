(ns ui.render-comp.discourse-suggestions
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [cljs-http.client :as http]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.utils :refer [q delete-block gen-new-uid uid-to-block update-block-string get-safety-settings send-message-component model-mappings watch-children update-block-string-for-block-with-child watch-string create-struct settings-struct get-child-of-child-with-str q p get-parent-parent extract-from-code-block log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
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
       selections       (r/atom #{})]
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
          [:span "For selected nodes find similar nodes: "]
          [:div.chk
           {:style {:align-self "center"
                    :margin-left "5px"}}
           [:> Button
            {:minimal true
             :fill false
             ;:style {:background-color "whitesmoke"}
             :on-click (fn [x]
                         (let [selected (into [] @selections)
                               str-data (clj->js (mapv :string selected))
                               uid-data (mapv  :uid    selected)
                               url "http://localhost:3000/get-openai-embeddings"
                               headers {"Content-Type" "application/json"}
                               _ (println "DATA: --" str-data)
                               res-ch (http/post url {:with-credentials? false
                                                      :headers           headers
                                                      :json-params       (clj->js {:input str-data
                                                                                   :top-k 3})})]
                           (println "find similar nodes as individuals" "--" str-data)
                           (cljs.pprint/pprint selected)
                           (println "--" uid-data)

                           (take! res-ch (fn [res]
                                           (println "GOT RESPONSE")
                                           (doseq [i (range (count uid-data))]
                                             (let  [u (nth uid-data i)
                                                    r (nth (:body res) i)
                                                    matches (str "``` \n "
                                                              (clojure.string/join
                                                                " \n "
                                                                (map #(-> % :metadata :title) r))
                                                              "\n ```")]
                                                 (create-new-block u "last" matches #())))))))}
                                            ;(cljs.pprint/pprint {u r}))))))}
            "As individuals"]]
          [:div.chk
           {:style {:align-self "center"
                    :margin-left "5px"}}
           [:> Button
            {:minimal true
             :fill false
             ;:style {:background-color "whitesmoke"}
             :on-click (fn [x]
                         (let [selected (into [] @selections)
                               str-data (clj->js [(clojure.string/join " \n " (mapv :string selected))])
                               uid-data (mapv  :uid    selected)
                               url      "http://localhost:3000/get-openai-embeddings"
                               headers  {"Content-Type" "application/json"}
                               _ (println "DATA: --" str-data)
                               res-ch (http/post url {:with-credentials? false
                                                      :headers           headers
                                                      :json-params       (clj->js {:input str-data
                                                                                   :top-k 3})})]
                           (println "find similar nodes as individuals" "--" str-data)
                           (cljs.pprint/pprint selected)
                           (println "--" uid-data)

                           (take! res-ch (fn [res]
                                           (let  [res     (-> res :body first)
                                                  _       (println "GOT RESPONSE" res)
                                                  matches (str "``` \n "
                                                             (clojure.string/join
                                                              " \n "
                                                              (map #(-> % :metadata :title) res))
                                                             "\n ```")]
                                             (create-new-block uid "last" matches #()))))))}
            "As group"]]]
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