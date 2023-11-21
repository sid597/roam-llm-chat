(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [ui.extract-data :as ed :refer [data-for-pages q]]
            [reagent.dom :as rd]))





#_(ns ui.core
    (:require [reagent.core :as r]
              [applied-science.js-interop :as j]
              [roam.datascript.reactive :as dr]
              [roam.datascript :as d]
              [clojure.pprint :as pp :refer [pprint]]
              [blueprintjs.core :as bp :refer [Button InputGroup Card]]
              [roam.ui.main-window :as cmp]
              [reagent.dom :as rd]))

(defn log
 [& args]  (apply js/console.log args))


(defn get-child-with-str [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))

(defn chat-context [uid]
  (let [context-ref (r/atom nil)
        chat-loaded (r/atom nil)
        update-fn   (fn []
                      (when-let [context-el @context-ref]
                        (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                              (clj->js {:uid uid
                                        :zoom-path true
                                        :el context-el}))
                          (.then (fn [_])))))]

    (r/create-class
      {:component-did-mount  update-fn
       :component-did-update update-fn
       :reagent-render
       (fn []
         [:div.chat-loader
          {:ref (fn [el] (reset! context-ref el))
           :style {:flex "1 1 auto"
                   :height "100%"
                   :overflow "auto"
                   :display "flex"
                   :align-items "flex-start"
                   :max-height "400px"}}])})))

(defn chat-history [messages]
  (println "chat History ----->" @messages)
  (let [history-ref (r/atom nil)
        update-fn   (fn [this]
                      (when-let [hist-el @history-ref]
                        (println "this" this "argv" (r/argv this))
                        (doall
                          (for [child (reverse (:children @messages))]
                            ^{:key child}
                            (let [uid (:uid child)
                                  msg-block-div (.createElement js/document "div")]
                              (println "child" child)
                              (do
                                (if (.hasChildNodes hist-el)
                                  (.insertBefore hist-el msg-block-div (.-firstChild hist-el))
                                  (.appendChild hist-el msg-block-div (.-firstChild hist-el)))
                                (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                      (clj->js {:uid uid
                                                :zoom-path true
                                                :el msg-block-div}))
                                  (.then (fn [_])))))))))]

    (r/create-class
      {:component-did-update update-fn
       :component-did-mount  update-fn
       :reagent-render       (fn [messages]
                               [:div.chat-history
                                {:ref   (fn [el] (reset! history-ref el))
                                 :style {:flex "1"
                                         :overflow-y "auto"
                                         :margin "10px"
                                         :min-height "300px"
                                         :max-height "800px"
                                         :background "aliceblue"}}])})))


(defn load-context [context messages]
  (let [children      (:children context)
        m-len         (count @messages)
        m-uid         (:uid @messages)]
    (fn [context messages]
      (doall
        (for [child children]
          ^{:key child}
          (let [child-uid (:uid child)
                cstr (:string child)
                ctr  (atom m-len)]
            (do
              (println "child ---->" (= "{{ query block }}"
                                       cstr) child-uid)
              (cond
                (= "{{ query block }}"
                  cstr)                 (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuery] child-uid)
                                          (.then (fn [r]
                                                   (let [res (js->clj r :keywordize-keys true)
                                                         page-data (data-for-pages res)]
                                                     (doall
                                                      (for [dg-node page-data]
                                                        (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                                                              (clj->js {:location {:parent-uid (str m-uid)
                                                                                   :order       -1}
                                                                        :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                                                                   :string (str "``` " dg-node "```")
                                                                                   :open   true}}))
                                                          (.then (fn [_]
                                                                   (println "new block in messages" page-data))))))))))

                :else                  (do
                                         (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                                               (clj->js {:location {:parent-uid (str m-uid)
                                                                    :order       -1}
                                                         :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                                                    :string (str cstr)
                                                                    :open   true}}))
                                           (.then (fn []
                                                    (println "new block in messages"))))
                                         (swap! ctr inc))))))))))



(defn chat-ui [block-uid]
  (println "block uid for chat" block-uid)
  (let [settings (get-child-with-str block-uid "Settings")
        context  (get-child-with-str block-uid "Context")
        messages (r/atom (get-child-with-str block-uid "Messages"))
        c-uid    (:uid context)]

   (fn [{:keys [block-uid]}]

     [:div.chat-container
      {:style {:display "flex"
               :flex-direction "column"
               :border-radius "8px"
               :overflow "hidden"}}
      [:> Card {:interactive true
                :elevation 3
                :style {:flex "1"
                        :margin "0"
                        :display "flex"
                        :flex-direction "column"
                        :border "2px solid rgba(0, 0, 0, 0.2)"
                        :border-radius "8px"}}
       [chat-history messages]
       [:div.chat-input-container
        {:style {:display "flex"
                 :align-items "center"
                 :border "1px"
                 :padding "10px"}}
        [chat-context c-uid]
        [:> Button {:icon "arrow-right"
                    :intent "primary"
                    :large true
                    :on-click (load-context context messages)
                    :style {:margin-left "10px"}}]]]])))




(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))




