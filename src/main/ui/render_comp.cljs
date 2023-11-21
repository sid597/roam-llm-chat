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

(defn chat-context []
  (let [input-ref (r/atom nil)
        chat-loaded (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (when-let [input-el @input-ref]
           (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                 (clj->js {:uid "NwZQptIUd"
                           :zoom-path true
                           :el input-el}))
             (.then (fn [res]
                      (log "PROMISE RESULT" res)
                      (reset! chat-loaded res))))))
       :reagent-render
       (fn []
         [:div {:ref (fn [el] (reset! input-ref el))}])})))


(defn load-context [context messages]
  (let [ctx-buid      (:uid context)
        children      (:children context)
        m-len         (count messages)
        m-uid         (:uid messages)]
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
                                                     (pprint page-data)
                                                     (println page-data)
                                                     (println (str page-data))
                                                     (doall
                                                      (for [dg-node page-data]
                                                        (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                                                              (clj->js {:location {:parent-uid (str m-uid)
                                                                                   :order       -1}
                                                                        :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                                                                   :string (str "``` " dg-node "```")
                                                                                   :open   true}}))
                                                          (.then (fn []
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
  (pprint (ffirst (q '[:find (pull ?e [:block/string :block/uid {:block/children ...}])
                        :in $ ?uid
                        :where
                        [?e :block/uid ?uid]]
                     block-uid)))
  (let [settings (get-child-with-str block-uid "Settings")
        context  (get-child-with-str block-uid "Context")
        messages (get-child-with-str block-uid "Messages")
        chat-loader-el (r/atom nil)
        c-uid    (:uid context)
        chat-loaded (r/atom nil)
        ;; Define the function for handling the mounting of the component
        handle-did-mount (fn []
                           (println "component did mount")
                           (let [hist           (.querySelector js/document ".chat-history")]
                             (println "CHAT LOADED" c-uid)
                             (when @chat-loader-el
                               #_(.appendChild chat-loader-el new-input-el (.-firstChild chat-loader-el))
                               (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                     (clj->js {:uid (str c-uid)
                                               :zoom-path true
                                               :el @chat-loader-el}))
                                 (.then (fn [res]
                                          (log "PROMISE RESULT" res)
                                          (reset! chat-loaded res)))))
                             (when hist
                               (doall
                                 (for [child (reverse (:children messages))]
                                   ^{:key child}
                                   (let [uid (:uid child)
                                         msg-block-div (.createElement js/document "div")]
                                     (println "child" child)
                                     (do
                                       (if (.hasChildNodes hist)
                                         (.insertBefore hist msg-block-div (.-firstChild hist))
                                         (.appendChild hist msg-block-div (.-firstChild hist)))
                                       (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                             (clj->js {:uid uid
                                                       :zoom-path true
                                                       :el msg-block-div}))
                                         (.then (fn [res]
                                                  (log "PROMISE RESULT" res)
                                                  (reset! chat-loaded res)))))))))))]

    (r/create-class
      {:component-did-mount handle-did-mount
       :reagent-render (fn [{:keys [block-uid]}]
                         (println "settings")
                         (pprint settings)
                         (pprint context)
                         (println "muid" c-uid)
                         (pprint messages)
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

                           [:div.chat-history
                            {:style {:flex "1"
                                     :overflow-y "auto"
                                     :margin "10px"
                                     :min-height "300px"
                                     :background "aliceblue"}}]

                           [:div.chat-input-container
                            {:style {:display "flex"
                                     :align-items "center"
                                     :border "1px"
                                     :padding "10px"}}
                            [:div.chat-loader
                             {:ref (fn [el] (reset! chat-loader-el el))
                              :style {:flex "1"
                                      :display "flex"
                                      :align-items "center"}}]

                            [:> Button {:icon "arrow-right"
                                        :intent "primary"
                                        :large true
                                        :on-click (load-context context messages)
                                        :style {:margin-left "10px"}}]]]])})))




(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))




